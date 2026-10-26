package pt.tiago.nightcrush.leveltool

import kotlinx.serialization.encodeToString
import pt.tiago.nightcrush.core.ENGINE_VERSION
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Rebuilds content/catalog.json from the published level files.
 *
 * The catalog is the single index the app and the verifier read: one entry per
 * level with its revision and SHA-256 so a tampered or stale file is caught
 * before any of it reaches the device.
 */
object CatalogBuilder {

    private const val LEVELS_PER_WORLD = 50
    private const val WORLDS = 12

    /** The twelve world names, in world order. Portuguese and English are the same. */
    private val WORLD_NAMES = listOf(
        "Ria Formosa",
        "Sagres",
        "Arrábida",
        "Berlengas",
        "Nazaré",
        "Costa Vicentina",
        "Fajãs dos Açores",
        "Porto Moniz",
        "Gruta de Benagil",
        "Ilhas Selvagens",
        "Fossa Abissal",
        "Atlântico Profundo",
    )

    /** Rebuilds the catalog under [contentRoot] from the level files below it. */
    fun rebuild(contentRoot: Path): CatalogFile {
        val catalog = CatalogFile(
            engineVersion = ENGINE_VERSION,
            worlds = worlds(),
            levels = levels(contentRoot),
        )
        writeText(contentRoot.resolve("catalog.json"), levelJson.encodeToString(CatalogFile.serializer(), catalog))
        return catalog
    }

    private fun worlds(): List<WorldEntry> = (1..WORLDS).map { world ->
        val name = WORLD_NAMES[world - 1]
        WorldEntry(
            id = "world-%02d".format(world),
            namePt = name,
            nameEn = name,
            from = (world - 1) * LEVELS_PER_WORLD + 1,
            to = world * LEVELS_PER_WORLD,
        )
    }

    private fun levels(contentRoot: Path): List<LevelEntry> {
        val levelsRoot = contentRoot.resolve("levels")
        if (!Files.isDirectory(levelsRoot)) return emptyList()
        val files = Files.walk(levelsRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.name.startsWith("level-") && it.extension == "json" }
                .sorted()
                .toList()
        }
        val entries = ArrayList<LevelEntry>(files.size)
        for (file in files) {
            val level = readLevel(file)
            entries += LevelEntry(
                id = level.id,
                ordinal = level.ordinal,
                worldId = level.worldId,
                path = repositoryPath(contentRoot, file),
                revision = level.revision,
                sha256 = sha256File(file),
                isFullMoon = level.isFullMoon,
                titlePt = level.titlePt,
                titleEn = level.titleEn,
            )
        }
        return entries.sortedBy { it.ordinal }
    }

    /**
     * The catalog path is relative to the repository root (the parent of
     * [contentRoot]) so the CLI, the verifier and the asset exporter, which all
     * run from the repository root, can resolve it from the same working
     * directory. Separators are always forward slashes.
     */
    private fun repositoryPath(contentRoot: Path, file: Path): String {
        val root = contentRoot.toAbsolutePath().normalize()
        val absolute = file.toAbsolutePath().normalize()
        val relative = when {
            root.parent != null && absolute.startsWith(root.parent) -> root.parent.relativize(absolute)
            absolute.startsWith(root) -> root.relativize(absolute)
            else -> absolute
        }
        return relative.joinToString("/") { it.toString() }
    }
}
