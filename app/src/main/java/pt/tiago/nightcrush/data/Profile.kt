package pt.tiago.nightcrush.data

import kotlinx.serialization.Serializable
import pt.tiago.nightcrush.core.model.MoveSpec

/**
 * The whole local profile: progression, economy, collection and settings.
 * Written atomically with a recovery copy; never overwritten silently when
 * unreadable (see ProfileRepository).
 */
@Serializable
data class Profile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val profileId: String,
    val createdAtEpochMs: Long,
    val lastSeenEpochMs: Long,
    val pearls: Int = 0,
    val lives: Int = MAX_LIVES,
    val lastLifeAnchorEpochMs: Long = 0,
    val infiniteLivesUntilEpochMs: Long = 0,
    val boosters: BoosterInventory = BoosterInventory(),
    val streak: Int = 0,
    val maxStreak: Int = 0,
    /** Key "NNNN-easy" -> progress. */
    val levels: Map<String, LevelProgress> = emptyMap(),
    /** Number of 20-star chests opened. */
    val starChestsOpened: Int = 0,
    val unopenedStarChests: Int = 0,
    /** "world-01-easy" world chests already claimed. */
    val worldChestsClaimed: Set<String> = emptySet(),
    /** 1..7 position in the daily reward track; 0 when nothing claimed yet. */
    val dailyRewardDay: Int = 0,
    val lastDailyClaimEpochDay: Long = -1,
    val missions: DailyMissions = DailyMissions(),
    val collection: CollectionState = CollectionState(),
    val achievements: Set<String> = emptySet(),
    val stats: GameStats = GameStats(),
    val freePlay: FreePlayRecords = FreePlayRecords(),
    val dailyHistory: List<DailyChallengeResult> = emptyList(),
    val weeklyEvent: WeeklyEventState = WeeklyEventState(),
    val settings: GameSettings = GameSettings(),
    val savedMatch: SavedMatch? = null,
    val tutorialSeen: Set<String> = emptySet(),
) {
    companion object {
        const val SCHEMA_VERSION = 2
        const val MAX_LIVES = 5
        const val LIFE_REGEN_MILLIS = 20 * 60 * 1000L

        fun fresh(nowEpochMs: Long, profileId: String): Profile = Profile(
            profileId = profileId,
            createdAtEpochMs = nowEpochMs,
            lastSeenEpochMs = nowEpochMs,
            lastLifeAnchorEpochMs = nowEpochMs,
        )
    }
}

@Serializable
data class BoosterInventory(
    val rayStart: Int = 3,
    val jellyStart: Int = 2,
    val pearlStart: Int = 1,
    val harpoon: Int = 3,
    val freeSwap: Int = 2,
    val shuffle: Int = 2,
)

@Serializable
data class LevelProgress(
    val stars: Int = 0,
    val bestScore: Int = 0,
    val wins: Int = 0,
    val attempts: Int = 0,
    val wonWithoutBoosters: Boolean = false,
)

@Serializable
data class DailyMissions(
    val epochDay: Long = -1,
    val ids: List<String> = emptyList(),
    val progress: List<Int> = emptyList(),
    val claimed: List<Boolean> = emptyList(),
    val bonusClaimed: Boolean = false,
)

@Serializable
data class CollectionState(
    val stickers: Map<String, Int> = emptyMap(),
    val panoramaUnlocked: Set<String> = emptySet(),
)

@Serializable
data class GameStats(
    /** Per difficulty: keys "easy"/"medium"/"hard". */
    val wins: Map<String, Int> = emptyMap(),
    val plays: Map<String, Int> = emptyMap(),
    val stars: Map<String, Int> = emptyMap(),
    val bestScore: Map<String, Int> = emptyMap(),
    val specialsCreated: Map<String, Int> = emptyMap(),
    val combos: Map<String, Int> = emptyMap(),
    val maxCascade: Int = 0,
    val timePlayedMillis: Long = 0,
    val maxStreak: Int = 0,
    /** Daily rewards claimed in total, for the 7 and 30 day achievements. */
    val dailyRewardsClaimed: Int = 0,
)

@Serializable
data class FreePlayRecords(
    /** Per difficulty: levels beaten in one endless run. */
    val endlessLevels: Map<String, Int> = emptyMap(),
    val endlessScore: Map<String, Int> = emptyMap(),
    val timeAttackScore: Map<String, Int> = emptyMap(),
    val puzzlesSolved: Set<Int> = emptySet(),
    /** Best stars per puzzle number ("12" -> 3): 3 without hints, 2 with one, 1 with more. */
    val puzzleStars: Map<String, Int> = emptyMap(),
)

@Serializable
data class DailyChallengeResult(
    val epochDay: Long,
    val difficulty: String,
    val stars: Int,
    val score: Int,
)

@Serializable
data class WeeklyEventState(
    val isoWeek: Long = -1,
    val progress: Int = 0,
    val claimedTiers: List<Int> = emptyList(),
    val pearlsEarned: Int = 0,
)

@Serializable
data class SavedMatch(
    val levelId: String,
    val difficulty: String,
    val boostersUsed: List<String> = emptyList(),
    val moves: List<MoveSpec> = emptyList(),
    val startedAtEpochMs: Long = 0,
    val livesSpent: Boolean = false,
    /** Parallel to [moves]: swap, harpoon, freeSwap or shuffle. Empty means all swaps (older saves). */
    val kinds: List<String> = emptyList(),
)

@Serializable
data class GameSettings(
    val soundOn: Boolean = true,
    val musicOn: Boolean = true,
    val vibrationOn: Boolean = true,
    /** 1.0, 1.5 or 2.0. */
    val animationSpeed: Float = 1f,
    /** always | delay | buttonOnly */
    val hints: String = "delay",
    val confirmBoosters: Boolean = true,
    /** normal | deuteranopia | protanopia | tritanopia */
    val colorPalette: String = "normal",
    val distinctSymbols: Boolean = true,
    val reducedMotion: Boolean = false,
    val leftHanded: Boolean = false,
    val largerText: Boolean = false,
    /** BIO | GEM | CANDY | NEON */
    val boardTheme: String = "BIO",
    /** 0..1, separate for effects and music. */
    val sfxVolume: Float = 1f,
    val musicVolume: Float = 0.6f,
    /** Draws a high-contrast symbol inside every piece, on top of its shape. */
    val contrastSymbols: Boolean = false,
    /** False until the player toggles reduced motion; until then it follows the system. */
    val reducedMotionChosen: Boolean = false,
)
