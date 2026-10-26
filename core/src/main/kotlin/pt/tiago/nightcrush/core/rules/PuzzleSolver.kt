package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Outcome

/**
 * Exhaustive depth-first search for puzzle boards. Puzzles never refill, so
 * the tree is small: a few dozen swaps per level for at most four levels.
 */
object PuzzleSolver {

    /** A winning sequence of at most [depth] swaps from [state], or null. */
    fun solve(state: GameState, depth: Int): List<Move.Swap>? {
        if (state.outcome is Outcome.Won) return emptyList()
        if (depth <= 0 || state.outcome != Outcome.Playing) return null
        for (swap in GameEngine.validSwaps(state)) {
            val next = GameEngine.applyMove(state, swap, recordEvents = false).state
            when (next.outcome) {
                is Outcome.Won -> return listOf(swap)
                Outcome.Playing -> solve(next, depth - 1)?.let { return listOf(swap) + it }
                else -> Unit
            }
        }
        return null
    }
}
