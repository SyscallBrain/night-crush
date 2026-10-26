package pt.tiago.nightcrush.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.data.Profile

class PuzzleRulesTest {
    private val fresh = Profile.fresh(0L, "p")

    @Test fun puzzlesOpenOneAfterTheOtherInsideAPack() {
        assertTrue(PuzzleRules.isUnlocked(fresh, 1))
        assertFalse(PuzzleRules.isUnlocked(fresh, 2))
        assertTrue(PuzzleRules.isUnlocked(PuzzleRules.record(fresh, 1, 3), 2))
    }

    @Test fun theNextPackOpensAfterTenSolved() {
        var profile = fresh
        assertFalse(PuzzleRules.isUnlocked(profile, 26))
        for (n in 1..10) profile = PuzzleRules.record(profile, n, 1)
        assertTrue(PuzzleRules.isPackOpen(profile, 2))
        assertTrue(PuzzleRules.isUnlocked(profile, 26))
        assertFalse(PuzzleRules.isUnlocked(profile, 27))
    }

    @Test fun hintsCostStarsAndTheBestResultIsKept() {
        assertEquals(3, PuzzleRules.stars(0))
        assertEquals(2, PuzzleRules.stars(1))
        assertEquals(1, PuzzleRules.stars(4))
        val profile = PuzzleRules.record(PuzzleRules.record(fresh, 5, 3), 5, 1)
        assertEquals(3, profile.freePlay.puzzleStars["5"])
    }
}
