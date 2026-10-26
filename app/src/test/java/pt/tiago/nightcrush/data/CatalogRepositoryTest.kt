package pt.tiago.nightcrush.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.AssetFile
import pt.tiago.nightcrush.core.model.AssetManifest
import pt.tiago.nightcrush.core.model.CatalogFile
import pt.tiago.nightcrush.core.model.LevelEntry
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.WorldEntry
import java.security.MessageDigest

/**
 * [CatalogRepository] is exercised through its asset reader constructor, so
 * these are plain JVM tests: no Context, no Robolectric, no APK.
 */
class CatalogRepositoryTest {
    @Test fun theAssetPathFollowsTheSameWorldRuleAsLevelTool() {
        assertEquals("levels/world-01/level-0001.json", CatalogRepository.levelPath(1))
        assertEquals("levels/world-01/level-0050.json", CatalogRepository.levelPath(50))
        assertEquals("levels/world-02/level-0051.json", CatalogRepository.levelPath(51))
        assertEquals("levels/world-12/level-0600.json", CatalogRepository.levelPath(600))
        assertEquals(1, CatalogRepository.worldOf(1))
        assertEquals(2, CatalogRepository.worldOf(51))
        assertEquals(12, CatalogRepository.worldOf(600))
        assertEquals("world-03", CatalogRepository.worldIdOf(128))
    }

    @Test fun aBuildWithoutContentGetsAnEmptyCatalogInsteadOfACrash() {
        val repository = CatalogRepository { null }

        assertEquals(CatalogRepository.EMPTY_CATALOG, repository.catalog())
        assertEquals(0, repository.catalog().engineVersion)
        assertTrue(repository.allLevels().isEmpty())
        assertNull(repository.level(1))
        assertNull(repository.world(1))
        val problems = repository.verifyIntegrity()
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("catalog.json is missing") })
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("manifest.json is missing") })
    }

    @Test fun theCatalogIsParsedOnceAndTheLevelsAreCached() {
        val content = ContentFixture()
        val repository = content.repository()

        val catalog = repository.catalog()
        assertEquals(2, catalog.levels.size)
        assertEquals(2, catalog.worlds.size)
        // A second call and a changed asset must not change the answer.
        content.assets[CatalogRepository.CATALOG_ASSET] = "garbage".toByteArray()
        assertEquals(catalog, repository.catalog())

        val first = repository.level(1)
        assertNotNull(first)
        content.assets[CatalogRepository.levelPath(1)] = "garbage".toByteArray()
        assertEquals(first, repository.level(1))
    }

    @Test fun levelsAreReadFromTheirOwnAsset() {
        val repository = ContentFixture().repository()

        val level = repository.level(128)

        assertNotNull(level)
        assertEquals(128, level!!.ordinal)
        assertEquals("world-03", level.worldId)
        assertEquals(2, level.revision)
        assertEquals("Farol", level.titlePt)
    }

    @Test fun aLevelFromAnotherOrdinalIsNotServed() {
        val fixture = ContentFixture()

        assertNull(fixture.repository().level(0))
        assertNull(fixture.repository().level(-1))
        assertNull(fixture.repository().level(999))
    }

    @Test fun allLevelsIsSortedByOrdinal() {
        val fixture = ContentFixture(ordinals = listOf(128, 1, 51))

        val ordinals = fixture.repository().allLevels().map { level -> level.ordinal }

        assertEquals(listOf(1, 51, 128), ordinals)
    }

    @Test fun aLevelWhoseBytesDoNotMatchItsHashIsRefused() {
        val fixture = ContentFixture()

        fixture.assets[CatalogRepository.levelPath(1)] = levelJson(1).replace("\"Ria\"", "\"Ria!\"").toByteArray()

        assertNull(fixture.repository().level(1))
    }

    @Test fun worldsAreFoundByRangeAndById() {
        val repository = ContentFixture().repository()

        val world = repository.world(128)

        assertNotNull(world)
        assertEquals("world-03", world!!.id)
        assertEquals(101, world.from)
        assertEquals(150, world.to)
        assertEquals(world.id, repository.world(150)?.id)
    }

    @Test fun intactContentHasNoIntegrityProblems() {
        val fixture = ContentFixture()

        assertTrue(fixture.repository().verifyIntegrity().toString(), fixture.repository().verifyIntegrity().isEmpty())
    }

    @Test fun aTamperedLevelIsReported() {
        val fixture = ContentFixture()
        fixture.assets[CatalogRepository.levelPath(1)] = levelJson(1).replace("\"Ria\"", "\"Ria!\"").toByteArray()

        val problems = fixture.repository().verifyIntegrity()

        assertTrue(problems.toString(), problems.any { problem -> problem.contains("level-0001 does not match its catalog hash") })
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("level-0001 does not match its manifest hash") })
        assertTrue(problems.toString(), problems.any { problem -> problem.contains(CatalogRepository.levelPath(1)) })
    }

    @Test fun aMissingLevelIsReported() {
        val fixture = ContentFixture()
        fixture.assets.remove(CatalogRepository.levelPath(1))

        val problems = fixture.repository().verifyIntegrity()

        assertTrue(problems.toString(), problems.any { problem -> problem.contains("level-0001 is missing") })
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("is listed in the manifest but missing") })
    }

    @Test fun aTamperedCatalogIsReported() {
        val fixture = ContentFixture()
        fixture.assets[CatalogRepository.CATALOG_ASSET] = fixture.catalogText.replace("\"Farol\"", "\"Farol!\"")
            .toByteArray()

        val problems = fixture.repository().verifyIntegrity()

        assertTrue(problems.toString(), problems.any { problem -> problem.contains("catalog.json does not match manifest.catalogSha256") })
    }

    @Test fun theProblemListIsCapped() {
        val fixture = ContentFixture(ordinals = (1..60).toList())
        fixture.assets.keys.filter { it.startsWith("levels/") }.forEach { path -> fixture.assets[path] = "{}".toByteArray() }

        val problems = fixture.repository().verifyIntegrity()

        assertEquals(CatalogRepository.MAX_REPORTED_PROBLEMS + 1, problems.size)
        assertTrue(problems.last().contains("more problem(s)"))
    }

    // ------------------------------------------------------------------ fixture

    /** Builds the packaged asset set: catalog, manifest and one file per level. */
    private class ContentFixture(ordinals: List<Int> = listOf(1, 128)) {
        val assets = LinkedHashMap<String, ByteArray>()
        val catalogText: String

        init {
            val files = ArrayList<AssetFile>()
            ordinals.sorted().forEach { ordinal ->
                val path = CatalogRepository.levelPath(ordinal)
                val bytes = levelJson(ordinal).toByteArray()
                assets[path] = bytes
                files.add(AssetFile(path, sha256(bytes)))
            }
            val catalog = CatalogFile(
                engineVersion = 1,
                worlds = ordinals.sorted().map { ordinal ->
                    WorldEntry(
                        id = CatalogRepository.worldIdOf(ordinal),
                        namePt = "Mundo",
                        nameEn = "World",
                        from = (ordinal - 1) / 50 * 50 + 1,
                        to = (ordinal - 1) / 50 * 50 + 50,
                    )
                }.distinctBy { world -> world.id },
                levels = ordinals.sorted().map { ordinal ->
                    LevelEntry(
                        id = "level-%04d".format(ordinal),
                        ordinal = ordinal,
                        worldId = CatalogRepository.worldIdOf(ordinal),
                        path = CatalogRepository.levelPath(ordinal),
                        revision = if (ordinal == 1) 1 else 2,
                        sha256 = sha256(assets.getValue(CatalogRepository.levelPath(ordinal))),
                        isFullMoon = false,
                        titlePt = if (ordinal == 1) "Ria" else "Farol",
                        titleEn = "",
                    )
                },
            )
            catalogText = json.encodeToString(CatalogFile.serializer(), catalog)
            val catalogBytes = catalogText.toByteArray()
            assets[CatalogRepository.CATALOG_ASSET] = catalogBytes
            files.add(AssetFile(CatalogRepository.CATALOG_ASSET, sha256(catalogBytes)))
            val manifest = AssetManifest(
                engineVersion = 1,
                catalogSha256 = sha256(catalogBytes),
                files = files.sortedBy { file -> file.path },
            )
            assets[CatalogRepository.MANIFEST_ASSET] =
                json.encodeToString(AssetManifest.serializer(), manifest).toByteArray()
        }

        fun repository(): CatalogRepository = CatalogRepository { name -> assets[name] }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** The packaged bytes of one level, as `export-assets` would copy them. */
        fun levelJson(ordinal: Int): String {
            val level = LevelFile(
                engineVersion = 1,
                id = "level-%04d".format(ordinal),
                ordinal = ordinal,
                worldId = CatalogRepository.worldIdOf(ordinal),
                titlePt = if (ordinal == 1) "Ria" else "Farol",
                revision = if (ordinal == 1) 1 else 2,
            )
            return json.encodeToString(LevelFile.serializer(), level)
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
