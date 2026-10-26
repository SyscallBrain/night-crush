package pt.tiago.nightcrush.leveltool

import kotlinx.serialization.Serializable
import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.model.LevelFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * The pool of levels the daily challenge may pick from.
 *
 * Eligibility is deterministic and content-driven: no tutorial levels (they
 * force specific swaps), no full moon levels (they have their own reward curve)
 * and no timed levels (the daily challenge counts moves, not seconds). The list
 * only depends on the catalog, so every device derives the same pool.
 */
object DailyPool {

    @Serializable
    data class PoolFile(
        val schemaVersion: Int = 1,
        val engineVersion: Int,
        val ids: List<String>,
    ) {
        /** Convenience for the current engine version: `PoolFile(ids)`. */
        constructor(ids: List<String>) : this(engineVersion = ENGINE_VERSION, ids = ids)
    }

    /** Level ids eligible for the daily challenge: no tutorial, not full moon, moves limit, revision current. */
    fun eligible(catalogPath: Path = Path.of("content/catalog.json")): List<String> {
        if (!catalogPath.exists()) return emptyList()
        val catalog = levelJson.decodeFromString(CatalogFile.serializer(), Files.readString(catalogPath))
        val ids = ArrayList<String>()
        for (entry in catalog.levels.sortedBy { it.ordinal }) {
            val path = Path.of(entry.path)
            if (!path.exists()) continue
            val level = readLevel(path)
            if (!isEligible(level, entry)) continue
            ids += level.id
        }
        return ids
    }

    private fun isEligible(level: LevelFile, entry: LevelEntry): Boolean {
        if (level.isFullMoon) return false
        if (level.tutorial != null) return false
        if (level.revision != entry.revision) return false
        if (level.variants.isEmpty()) return false
        return level.variants.values.all { it.limit.type.lowercase() == "moves" }
    }
}
