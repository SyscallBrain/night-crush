package pt.tiago.nightcrush.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.ComboKind
import pt.tiago.nightcrush.data.GameStats
import pt.tiago.nightcrush.data.Profile

class AchievementsTest {
    private val catalog = Achievements.Catalog(
        worlds = (0 until 12).map { (it * 50 + 1)..(it * 50 + 50) },
        fullMoons = setOf(10, 20),
        available = (1..600).toSet(),
    )
    private val fresh = Profile.fresh(0L, "p")

    @Test fun thereAreAboutFortyFiveWithUniqueIdsAndTexts() {
        assertTrue(Achievements.all.size in 40..50)
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
        for (achievement in Achievements.all) {
            // Throws when a string resource is missing.
            Achievements.titleRes(achievement.id)
            Achievements.descRes(achievement.id)
        }
    }

    @Test fun aFreshProfileHasNothing() {
        assertEquals(emptyList<String>(), Achievements.newlyUnlocked(fresh, catalog))
    }

    @Test fun statsUnlockTheMatchingOnesOnce() {
        val profile = fresh.copy(stats = GameStats(maxCascade = 7, combos = mapOf(ComboKind.PEARL_PEARL.ordinal.toString() to 1)))
        val unlocked = Achievements.newlyUnlocked(profile, catalog)
        assertEquals(listOf("combo_pearl_pearl", "cascade_6"), unlocked)
        assertEquals(emptyList<String>(), Achievements.newlyUnlocked(profile.copy(achievements = unlocked.toSet()), catalog))
    }
}
