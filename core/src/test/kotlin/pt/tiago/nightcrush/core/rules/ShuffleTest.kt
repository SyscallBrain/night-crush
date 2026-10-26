package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.play
import pt.tiago.nightcrush.core.testutil.render

class ShuffleTest {

    @Test
    fun `an explicit shuffle guarantees a move`() {
        val s = boardOf("BBY", "BYY", "GGB") { seed(11) }
        assertFalse(GameEngine.hasValidMove(s))
        val result = s.play(Move.Shuffle)
        assertTrue(result.events.any { it is GameEvent.Shuffled })
        assertTrue(GameEngine.hasValidMove(result.state))
        assertTrue(MatchFinder.find(result.state).isEmpty())
    }

    @Test
    fun `shuffle is deterministic for one seed`() {
        val a = boardOf("BBY", "BYY", "GGB") { seed(11) }.play(Move.Shuffle)
        val b = boardOf("BBY", "BYY", "GGB") { seed(11) }.play(Move.Shuffle)
        assertTrue(a.state.render() == b.state.render())
        assertTrue(a.state.rngAux == b.state.rngAux)
    }

    @Test
    fun `the shuffle keeps specials and never creates a match`() {
        val s = boardOf("BBY", "BYY", "GGB") {
            seed(23)
            fish(1, 1, 0)
        }
        val result = s.play(Move.Shuffle)
        assertTrue(result.events.any { it is GameEvent.Shuffled })
        assertTrue(result.state.pieces.any { pt.tiago.nightcrush.core.model.Pieces.isLanternFish(it) })
        assertTrue(MatchFinder.find(result.state).isEmpty())
        assertTrue(GameEngine.hasValidMove(result.state))
    }
}

class PuzzleRulesTest {

    @Test
    fun `a stuck puzzle board loses instead of shuffling`() {
        val s = pt.tiago.nightcrush.core.model.GameState(width = 3, height = 3, stuckLoses = true)
        val rows = listOf("BBY", "GTB", "YGT")
        for (r in 0 until 3) for (c in 0 until 3) {
            s.pieces[s.idx(r, c)] = pt.tiago.nightcrush.core.model.Pieces.plain("BYGTPC".indexOf(rows[r][c]))
        }
        java.util.Arrays.fill(s.spawner, false)
        s.movesLeft = 5
        s.limitValue = 5
        s.objectives = listOf(pt.tiago.nightcrush.core.model.Objective.ClearSilt(9))
        s.progress = arrayOf(IntArray(1))
        // Swapping (0,2) with (1,2) clears the blue top row; the six pieces left (two of each colour) can never line up.
        val result = GameEngine.applyMove(s, Move.Swap(pt.tiago.nightcrush.core.model.Cell(0, 2), pt.tiago.nightcrush.core.model.Cell(1, 2)))
        assertTrue(result.events.none { it is GameEvent.Shuffled })
        assertTrue(result.state.outcome == pt.tiago.nightcrush.core.model.Outcome.Lost.OutOfMoves)
    }

    @Test
    fun `the puzzle solver finds the shortest win`() {
        val s = pt.tiago.nightcrush.core.model.GameState(width = 3, height = 3, stuckLoses = true)
        val rows = listOf("BBY", "GTB", "YGT")
        for (r in 0 until 3) for (c in 0 until 3) {
            s.pieces[s.idx(r, c)] = pt.tiago.nightcrush.core.model.Pieces.plain("BYGTPC".indexOf(rows[r][c]))
        }
        s.silt[s.idx(0, 0)] = 1
        java.util.Arrays.fill(s.spawner, false)
        s.movesLeft = 3
        s.limitValue = 3
        s.objectives = listOf(pt.tiago.nightcrush.core.model.Objective.ClearSilt(1))
        s.progress = arrayOf(IntArray(1))
        val solution = PuzzleSolver.solve(s, 3)
        assertTrue(solution != null && solution.size == 1)
    }
}
