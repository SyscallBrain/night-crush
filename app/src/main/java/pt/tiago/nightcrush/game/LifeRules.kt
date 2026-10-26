package pt.tiago.nightcrush.game

import pt.tiago.nightcrush.data.Profile

/**
 * Lives: five maximum, one regained every 20 minutes of wall-clock time,
 * counted offline from the last anchor. A clock that moves backwards never
 * costs the player lives.
 */
object LifeRules {

    fun refill(profile: Profile, nowEpochMs: Long): Profile {
        if (profile.infiniteLivesUntilEpochMs in 1 until nowEpochMs) {
            // Expired infinite lives are simply cleared.
            return refillLives(profile.copy(infiniteLivesUntilEpochMs = 0), nowEpochMs)
        }
        return refillLives(profile, nowEpochMs)
    }

    private fun refillLives(profile: Profile, nowEpochMs: Long): Profile {
        if (profile.lives >= Profile.MAX_LIVES) {
            return if (profile.lastLifeAnchorEpochMs > nowEpochMs) {
                profile.copy(lastLifeAnchorEpochMs = nowEpochMs)
            } else {
                profile
            }
        }
        if (profile.lastLifeAnchorEpochMs > nowEpochMs) {
            // The clock went backwards: re-anchor without punishing.
            return profile.copy(lastLifeAnchorEpochMs = nowEpochMs)
        }
        val elapsed = nowEpochMs - profile.lastLifeAnchorEpochMs
        val gained = (elapsed / Profile.LIFE_REGEN_MILLIS).toInt()
        if (gained <= 0) return profile
        val lives = (profile.lives + gained).coerceAtMost(Profile.MAX_LIVES)
        val anchor = if (lives >= Profile.MAX_LIVES) {
            nowEpochMs
        } else {
            profile.lastLifeAnchorEpochMs + gained * Profile.LIFE_REGEN_MILLIS
        }
        return profile.copy(lives = lives, lastLifeAnchorEpochMs = anchor)
    }

    fun spend(profile: Profile, nowEpochMs: Long): Profile {
        if (profile.infiniteLivesUntilEpochMs > nowEpochMs) return profile
        val current = refillLives(profile, nowEpochMs)
        if (current.lives <= 0) return current
        val wasFull = current.lives >= Profile.MAX_LIVES
        return current.copy(
            lives = current.lives - 1,
            lastLifeAnchorEpochMs = if (wasFull) nowEpochMs else current.lastLifeAnchorEpochMs,
        )
    }

    fun grantInfinite(profile: Profile, nowEpochMs: Long, minutes: Int): Profile =
        profile.copy(
            infiniteLivesUntilEpochMs = maxOf(profile.infiniteLivesUntilEpochMs, nowEpochMs) + minutes * 60_000L,
        )

    fun nextLifeInMillis(profile: Profile, nowEpochMs: Long): Long {
        if (profile.lives >= Profile.MAX_LIVES || profile.infiniteLivesUntilEpochMs > nowEpochMs) return 0
        val elapsed = (nowEpochMs - profile.lastLifeAnchorEpochMs).coerceAtLeast(0)
        val remaining = Profile.LIFE_REGEN_MILLIS - elapsed % Profile.LIFE_REGEN_MILLIS
        return remaining
    }
}
