package pt.tiago.nightcrush.game

import pt.tiago.nightcrush.core.model.ComboKind
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.SpecialOrderKind
import pt.tiago.nightcrush.data.Profile

/**
 * The local achievements (design 8.9). Every one is a pure reading of the
 * profile, so they can be re-evaluated at any time and never drift: unlocking
 * only records the id in [Profile.achievements].
 */
object Achievements {

    /** What the rules need from the catalog: each world's ordinals and the Full Moon levels. */
    class Catalog(val worlds: List<IntRange>, val fullMoons: Set<Int>, val available: Set<Int>)

    enum class Group { SPECIALS, COMBOS, PLAY, WORLDS, META }

    /** [progress] returns (current, target); the achievement unlocks at target. */
    class Achievement(val id: String, val group: Group, val progress: (Profile, Catalog) -> Pair<Int, Int>)

    private fun special(profile: Profile, kind: SpecialOrderKind): Int = profile.stats.specialsCreated[kind.ordinal.toString()] ?: 0
    private fun combo(profile: Profile, kind: ComboKind): Int = profile.stats.combos[kind.ordinal.toString()] ?: 0

    private fun wonAnywhere(profile: Profile, ordinal: Int): Boolean =
        Difficulty.entries.any { (profile.levels[ProfileReducer.key(ordinal, it)]?.wins ?: 0) > 0 }

    private fun threeStarLevels(profile: Profile): Int =
        profile.levels.keys.map { it.substringBefore('-') }.distinct().count { ordinal ->
            Difficulty.entries.any { (profile.levels["$ordinal-${it.name.lowercase()}"]?.stars ?: 0) >= 3 }
        }

    private fun worldDone(profile: Profile, catalog: Catalog, world: Int): Pair<Int, Int> {
        val range = catalog.worlds.getOrNull(world) ?: return 0 to 1
        val levels = range.filter { it in catalog.available }
        return levels.count { wonAnywhere(profile, it) } to levels.size.coerceAtLeast(1)
    }

    private fun stickers(profile: Profile): Int = profile.collection.stickers.count { it.value > 0 }

    val all: List<Achievement> = buildList {
        for (kind in SpecialOrderKind.entries) {
            add(Achievement("first_${kind.name.lowercase()}", Group.SPECIALS) { p, _ -> special(p, kind).coerceAtMost(1) to 1 })
        }
        add(Achievement("specials_100", Group.SPECIALS) { p, _ -> SpecialOrderKind.entries.sumOf { special(p, it) } to 100 })
        for (kind in ComboKind.entries) {
            add(Achievement("combo_${kind.name.lowercase()}", Group.COMBOS) { p, _ -> combo(p, kind).coerceAtMost(1) to 1 })
        }
        add(Achievement("cascade_6", Group.PLAY) { p, _ -> p.stats.maxCascade to 6 })
        add(Achievement("cascade_10", Group.PLAY) { p, _ -> p.stats.maxCascade to 10 })
        add(Achievement("win_hard", Group.PLAY) { p, _ -> (p.stats.wins["hard"] ?: 0).coerceAtMost(1) to 1 })
        add(Achievement("three_stars_10", Group.PLAY) { p, _ -> threeStarLevels(p) to 10 })
        add(Achievement("three_stars_50", Group.PLAY) { p, _ -> threeStarLevels(p) to 50 })
        add(Achievement("three_stars_150", Group.PLAY) { p, _ -> threeStarLevels(p) to 150 })
        add(Achievement("no_boosters_10", Group.PLAY) { p, _ ->
            p.levels.values.count { it.wonWithoutBoosters && it.stars >= 3 } to 10
        })
        add(Achievement("streak_5", Group.PLAY) { p, _ -> p.maxStreak to 5 })
        add(Achievement("streak_10", Group.PLAY) { p, _ -> p.maxStreak to 10 })
        add(Achievement("endless_10", Group.PLAY) { p, _ -> (p.freePlay.endlessLevels.values.maxOrNull() ?: 0) to 10 })
        add(Achievement("time_attack_100k", Group.PLAY) { p, _ -> (p.freePlay.timeAttackScore.values.maxOrNull() ?: 0) to 100_000 })
        add(Achievement("daily_10", Group.PLAY) { p, _ -> p.dailyHistory.size to 10 })
        add(Achievement("puzzles_25", Group.PLAY) { p, _ -> p.freePlay.puzzlesSolved.size to 25 })
        for (world in 0 until 12) {
            add(Achievement("world_%02d".format(world + 1), Group.WORLDS) { p, c -> worldDone(p, c, world) })
        }
        add(Achievement("full_moons_world", Group.WORLDS) { p, c ->
            val best = c.worlds.maxOfOrNull { range ->
                val moons = range.filter { it in c.fullMoons && it in c.available }
                if (moons.isEmpty()) 0 else moons.count { wonAnywhere(p, it) } * 100 / moons.size
            } ?: 0
            best to 100
        })
        add(Achievement("daily_reward_7", Group.META) { p, _ -> p.stats.dailyRewardsClaimed to 7 })
        add(Achievement("daily_reward_30", Group.META) { p, _ -> p.stats.dailyRewardsClaimed to 30 })
        add(Achievement("page_1", Group.META) { p, _ -> StickerBook.completePages(p.collection).coerceAtMost(1) to 1 })
        add(Achievement("pages_4", Group.META) { p, _ -> StickerBook.completePages(p.collection) to 4 })
        add(Achievement("collection_full", Group.META) { p, _ -> stickers(p) to StickerBook.PAGES * StickerBook.PER_PAGE })
        add(Achievement("pearls_1000", Group.META) { p, _ -> p.pearls to 1000 })
    }

    fun isUnlocked(profile: Profile, catalog: Catalog, achievement: Achievement): Boolean {
        val (value, target) = achievement.progress(profile, catalog)
        return value >= target
    }

    /** String resources follow the id: ach_<id>_title and ach_<id>_desc. */
    fun titleRes(id: String): Int = stringRes("ach_${id}_title")
    fun descRes(id: String): Int = stringRes("ach_${id}_desc")

    private fun stringRes(name: String): Int = pt.tiago.nightcrush.R.string::class.java.getField(name).getInt(null)

    /** Ids that are reached now but not recorded yet. */
    fun newlyUnlocked(profile: Profile, catalog: Catalog): List<String> =
        all.filter { it.id !in profile.achievements && isUnlocked(profile, catalog, it) }.map { it.id }
}
