package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.ComboKind
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Order
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.SpecialOrderKind
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.play
import pt.tiago.nightcrush.core.testutil.swap

class ObjectivesTest {

    private fun blueCounts(n: Int): IntArray {
        val counts = IntArray(6)
        counts[0] = n
        return counts
    }

    @Test
    fun `collecting pieces completes a collect objective`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            objective(Objective.CollectColors(blueCounts(3)))
            moves(1)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(3, result.state.progress[0][0])
        assertEquals(Outcome.Won, result.state.outcome)
    }

    @Test
    fun `a level is won only when every objective is complete`() {
        val blue = blueCounts(3)
        val other = IntArray(6)
        other[1] = 5
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            objective(Objective.CollectColors(blue))
            objective(Objective.CollectColors(other))
            moves(5)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(Outcome.Playing, result.state.outcome)
    }

    @Test
    fun `delivering a lantern completes its objective`() {
        val s = boardOf(
            "LBG",
            ".GB",
            ".YG",
        ) {
            exit(2, 0)
            objective(Objective.DeliverLanterns(1))
            moves(4)
        }
        val result = s.swap(1, 1, 1, 2)
        assertTrue(result.events.any { it is GameEvent.LanternDelivered })
        assertEquals(1, result.state.progress[0][0])
        assertEquals(Outcome.Won, result.state.outcome)
    }

    @Test
    fun `freeing a seahorse completes its objective`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            bubble(0, 0)
            objective(Objective.FreeSeahorses(1))
            moves(4)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(1, result.state.progress[0][0])
        assertEquals(Outcome.Won, result.state.outcome)
    }

    @Test
    fun `spreading glow to every cell completes its objective`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            objective(Objective.SpreadGlow)
            moves(4)
            for (r in 0 until 3) for (c in 0 until 3) glow(r, c)
            glow(0, 1, lit = false)
            glow(1, 1, lit = true)
            glow(2, 1, lit = false)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(Outcome.Won, result.state.outcome)
        assertEquals(9, result.state.progress[0][0])
    }

    @Test
    fun `breaking an objective blocker completes its objective`() {
        val counts = IntArray(6)
        counts[Blockers.KIND_ORDER.indexOf(Blockers.kindOf(1))] = 1
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            coral(0, 0, 1)
            objective(Objective.ClearBlockers(counts))
            moves(4)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(1, result.state.progress[0][0])
        assertEquals(Outcome.Won, result.state.outcome)
    }

    @Test
    fun `an order for a ray counts created rays`() {
        val s = boardOf("BGBB", "YBGY", "GYBG") {
            objective(Objective.Orders(listOf(Order.MakeSpecial(SpecialOrderKind.RAY, 1))))
            moves(3)
        }
        val result = s.swap(1, 1, 0, 1)
        assertEquals(1, result.state.progress[0][0])
        assertEquals(Outcome.Won, result.state.outcome)
    }

    @Test
    fun `an order for layers counts blocker damage`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            coral(0, 0, 2)
            objective(Objective.Orders(listOf(Order.BreakLayers(pt.tiago.nightcrush.core.model.BlockerKind.CORAL, 1))))
            moves(3)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(1, result.state.progress[0][0])
    }

    @Test
    fun `an order for a special combination counts the combo`() {
        val s = boardOf("BYG", "YGB", "GBY") {
            rayH(1, 0, 0)
            rayV(1, 1, 0)
            objective(Objective.Orders(listOf(Order.MakeCombo(ComboKind.RAY_RAY, 1))))
            moves(3)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(1, result.state.progress[0][0])
    }

    @Test
    fun `time up loses a level unless the objectives are complete`() {
        val s = boardOf("BGB", "GBY", "YYG") {
            time(90)
            objective(Objective.ClearSilt(1))
        }
        val lost = s.play(Move.TimeUp)
        assertEquals(Outcome.Lost.OutOfTime, lost.state.outcome)

        val done = boardOf("BGB", "GBY", "YYG") {
            time(30)
            objective(Objective.ClearSilt(1))
        }
        done.progress[0][0] = 1
        val won = done.play(Move.TimeUp)
        assertEquals(Outcome.Won, won.state.outcome)
    }

    @Test
    fun `the winning move that spends the last move still wins`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            objective(Objective.CollectColors(blueCounts(3)))
            moves(1)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(0, result.state.movesLeft)
        assertEquals(Outcome.Won, result.state.outcome)
    }
}
