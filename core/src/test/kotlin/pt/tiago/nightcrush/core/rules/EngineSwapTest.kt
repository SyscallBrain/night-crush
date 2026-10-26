package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.ComboKind
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.SpecialKind
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.pieceAt
import pt.tiago.nightcrush.core.testutil.swap

class EngineSwapTest {

    // Stable 3x3 board used by several tests: swapping (0,1) with (1,1) makes row 0 a run.
    private fun stable() = boardOf(
        "BGB",
        "GBY",
        "YYG",
    )

    @Test
    fun `a swap that creates a match clears the group and spends a move`() {
        val s = stable()
        val result = s.swap(0, 1, 1, 1)
        assertEquals(98, result.state.movesLeft)
        assertEquals(1, result.state.movesUsed)

        val matched = result.events.filterIsInstance<GameEvent.Matched>().single()
        assertEquals(MatchKind.THREE, matched.kind)
        assertEquals(listOf(0, 1, 2), matched.cells.map { it.r * 3 + it.c })
        assertEquals(3, result.events.filterIsInstance<GameEvent.PieceCleared>().size)
    }

    @Test
    fun `an invalid swap is rejected, restores the board and spends nothing`() {
        val s = stable()
        val result = s.swap(0, 0, 0, 1)
        assertTrue(result.events.any { it is GameEvent.SwapRejected })
        assertEquals(99, result.state.movesLeft)
        assertEquals(Pieces.plain(0), result.state.pieceAt(0, 0))
        assertEquals(Pieces.plain(4), result.state.pieceAt(0, 1))
        assertTrue(result.events.none { it is GameEvent.PieceCleared })
    }

    @Test
    fun `a barnacled piece cannot be swapped or fall`() {
        val s = boardOf("BYG", "YGB", "GBY") { barnacle(0, 1, 2) }
        val result = s.swap(0, 0, 0, 1)
        assertTrue(result.events.any { it is GameEvent.SwapRejected })
    }

    @Test
    fun `a netted piece cannot be swapped`() {
        val s = boardOf("BYG", "YGB", "GBY") { net(0, 1) }
        val result = s.swap(0, 0, 0, 1)
        assertTrue(result.events.any { it is GameEvent.SwapRejected })
    }

    @Test
    fun `a blocked cell cannot be swapped`() {
        val s = boardOf("BYG", "YGB", "GBY") { coral(0, 1, 1) }
        val result = s.swap(0, 0, 0, 1)
        assertTrue(result.events.any { it is GameEvent.SwapRejected })
    }

    @Test
    fun `two lanterns never swap`() {
        val s = boardOf("BYG", "YGB", "GBY") {
            lantern(0, 0)
            lantern(0, 1)
        }
        val result = s.swap(0, 0, 0, 1)
        assertTrue(result.events.any { it is GameEvent.SwapRejected })
    }

    @Test
    fun `a lantern can be swapped into a match like any piece`() {
        val s = boardOf(
            "BGB",
            "GBY",
            "YYG",
        ) { lantern(0, 1) }
        // Row 0 has B _ B; swapping the lantern down brings a B into the middle.
        val result = s.swap(0, 1, 1, 1)
        assertTrue(result.events.any { it is GameEvent.Matched })
        assertEquals(98, result.state.movesLeft)
    }

    @Test
    fun `swapping the pearl clears every piece of the other colour`() {
        val s = boardOf(
            "BYG",
            "*GB",
            "GYB",
        )
        val result = s.swap(1, 0, 1, 1) // pearl with the green piece
        val combos = result.events.filterIsInstance<GameEvent.ComboActivated>()
        assertEquals(ComboKind.PEARL_PLAIN, combos.single().combo)
        val clearedGreen = result.events.filterIsInstance<GameEvent.PieceCleared>().count { it.color == 4 }
        assertEquals(3, clearedGreen)
        assertTrue(result.state.pieces.none { it == Pieces.PEARL })
        assertEquals(98, result.state.movesLeft)
    }

    @Test
    fun `swapping a lantern fish with a plain piece sets the fish flying`() {
        val s = boardOf("BYG", "YGB", "GBY") { fish(0, 0, 0) }
        val result = s.swap(0, 0, 1, 0)
        val activated = result.events.filterIsInstance<GameEvent.SpecialActivated>()
        assertEquals(SpecialKind.LANTERN_FISH, activated.single().kind)
        val cleared = result.events.filterIsInstance<GameEvent.PieceCleared>().map { it.cell.r * 3 + it.cell.c }.toSet()
        assertEquals(setOf(0, 3, 4, 6), cleared) // own cell, up, down, right
    }

    @Test
    fun `two rays form a cross and do not fire twice`() {
        val s = boardOf("BYG", "YGB", "GBY") {
            rayH(1, 0, 0)
            rayV(1, 1, 0)
        }
        val result = s.swap(1, 0, 1, 1)
        val combos = result.events.filterIsInstance<GameEvent.ComboActivated>()
        assertEquals(ComboKind.RAY_RAY, combos.single().combo)
        val cleared = result.events.filterIsInstance<GameEvent.PieceCleared>().map { it.cell.r * 3 + it.cell.c }.toSet()
        assertEquals(setOf(3, 4, 5, 1, 7), cleared)
        // No stray activations: the combo already used both rays.
        assertTrue(result.events.none { it is GameEvent.SpecialActivated })
    }

    @Test
    fun `a ray combo chains into a ray standing in the cross`() {
        val s = boardOf(
            "BYGBY",
            "YGBYG",
            "GBYGB",
            "BYGBY",
            "YGBYG",
        ) {
            rayH(2, 0, 0)
            rayV(2, 1, 0)
            rayV(2, 3, 1)
        }
        val result = s.swap(2, 0, 2, 1)
        assertEquals(ComboKind.RAY_RAY, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        // The third ray stands in the cleared row and fires its own column once.
        val activated = result.events.filterIsInstance<GameEvent.SpecialActivated>()
        assertEquals(1, activated.size)
        assertEquals(SpecialKind.RAY, activated[0].kind)
    }
}
