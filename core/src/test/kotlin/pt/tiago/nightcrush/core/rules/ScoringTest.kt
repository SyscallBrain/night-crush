package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.swap
import kotlin.math.roundToInt

class ScoringTest {

    @Test
    fun `a plain three scores sixty`() {
        val result = boardOf("BGB", "GBY", "YYG").swap(0, 1, 1, 1)
        assertEquals(60, result.state.score)
    }

    @Test
    fun `a four scores one hundred and twenty and creates a ray`() {
        val result = boardOf("BGBB", "YBGY", "GYBG").swap(1, 1, 0, 1)
        assertEquals(120, result.state.score)
        assertTrue(result.events.any { it is GameEvent.SpecialCreated })
    }

    @Test
    fun `a ray scores sixty for every cell it clears`() {
        val s = boardOf("GBG", "YGB", "YBY") { rayH(1, 0, 0) }
        // 60 for the three, plus 60 for each of the two extra cells the ray clears.
        val result = s.swap(1, 0, 1, 1)
        assertEquals(180, result.state.score)
    }

    @Test
    fun `a jelly's second explosion scores at the next cascade step`() {
        val s = boardOf("GBG", "YGB", "YBY") { jelly(1, 0, 0) }
        val result = s.swap(1, 0, 1, 1)
        // 60 (match) + 6 x 60 (first explosion) + 60 x 2 (the jelly itself in cascade 2).
        assertEquals(540, result.state.score)
        assertTrue(result.events.filterIsInstance<GameEvent.CascadeStep>().any { it.index == 2 })
    }

    @Test
    fun `clearing silt scores one hundred per layer`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            silt(1, 1, 1)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(160, result.state.score)
    }

    @Test
    fun `the difficulty multiplier applies to the final score`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            silt(1, 1, 1)
            objective(Objective.ClearSilt(1))
            moves(3)
            difficulty(Difficulty.HARD)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(Outcome.Won, result.state.outcome)
        assertTrue(result.events.any { it is GameEvent.FinaleStarted })
        val deltas = result.events.filterIsInstance<GameEvent.ScoreDelta>().sumOf { it.points }
        assertEquals((deltas * 2.0).roundToInt(), result.state.score)
    }

    @Test
    fun `easy levels keep the raw score`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            silt(1, 1, 1)
            objective(Objective.ClearSilt(1))
            moves(3)
            difficulty(Difficulty.EASY)
        }
        val result = s.swap(1, 0, 1, 1)
        val deltas = result.events.filterIsInstance<GameEvent.ScoreDelta>().sumOf { it.points }
        assertEquals(deltas, result.state.score)
    }

    @Test
    fun `running out of moves is a loss with no extra chance`() {
        val s = boardOf("BGB", "GBY", "YYG") { moves(1) }
        val result = s.swap(0, 1, 1, 1)
        assertEquals(Outcome.Lost.OutOfMoves, result.state.outcome)
    }
}
