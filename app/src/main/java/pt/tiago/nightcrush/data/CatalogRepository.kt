package pt.tiago.nightcrush.data

import android.content.Context
import kotlinx.serialization.json.Json
import pt.tiago.nightcrush.core.model.AssetFile
import pt.tiago.nightcrush.core.model.AssetManifest
import pt.tiago.nightcrush.core.model.CatalogFile
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.PuzzleEntry
import pt.tiago.nightcrush.core.model.PuzzleFile
import pt.tiago.nightcrush.core.model.WorldEntry
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The packaged content: `catalog.json`, `manifest.json` and
 * `levels/world-NN/level-NNNN.json`, all shipped as APK assets and all
 * covered by a SHA-256 written at build time.
 *
 * Nothing is downloaded and no path from the profile is ever followed, so a
 * tampered save cannot point the game at a file of its own.
 *
 * The primary constructor takes an asset reader instead of a [Context] so the
 * whole class can be exercised by plain JVM tests; [CatalogRepository.assetsOf]
 * is the Android wiring.
 */
class CatalogRepository(private val readAsset: (String) -> ByteArray?) {
    constructor(context: Context) : this(assetsOf(context))

    private val json = Json { ignoreUnknownKeys = true }
    private val levelCache = ConcurrentHashMap<Int, LevelFile>()
    private val cacheLock = Any()

    @Volatile
    private var cachedCatalog: CatalogFile? = null

    @Volatile
    private var cachedManifest: AssetManifest? = null

    /**
     * The published catalog, parsed once. Early builds ship no content at all:
     * that is an empty catalog, not a crash, so the UI can still start.
     */
    fun catalog(): CatalogFile {
        cachedCatalog?.let { return it }
        return synchronized(cacheLock) {
            cachedCatalog ?: readCatalog().also { cachedCatalog = it }
        }
    }

    /** The level at [ordinal], parsed on demand and cached. Null when absent or damaged. */
    fun level(ordinal: Int): LevelFile? {
        if (ordinal < 1) return null
        levelCache[ordinal]?.let { return it }
        val path = levelPath(ordinal)
        val bytes = readAsset(path) ?: return null
        if (!hashMatches(path, bytes)) return null
        val parsed = try {
            json.decodeFromString(LevelFile.serializer(), bytes.decodeToString())
        } catch (_: Exception) {
            return null
        }
        if (parsed.ordinal != ordinal) return null
        if (parsed.worldId != worldIdOf(ordinal)) return null
        levelCache[ordinal] = parsed
        return parsed
    }

    @Volatile
    private var cachedPuzzles: List<PuzzleEntry>? = null

    /**
     * The 100 puzzles, in order. They must be listed in the manifest with a
     * matching hash; anything else reads as "no puzzles", never as a crash.
     */
    fun puzzles(): List<PuzzleEntry> {
        cachedPuzzles?.let { return it }
        val bytes = readAsset(PUZZLES_ASSET) ?: return emptyList()
        val listed = manifest()?.files?.firstOrNull { it.path == PUZZLES_ASSET } ?: return emptyList()
        if (sha256(bytes) != listed.sha256) return emptyList()
        val parsed = try {
            json.decodeFromString(PuzzleFile.serializer(), bytes.decodeToString())
        } catch (_: Exception) {
            return emptyList()
        }
        return parsed.puzzles.sortedBy { it.number }.also { cachedPuzzles = it }
    }

    /** Every catalog level, sorted by ordinal; undamaged ones only (see [verifyIntegrity]). */
    fun allLevels(): List<LevelFile> = catalog().levels
        .map { entry -> entry.ordinal }
        .distinct()
        .sorted()
        .mapNotNull { ordinal -> level(ordinal) }

    /** The world a level belongs to, by ordinal range and then by id. */
    fun world(ordinal: Int): WorldEntry? {
        val worlds = catalog().worlds
        worlds.firstOrNull { entry -> entry.from <= ordinal && ordinal <= entry.to }?.let { return it }
        return worlds.firstOrNull { entry -> entry.id == worldIdOf(ordinal) }
    }

    /**
     * Every integrity problem found in the packaged content, as readable
     * lines: missing assets, hashes that disagree with `manifest.json` or with
     * the catalog entry, and catalog entries with no manifest record.
     *
     * This hashes every level file, so it belongs to the diagnostics screen,
     * not to startup.
     */
    fun verifyIntegrity(): List<String> {
        val problems = ArrayList<String>()
        val manifest = manifest()
        val catalogBytes = readAsset(CATALOG_ASSET)
        if (manifest == null) problems.add("$MANIFEST_ASSET is missing or unreadable")
        if (catalogBytes == null) {
            problems.add("$CATALOG_ASSET is missing")
        } else if (manifest != null && manifest.catalogSha256.isNotEmpty() && sha256(catalogBytes) != manifest.catalogSha256) {
            problems.add("$CATALOG_ASSET does not match manifest.catalogSha256")
        }
        addManifestProblems(manifest, problems)
        addCatalogProblems(problems)
        return summarize(problems)
    }

    /** The asset manifest, or null when it is absent or unparseable. */
    fun manifest(): AssetManifest? {
        cachedManifest?.let { return it }
        return synchronized(cacheLock) {
            cachedManifest ?: readManifest().also { cachedManifest = it }
        }
    }

    private fun readCatalog(): CatalogFile {
        val bytes = readAsset(CATALOG_ASSET) ?: return EMPTY_CATALOG
        val decoded = try {
            json.decodeFromString(CatalogFile.serializer(), bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
        // A damaged catalog is an empty one: the game still starts and the
        // diagnostics screen reports the real problem.
        return decoded ?: EMPTY_CATALOG
    }

    private fun readManifest(): AssetManifest? {
        val bytes = readAsset(MANIFEST_ASSET) ?: return null
        return try {
            json.decodeFromString(AssetManifest.serializer(), bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    private fun addManifestProblems(manifest: AssetManifest?, problems: MutableList<String>) {
        manifest ?: return
        manifest.files.forEach { file ->
            val bytes = readAsset(file.path)
            when {
                bytes == null -> problems.add("asset ${file.path} is listed in the manifest but missing")
                sha256(bytes) != file.sha256 -> problems.add("asset ${file.path} does not match its manifest hash")
            }
        }
    }

    private fun addCatalogProblems(problems: MutableList<String>) {
        val manifest = manifest()
        val files = manifest?.files?.associate { file: AssetFile -> file.path to file.sha256 }.orEmpty()
        catalog().levels.forEach { entry ->
            val path = levelPath(entry.ordinal)
            if (entry.path.isNotEmpty() && !entry.path.endsWith(path)) {
                problems.add("catalog entry ${entry.id} points at ${entry.path}, not ${path}")
            }
            val bytes = readAsset(path)
            if (bytes == null) {
                problems.add("level ${entry.id} is missing at $path")
                return@forEach
            }
            val hash = sha256(bytes)
            if (hash != entry.sha256) problems.add("level ${entry.id} does not match its catalog hash")
            val manifestHash = files[path]
            when {
                manifestHash == null -> problems.add("level ${entry.id} is not listed in $MANIFEST_ASSET")
                manifestHash != hash -> problems.add("level ${entry.id} does not match its manifest hash")
            }
        }
    }

    /** A level is only used when every hash published for it agrees with the packaged bytes. */
    private fun hashMatches(path: String, bytes: ByteArray): Boolean {
        val hash = sha256(bytes)
        val expected = ArrayList<String>()
        manifest()?.files?.firstOrNull { file -> file.path == path }?.sha256?.let { expected.add(it) }
        catalog().levels.firstOrNull { entry -> levelPath(entry.ordinal) == path }?.sha256?.let { expected.add(it) }
        return expected.all { it == hash }
    }

    private fun summarize(problems: List<String>): List<String> =
        if (problems.size <= MAX_REPORTED_PROBLEMS) {
            problems
        } else {
            problems.take(MAX_REPORTED_PROBLEMS) +
                "… and ${problems.size - MAX_REPORTED_PROBLEMS} more problem(s)"
        }

    companion object {
        const val CATALOG_ASSET = "catalog.json"
        const val MANIFEST_ASSET = "manifest.json"
        const val PUZZLES_ASSET = "puzzles.json"
        const val LEVELS_PER_WORLD = 50
        const val MAX_REPORTED_PROBLEMS = 50
        const val MAX_ASSET_BYTES = 2 * 1024 * 1024

        /** Early builds without packaged content. */
        val EMPTY_CATALOG = CatalogFile(engineVersion = 0, worlds = emptyList(), levels = emptyList())

        /** Same world rule as CatalogBuilder in :leveltool. */
        fun worldOf(ordinal: Int): Int = (ordinal - 1) / LEVELS_PER_WORLD + 1

        fun worldIdOf(ordinal: Int): String = String.format(Locale.ROOT, "world-%02d", worldOf(ordinal))

        /** The packaged asset path of a level, as written by `export-assets`. */
        fun levelPath(ordinal: Int): String =
            String.format(Locale.ROOT, "levels/world-%02d/level-%04d.json", worldOf(ordinal), ordinal)

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte) }

        /** Reads a packaged asset; a missing or oversized file is simply absent. */
        fun assetsOf(context: Context): (String) -> ByteArray? {
            val assets = context.applicationContext.assets
            return { name ->
                try {
                    assets.open(name).use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > MAX_ASSET_BYTES) null else bytes
                    }
                } catch (_: IOException) {
                    null
                }
            }
        }
    }
}
