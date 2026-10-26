package pt.tiago.nightcrush.game

import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.random.SplitMix64
import pt.tiago.nightcrush.data.BoosterInventory
import pt.tiago.nightcrush.data.CollectionState
import pt.tiago.nightcrush.data.DailyChallengeResult
import pt.tiago.nightcrush.data.FreePlayRecords
import pt.tiago.nightcrush.data.GameStats
import pt.tiago.nightcrush.data.LevelProgress
import pt.tiago.nightcrush.data.Profile

/** All progression and economy rules, applied to a profile after a level. */
object ProfileReducer {

    fun key(ordinal: Int, difficulty: Difficulty): String = "%04d-%s".format(ordinal, difficulty.name.lowercase())

    /** Pearls the player will earn for this result, for the result screen. */
    fun pearlsForWin(
        profile: Profile,
        level: LevelFile?,
        difficulty: Difficulty,
        stars: Int,
        mode: GameMode,
    ): Int {
        if (mode != GameMode.CAMPAIGN || level == null) return 2
        val before = profile.levels[key(level.ordinal, difficulty)] ?: LevelProgress()
        val multiplier = when (difficulty) {
            Difficulty.EASY -> 1.0
            Difficulty.MEDIUM -> 1.5
            Difficulty.HARD -> 2.0
        }
        val fullMoon = if (level.isFullMoon) 2 else 1
        val base = when {
            before.wins == 0 -> 10 + 5 * (stars - 1).coerceAtLeast(0)
            stars > before.stars -> 5 * (stars - before.stars)
            else -> 2
        }
        return ((base * multiplier).toInt() * fullMoon).coerceAtLeast(1)
    }

    fun applyResult(
        profile: Profile,
        level: LevelFile?,
        difficulty: Difficulty,
        won: Boolean,
        stars: Int,
        score: Int,
        usedBoosters: Boolean,
        mode: GameMode,
        specialsCreated: IntArray,
        combosMade: IntArray,
        maxCascade: Int,
        nowEpochMs: Long,
    ): Profile {
        var updated = profile
        val difficultyKey = difficulty.name.lowercase()

        if (mode == GameMode.CAMPAIGN && won && level != null) {
            val k = key(level.ordinal, difficulty)
            val before = profile.levels[k] ?: LevelProgress()
            val starsImproved = stars - before.stars
            val progress = LevelProgress(
                stars = maxOf(before.stars, stars),
                bestScore = maxOf(before.bestScore, score),
                wins = before.wins + 1,
                attempts = before.attempts + 1,
                wonWithoutBoosters = before.wonWithoutBoosters || !usedBoosters,
            )
            updated = updated.copy(
                levels = updated.levels + (k to progress),
                pearls = updated.pearls + pearlsForWin(profile, level, difficulty, stars, mode),
            )
            if (starsImproved > 0) {
                updated = updated.copy(unopenedStarChests = chestsAfterStars(updated))
            }
        } else if (mode == GameMode.CAMPAIGN && level != null) {
            val k = key(level.ordinal, difficulty)
            val before = updated.levels[k] ?: LevelProgress()
            updated = updated.copy(levels = updated.levels + (k to before.copy(attempts = before.attempts + 1)))
        }

        // Streak: campaign only, reset on any loss.
        updated = if (mode == GameMode.CAMPAIGN) {
            if (won) {
                val streak = updated.streak + 1
                updated.copy(streak = streak, maxStreak = maxOf(updated.maxStreak, streak))
            } else {
                updated.copy(streak = 0)
            }
        } else {
            updated
        }

        // Stats.
        val stats = updated.stats
        val statsUpdate = stats.copy(
            wins = stats.wins + (difficultyKey to ((stats.wins[difficultyKey] ?: 0) + if (won) 1 else 0)),
            plays = stats.plays + (difficultyKey to ((stats.plays[difficultyKey] ?: 0) + 1)),
            stars = stats.stars + (difficultyKey to ((stats.stars[difficultyKey] ?: 0) + if (won) stars else 0)),
            bestScore = stats.bestScore + (
                difficultyKey to maxOf(stats.bestScore[difficultyKey] ?: 0, score)
                ),
            specialsCreated = mergeCounts(stats.specialsCreated, specialsCreated),
            combos = mergeCounts(stats.combos, combosMade),
            maxCascade = maxOf(stats.maxCascade, maxCascade),
            maxStreak = maxOf(stats.maxStreak, updated.maxStreak),
        )
        updated = updated.copy(stats = statsUpdate)

        // Daily challenge history.
        if (mode == GameMode.DAILY) {
            val day = Missions.epochDay(nowEpochMs)
            val entry = DailyChallengeResult(day, difficultyKey, if (won) stars else 0, score)
            val history = (updated.dailyHistory.filterNot { it.epochDay == day && it.difficulty == difficultyKey } + entry)
                .sortedByDescending { it.epochDay }
                .take(270)
            updated = updated.copy(dailyHistory = history)
        }

        if (mode != GameMode.CAMPAIGN) {
            val free = updated.freePlay
            updated = when (mode) {
                GameMode.TIME_ATTACK -> {
                    val best = maxOf(free.timeAttackScore[difficultyKey] ?: 0, score)
                    updated.copy(freePlay = free.copy(timeAttackScore = free.timeAttackScore + (difficultyKey to best)))
                }
                GameMode.ZEN -> updated
                else -> updated
            }
        }
        return updated
    }

    private fun mergeCounts(existing: Map<String, Int>, delta: IntArray): Map<String, Int> {
        if (delta.isEmpty()) return existing
        val out = existing.toMutableMap()
        for (i in delta.indices) {
            if (delta[i] <= 0) continue
            val k = i.toString()
            out[k] = (out[k] ?: 0) + delta[i]
        }
        return out
    }

    private fun chestsAfterStars(profile: Profile): Int {
        val totalStars = profile.levels.values.sumOf { it.stars }
        val earned = totalStars / 20
        val opened = profile.starChestsOpened
        return (earned - opened).coerceAtLeast(0)
    }

    fun totalStars(profile: Profile): Int = profile.levels.values.sumOf { it.stars }

    fun starsFor(profile: Profile, difficulty: Difficulty): Int =
        profile.levels.filterKeys { it.endsWith("-" + difficulty.name.lowercase()) }.values.sumOf { it.stars }

    fun endlessRecord(profile: Profile, difficulty: Difficulty, levels: Int, score: Int): Profile {
        val free = profile.freePlay
        val key = difficulty.name.lowercase()
        val updated = free.copy(
            endlessLevels = free.endlessLevels + (key to maxOf(free.endlessLevels[key] ?: 0, levels)),
            endlessScore = free.endlessScore + (key to maxOf(free.endlessScore[key] ?: 0, score)),
        )
        return profile.copy(freePlay = updated)
    }
}

/** Puzzle unlocking, stars and records (design 9). */
object PuzzleRules {
    const val PER_PACK = 25
    /** Puzzles solved in a pack before the next pack opens. */
    const val TO_OPEN_NEXT_PACK = 10

    fun pack(number: Int): Int = (number - 1) / PER_PACK + 1

    fun stars(hintsUsed: Int): Int = when (hintsUsed) {
        0 -> 3
        1 -> 2
        else -> 1
    }

    fun solvedIn(profile: Profile, pack: Int): Int =
        profile.freePlay.puzzlesSolved.count { pack(it) == pack }

    fun isPackOpen(profile: Profile, pack: Int): Boolean = pack == 1 || solvedIn(profile, pack - 1) >= TO_OPEN_NEXT_PACK

    /** Inside an open pack, puzzles open one after the other. */
    fun isUnlocked(profile: Profile, number: Int): Boolean {
        val pack = pack(number)
        if (!isPackOpen(profile, pack)) return false
        val first = (pack - 1) * PER_PACK + 1
        return number == first || (number - 1) in profile.freePlay.puzzlesSolved
    }

    fun record(profile: Profile, number: Int, stars: Int): Profile {
        val free = profile.freePlay
        val key = number.toString()
        return profile.copy(
            freePlay = free.copy(
                puzzlesSolved = free.puzzlesSolved + number,
                puzzleStars = free.puzzleStars + (key to maxOf(free.puzzleStars[key] ?: 0, stars)),
            ),
        )
    }
}

/** Win streak rewards (campaign only): specials placed at the start of every level. */
object StreakRules {
    fun bonus(streak: Int): List<pt.tiago.nightcrush.core.model.PreLevelBooster> = when {
        streak >= 7 -> listOf(
            pt.tiago.nightcrush.core.model.PreLevelBooster.RAY,
            pt.tiago.nightcrush.core.model.PreLevelBooster.JELLY,
            pt.tiago.nightcrush.core.model.PreLevelBooster.LANTERN_FISH,
        )
        streak >= 5 -> listOf(pt.tiago.nightcrush.core.model.PreLevelBooster.RAY, pt.tiago.nightcrush.core.model.PreLevelBooster.JELLY)
        streak >= 3 -> listOf(pt.tiago.nightcrush.core.model.PreLevelBooster.RAY)
        else -> emptyList()
    }
}

/** Star chests, world chests and their deterministic contents. */
object ChestRules {

    class Reward(
        val pearls: Int,
        val boosters: BoosterInventory,
        val stickers: List<String>,
        val stickerPearls: Int,
    )

    /** Deterministic from the profile id and the chest index. */
    fun reward(profile: Profile, chestIndex: Int, worldChest: Boolean, bonusSticker: Boolean = false): Reward {
        val stream = SplitMix64(hash("${profile.profileId}:$chestIndex:${if (worldChest) "world" else "star"}"))
        val pearls = if (worldChest) 150 else 40 + stream.nextInt(41)
        val boosters = BoosterInventory(
            harpoon = 1 + stream.nextInt(2),
            freeSwap = stream.nextInt(2),
            shuffle = 1,
            rayStart = stream.nextInt(2),
            jellyStart = if (worldChest) 1 else 0,
            pearlStart = if (worldChest) stream.nextInt(2) else 0,
        )
        val stickers = ArrayList<String>()
        if (worldChest) {
            stickers.add(StickerBook.randomSticker(stream, "rare"))
            stickers.add(StickerBook.randomSticker(stream, null))
            stickers.add(StickerBook.randomSticker(stream, null))
        } else {
            stickers.add(StickerBook.randomSticker(stream, null))
        }
        // The sticker hunt week adds one more, drawn after the regular ones.
        if (bonusSticker) stickers.add(StickerBook.randomSticker(stream, null))
        var stickerPearls = 0
        for (sticker in stickers) {
            stickerPearls += when (StickerBook.rarityOf(sticker)) {
                StickerBook.Rarity.COMMON -> 10
                StickerBook.Rarity.RARE -> 25
                StickerBook.Rarity.EPIC -> 60
            }
        }
        return Reward(pearls, boosters, stickers, stickerPearls)
    }

    /** Key of the world chest for [worldIndex] (0-based) on [difficulty], e.g. "world-01-easy". */
    fun worldKey(worldIndex: Int, difficulty: Difficulty): String = "world-%02d-%s".format(worldIndex + 1, difficulty.name.lowercase())

    fun open(profile: Profile, reward: Reward, worldChest: Boolean, worldKey: String?): Profile {
        var collection = profile.collection
        var pearls = profile.pearls + reward.pearls
        for (sticker in reward.stickers) {
            val owned = (collection.stickers[sticker] ?: 0) > 0
            if (owned) {
                pearls += when (StickerBook.rarityOf(sticker)) {
                    StickerBook.Rarity.COMMON -> 10
                    StickerBook.Rarity.RARE -> 25
                    StickerBook.Rarity.EPIC -> 60
                }
            } else {
                collection = collection.copy(stickers = collection.stickers + (sticker to 1))
            }
        }
        val b = profile.boosters
        val boosters = BoosterInventory(
            rayStart = b.rayStart + reward.boosters.rayStart,
            jellyStart = b.jellyStart + reward.boosters.jellyStart,
            pearlStart = b.pearlStart + reward.boosters.pearlStart,
            harpoon = b.harpoon + reward.boosters.harpoon,
            freeSwap = b.freeSwap + reward.boosters.freeSwap,
            shuffle = b.shuffle + reward.boosters.shuffle,
        )
        return profile.copy(
            pearls = pearls,
            boosters = boosters,
            collection = collection,
            starChestsOpened = if (worldChest) profile.starChestsOpened else profile.starChestsOpened + 1,
            unopenedStarChests = if (worldChest) profile.unopenedStarChests else (profile.unopenedStarChests - 1).coerceAtLeast(0),
            worldChestsClaimed = if (worldChest && worldKey != null) profile.worldChestsClaimed + worldKey else profile.worldChestsClaimed,
            infiniteLivesUntilEpochMs = if (worldChest) {
                profile.infiniteLivesUntilEpochMs.coerceAtLeast(0) + 30 * 60_000L
            } else {
                profile.infiniteLivesUntilEpochMs
            },
        )
    }

    private fun hash(text: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (ch in text.toCharArray()) {
            h = (h xor ch.code.toLong()) * 0x100000001b3L
        }
        return h
    }
}

/** The sticker book: 12 pages of 9, with pearls for completing pages. */
object StickerBook {

    enum class Rarity { COMMON, RARE, EPIC }

    const val PAGES = 12
    const val PER_PAGE = 9

    fun stickerId(page: Int, position: Int): String = "w%02d-%02d".format(page, position)

    fun rarityOf(sticker: String): Rarity {
        val position = sticker.substringAfter('-').toIntOrNull() ?: 1
        return when {
            position >= 9 -> Rarity.EPIC
            position >= 7 -> Rarity.RARE
            else -> Rarity.COMMON
        }
    }

    fun randomSticker(stream: SplitMix64, rarity: String?): String {
        val page = stream.nextInt(PAGES) + 1
        val positions = when (rarity?.lowercase()) {
            "epic" -> intArrayOf(9)
            "rare" -> intArrayOf(7, 8)
            else -> {
                val roll = stream.nextInt(100)
                when {
                    roll < 10 -> intArrayOf(9)
                    roll < 35 -> intArrayOf(7, 8)
                    else -> intArrayOf(1, 2, 3, 4, 5, 6)
                }
            }
        }
        return stickerId(page, positions[stream.nextInt(positions.size)])
    }

    fun pageComplete(collection: CollectionState, page: Int): Boolean =
        (1..PER_PAGE).all { (collection.stickers[stickerId(page, it)] ?: 0) > 0 }

    fun completePages(collection: CollectionState): Int = (1..PAGES).count { pageComplete(collection, it) }

    /** Themes unlock at 4, 8 and 12 complete pages. */
    fun unlockedThemes(collection: CollectionState): Set<String> {
        val pages = completePages(collection)
        val themes = HashSet<String>()
        themes.add("BIO")
        if (pages >= 4) themes.add("GEM")
        if (pages >= 8) themes.add("CANDY")
        if (pages >= 12) themes.add("NEON")
        return themes
    }

    /** 200 pearls once per completed page, tracked by re-checking at open time. */
    fun pearlsForPageCompletion(collection: CollectionState, before: Int): Int =
        (completePages(collection) - before).coerceAtLeast(0) * 200
}

/** The seven day reward track, by local date. */
object DailyReward {

    class Step(val day: Int, val pearls: Int, val booster: String?, val infiniteMinutes: Int, val stickerRare: Boolean)

    val steps = listOf(
        Step(1, 20, null, 0, false),
        Step(2, 0, "shuffle", 0, false),
        Step(3, 0, null, 30, false),
        Step(4, 40, null, 0, false),
        Step(5, 0, "harpoon", 0, false),
        Step(6, 0, "freeSwap", 0, false),
        Step(7, 100, null, 0, true),
    )

    fun isClaimedToday(profile: Profile, epochDay: Long): Boolean = profile.lastDailyClaimEpochDay == epochDay

    /** Claiming advances the track; missing a day resets it to the first step. */
    fun claim(profile: Profile, epochDay: Long, stream: SplitMix64): Pair<Profile, Step>? {
        if (isClaimedToday(profile, epochDay)) return null
        val missed = profile.lastDailyClaimEpochDay >= 0 && epochDay - profile.lastDailyClaimEpochDay > 1
        val nextDay = if (missed || profile.lastDailyClaimEpochDay < 0) 1 else (profile.dailyRewardDay % steps.size) + 1
        val step = steps[nextDay - 1]
        var updated = profile.copy(dailyRewardDay = nextDay, lastDailyClaimEpochDay = epochDay, pearls = profile.pearls + step.pearls)
        step.booster?.let { name ->
            val b = updated.boosters
            updated = updated.copy(
                boosters = when (name) {
                    "shuffle" -> b.copy(shuffle = b.shuffle + 1)
                    "harpoon" -> b.copy(harpoon = b.harpoon + 1)
                    "freeSwap" -> b.copy(freeSwap = b.freeSwap + 2)
                    else -> b.copy(rayStart = b.rayStart + 1)
                },
            )
        }
        if (step.infiniteMinutes > 0) {
            updated = updated.copy(infiniteLivesUntilEpochMs = maxOf(updated.infiniteLivesUntilEpochMs, 0) + step.infiniteMinutes * 60_000L)
        }
        if (nextDay == 7) {
            val b = updated.boosters
            updated = updated.copy(boosters = b.copy(pearlStart = b.pearlStart + 1))
            if (step.stickerRare) {
                val sticker = StickerBook.randomSticker(stream, "rare")
                if ((updated.collection.stickers[sticker] ?: 0) == 0) {
                    updated = updated.copy(collection = updated.collection.copy(stickers = updated.collection.stickers + (sticker to 1)))
                } else {
                    updated = updated.copy(pearls = updated.pearls + 25)
                }
            }
        }
        return updated to step
    }
}

/** The weekly offline events: see Missions.kt for the rules. */
