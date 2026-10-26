package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.serialization.StateHasher
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.settle

class DeterminismTest {

    private fun fullBoard(seed: Long) = boardOf(
        "......",
        "......",
        "......",
        "......",
        "......",
        "......",
    ) {
        refillAll()
        colors(0, 1, 2, 3, 4)
        seed(seed)
    }.settle()

    @Test
    fun `identical seeds and identical moves keep identical hashes`() {
        var a = fullBoard(1234)
        var b = fullBoard(1234)
        assertEquals(StateHasher.hash(a), StateHasher.hash(b))
        var applied = 0
        repeat(60) { step ->
            val swaps = GameEngine.validSwaps(a)
            if (swaps.isEmpty()) return@repeat
            val move = swaps[(step * 7) % swaps.size]
            a = GameEngine.applyMove(a, move, recordEvents = false).state
            b = GameEngine.applyMove(b, move, recordEvents = false).state
            applied++
            assertEquals("hash diverged at step $step", StateHasher.hash(a), StateHasher.hash(b))
        }
        assertTrue(applied > 30)
    }

    @Test
    fun `different seeds give different boards`() {
        assertNotEquals(StateHasher.hash(fullBoard(1)), StateHasher.hash(fullBoard(2)))
    }

    @Test
    fun `the canonical form is stable for the same state`() {
        val a = fullBoard(77)
        val copy = a.copyState()
        assertEquals(StateHasher.canonical(a), StateHasher.canonical(copy))
    }

    @Test
    fun `quiet resolution never consumes the platform random`() {
        // Two boards built and settled twice must land on the same hash: any use of
        // a platform RNG would diverge between runs.
        val first = fullBoard(999)
        val second = fullBoard(999)
        assertEquals(StateHasher.hash(first), StateHasher.hash(second))
        assertEquals(first.rngAux, second.rngAux)
        assertEquals(first.rngColumns.toList(), second.rngColumns.toList())
    }
}
