package pt.tiago.nightcrush.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.rules.BoardSnapshot
import pt.tiago.nightcrush.core.rules.GameEngine

class AnimationTimelineTest {

    private val words = listOf("a", "b", "c", "d", "e")

    /** Rows of colour indices; swapping (1,2) with (2,2) completes 0-0-0 on the bottom row. */
    private fun board(): GameState {
        val rows = listOf(
            intArrayOf(1, 2, 3, 4),
            intArrayOf(2, 3, 0, 1),
            intArrayOf(0, 0, 1, 3),
        )
        val state = GameState(width = 4, height = 3)
        for (r in rows.indices) for (c in rows[r].indices) state.pieces[r * 4 + c] = Pieces.plain(rows[r][c])
        state.colors = intArrayOf(0, 1, 2, 3, 4)
        state.rngColumns = LongArray(4) { 17L + it }
        return state
    }

    private fun build(state: GameState, move: Move) =
        GameEngine.applyMove(state, move).let { AnimationTimeline.build(state, it, 1f, words, { "+$it" }) to it }

    @Test
    fun acceptedSwapAnimatesSwapThenClearThenParallelFall() {
        val pre = board()
        val (timeline, result) = build(pre, Move.Swap(Cell(1, 2), Cell(2, 2)))
        val first = timeline.phases.first()
        assertTrue(first is AnimationTimeline.SwapPhase && !first.rejected)
        assertTrue(timeline.phases.any { it is AnimationTimeline.ClearPhase })
        val fall = timeline.phases.first { it is AnimationTimeline.FallPhase } as AnimationTimeline.FallPhase
        // Three cells emptied on the bottom row: everything above drops in the same phase.
        assertTrue(fall.movers.size >= 3)
        assertTrue(fall.end - fall.start < 600)
        assertTrue(timeline.final.sameAs(BoardSnapshot.of(result.state)))
        assertTrue(timeline.phases.last().board.sameAs(timeline.final))
    }

    @Test
    fun phasesAreContiguousAndEndOnTheTotal() {
        val (timeline, _) = build(board(), Move.Swap(Cell(1, 2), Cell(2, 2)))
        var clock = 0L
        for (phase in timeline.phases) {
            assertEquals(clock, phase.start)
            clock = phase.end
        }
        assertEquals(timeline.totalMillis, clock)
    }

    @Test
    fun rejectedSwapGoesAndReturnsWithoutChangingTheBoard() {
        val pre = board()
        val (timeline, result) = build(pre, Move.Swap(Cell(0, 0), Cell(0, 1)))
        assertEquals(1, timeline.phases.size)
        val phase = timeline.phases.single() as AnimationTimeline.SwapPhase
        assertTrue(phase.rejected)
        assertTrue(BoardSnapshot.of(result.state).sameAs(BoardSnapshot.of(pre)))
    }

    @Test
    fun fasterSpeedShortensTheTimeline() {
        val pre = board()
        val result = GameEngine.applyMove(pre, Move.Swap(Cell(1, 2), Cell(2, 2)))
        val normal = AnimationTimeline.build(pre, result, 1f, words, { "" })
        val fast = AnimationTimeline.build(pre, result, 2f, words, { "" })
        assertTrue(fast.totalMillis < normal.totalMillis)
    }
}
