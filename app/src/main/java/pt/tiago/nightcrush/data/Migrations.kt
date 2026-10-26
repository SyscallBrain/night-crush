package pt.tiago.nightcrush.data

/**
 * Schema migrations stay explicit: a version this build does not understand
 * never becomes an empty profile, it is rejected and the file is kept.
 *
 * To add version 2:
 *  1. bump [Profile.SCHEMA_VERSION] to 2;
 *  2. add a branch here, `2 -> migrateV1ToV2(profile)`, ordered from the
 *     newest known version down, so old files walk the chain;
 *  3. keep the rewrite pure (no clock, no ids) so it stays testable.
 */
object Migrations {

    /** The oldest schema this build can still read and rewrite. */
    const val EARLIEST_SUPPORTED_VERSION = 1

    /** Decodes [text], migrates it and validates the result. Null when it cannot be used. */
    fun migrate(text: String): Profile? {
        val decoded = ProfileSerializer.decode(text) ?: return null
        return migrate(decoded)
    }

    /** Migrates an already decoded profile. Null when the version is unsupported or invalid. */
    fun migrate(profile: Profile): Profile? {
        val migrated = when (profile.schemaVersion) {
            Profile.SCHEMA_VERSION -> profile
            1 -> migrateV1ToV2(profile)
            else -> return null
        }
        val problems = ProfileValidator.problems(migrated)
        return if (problems.isEmpty()) migrated else null
    }

    /**
     * Version 2 drops statistics that only the pre-v4 engine could produce: a
     * cascade loop ran up to its 400-step guard and left impossible records
     * (cascades in the hundreds, scores in the tens of millions). The best
     * certified level scores about 1 M with cascades under 40.
     */
    internal fun migrateV1ToV2(profile: Profile): Profile {
        val stats = profile.stats
        val sane = stats.copy(
            maxCascade = if (stats.maxCascade > MAX_PLAUSIBLE_CASCADE) 0 else stats.maxCascade,
            bestScore = stats.bestScore.filterValues { it <= MAX_PLAUSIBLE_SCORE },
        )
        val free = profile.freePlay
        return profile.copy(
            schemaVersion = 2,
            stats = sane,
            freePlay = free.copy(
                endlessScore = free.endlessScore.filterValues { it <= MAX_PLAUSIBLE_SCORE * 20 },
                timeAttackScore = free.timeAttackScore.filterValues { it <= MAX_PLAUSIBLE_SCORE },
            ),
            levels = profile.levels.mapValues { (_, progress) ->
                if (progress.bestScore > MAX_PLAUSIBLE_SCORE) progress.copy(bestScore = 0) else progress
            },
        )
    }

    private const val MAX_PLAUSIBLE_CASCADE = 100
    private const val MAX_PLAUSIBLE_SCORE = 5_000_000

    /** True when [profile] needs no rewrite to be understood by this build. */
    fun isSupported(profile: Profile): Boolean = profile.schemaVersion in EARLIEST_SUPPORTED_VERSION..Profile.SCHEMA_VERSION
}
