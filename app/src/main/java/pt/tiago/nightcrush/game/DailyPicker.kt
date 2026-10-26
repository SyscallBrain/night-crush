package pt.tiago.nightcrush.game

import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.random.SplitMix64
import pt.tiago.nightcrush.data.CatalogRepository

/**
 * The daily challenge: one eligible level per day and difficulty, chosen from
 * the local date. Eligible means a normal campaign level with a move limit and
 * no tutorial.
 */
object DailyPicker {

    fun pick(
        catalog: CatalogRepository,
        levels: List<pt.tiago.nightcrush.core.model.LevelEntry>,
        epochDay: Long,
        difficulty: Difficulty,
    ): LevelFile? {
        if (levels.isEmpty()) return null
        val stream = SplitMix64(epochDay * 1_000_003L + difficulty.ordinal * 7919L)
        var index = stream.nextInt(levels.size)
        repeat(levels.size) {
            val entry = levels[index % levels.size]
            val level = catalog.level(entry.ordinal)
            if (level != null && eligible(level, difficulty)) return level
            index++
        }
        return null
    }

    private fun eligible(level: LevelFile, difficulty: Difficulty): Boolean {
        if (level.isFullMoon || level.tutorial != null) return false
        val variant = level.variants[difficulty.name.lowercase()] ?: return false
        return variant.limit.type == "moves"
    }
}
