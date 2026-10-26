package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.pieceAt
import pt.tiago.nightcrush.core.testutil.play
import pt.tiago.nightcrush.core.testutil.settle
import pt.tiago.nightcrush.core.testutil.swap

class GravityTest {

    @Test
    fun `pieces fall straight to the bottom`() {
        val s = boardOf(
            "B",
            "Y",
            ".",
        ) { noRefill() }
        val settled = s.settle()
        assertEquals(Pieces.plain(0), settled.pieceAt(1, 0))
        assertEquals(Pieces.plain(1), settled.pieceAt(2, 0))
    }

    @Test
    fun `falling pieces never cross columns`() {
        val s = boardOf(
            "BY",
            "YG",
            "..",
            "..",
        ) { noRefill() }
        val settled = s.settle()
        assertEquals(Pieces.plain(0), settled.pieceAt(2, 0))
        assertEquals(Pieces.plain(1), settled.pieceAt(3, 0))
        assertEquals(Pieces.plain(1), settled.pieceAt(2, 1))
        assertEquals(Pieces.plain(4), settled.pieceAt(3, 1))
    }

    @Test
    fun `a piece slides diagonally into a hole that nothing can fall into`() {
        val s = boardOf(
            " ..",
            "..G",
            "..Y",
        ) {
            coral(1, 1, 1)
            noRefill()
        }
        val settled = s.settle()
        // The green piece at (1,2) slides down-left into the pocket below the coral.
        assertEquals(Pieces.plain(4), settled.pieceAt(2, 1))
        assertEquals(Pieces.EMPTY, settled.pieceAt(1, 2))
        assertEquals(Pieces.plain(1), settled.pieceAt(2, 2))
    }

    @Test
    fun `a column fed from above is never borrowed diagonally`() {
        val s = boardOf(
            "..G",
            "...",
            "...",
        ) {
            refillAll()
            seed(5)
        }
        val copy = s.copyState()
        val events = ArrayList<GameEvent>()
        Gravity.settle(Resolution(copy, events))
        assertTrue("no diagonal slide expected", events.none { it is GameEvent.Slid })
        assertTrue(events.any { it is GameEvent.Fell })
    }

    @Test
    fun `a whirlpool carries a falling piece to its exit`() {
        val s = boardOf(
            "B..",
            "...",
            "...",
        ) {
            portal(2, 0, 2, 2)
            noRefill()
        }
        val settled = s.settle()
        assertEquals(Pieces.EMPTY, settled.pieceAt(2, 0))
        assertEquals(Pieces.plain(0), settled.pieceAt(2, 2))
    }

    @Test
    fun `refill spawns from the column stream and is deterministic`() {
        val a = boardOf("...", "...", "...") {
            refillAll()
            seed(42)
        }.settle()
        val b = boardOf("...", "...", "...") {
            refillAll()
            seed(42)
        }.settle()
        assertTrue(a.pieces.none { it == Pieces.EMPTY })
        assertEquals(a.pieces.toList(), b.pieces.toList())
        assertEquals(a.rngColumns.toList(), b.rngColumns.toList())
    }

    @Test
    fun `a different seed gives a different refill`() {
        val a = boardOf("...", "...", "...") { refillAll(); seed(1) }.settle()
        val b = boardOf("...", "...", "...") { refillAll(); seed(2) }.settle()
        assertNotEquals(a.pieces.toList(), b.pieces.toList())
    }

    @Test
    fun `lanterns spawn on their cadence when the rules allow it`() {
        val s = boardOf("...", "...", "...") {
            spawner(0, lantern = true)
            lanternRules(total = 2, maxOnBoard = 2, everyMoves = 2)
            usedMoves(2)
            seed(9)
        }
        val settled = s.settle()
        assertEquals(1, settled.lanternsSpawned)
        assertTrue(settled.pieces.any { it == Pieces.LANTERN })
    }

    @Test
    fun `no lantern spawns before its cadence`() {
        val s = boardOf("...", "...", "...") {
            spawner(0, lantern = true)
            lanternRules(total = 2, maxOnBoard = 2, everyMoves = 2)
            usedMoves(1)
            seed(9)
        }
        val settled = s.settle()
        assertEquals(0, settled.lanternsSpawned)
        assertTrue(settled.pieces.none { it == Pieces.LANTERN })
    }

    @Test
    fun `a lantern that missed its move still spawns on the next refill`() {
        val s = boardOf("...", "...", "...") {
            spawner(0, lantern = true)
            lanternRules(total = 2, maxOnBoard = 2, everyMoves = 2)
            usedMoves(3)
            seed(9)
        }
        val settled = s.settle()
        assertEquals(1, settled.lanternsSpawned)
    }

    @Test
    fun `a lantern that reaches its exit leaves the board`() {
        val s = boardOf(
            "L",
            ".",
        ) {
            exit(1, 0)
            noRefill()
        }
        val settled = s.settle()
        assertEquals(Pieces.EMPTY, settled.pieceAt(1, 0))
        assertEquals(1, settled.lanternsDelivered)
    }

    @Test
    fun `a closed conveyor rotates its contents every move`() {
        val s = boardOf(
            "BYGBY",
            "YGBYG",
            "GBYGB",
            "BYGBY",
            "YGBYG",
        ) {
            conveyorLoop(4 to 0, 4 to 1, 4 to 2, 4 to 3, 4 to 4)
        }
        // Harpooning is a free action; the belt rotates row 4 one step to the right.
        val result = s.play(Move.Harpoon(pt.tiago.nightcrush.core.model.Cell(0, 0)))
        assertEquals(Pieces.plain(4), result.state.pieceAt(4, 0))
        assertEquals(Pieces.plain(1), result.state.pieceAt(4, 1))
        assertEquals(Pieces.plain(1), result.state.pieceAt(4, 4))
        assertTrue(result.events.any { it is GameEvent.ConveyorMoved })
    }

    @Test
    fun `a conveyor that assembles a match resolves it after the belt moves`() {
        val s = boardOf(
            "YGBY",
            "GYGG",
            "BBYB",
        ) {
            conveyorOpen(2 to 0, 2 to 1, 2 to 2, 2 to 3)
        }
        // Harpooning (0,0) is a free action; the belt then shifts the last B into
        // the row and completes a three of blues.
        val result = s.play(Move.Harpoon(pt.tiago.nightcrush.core.model.Cell(0, 0)))
        val events = result.events
        val beltIndex = events.indexOfFirst { it is GameEvent.ConveyorMoved }
        val matchIndex = events.indexOfFirst { it is GameEvent.Matched }
        assertTrue("belt should move", beltIndex >= 0)
        assertTrue("match should resolve after the belt", matchIndex > beltIndex)
    }
}
