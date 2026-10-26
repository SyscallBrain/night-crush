package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.SpecialKind
import pt.tiago.nightcrush.core.testutil.blockerAt
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.isLit
import pt.tiago.nightcrush.core.testutil.overlayAt
import pt.tiago.nightcrush.core.testutil.pieceAt
import pt.tiago.nightcrush.core.testutil.siltAt
import pt.tiago.nightcrush.core.testutil.swap

class SpecialsTest {

    private fun stable() = boardOf(
        "GBG",
        "YGB",
        "GBY",
    )

    @Test
    fun `a ray matched vertically fires along its row`() {
        val s = boardOf("GBG", "YGB", "YBY") { rayH(1, 0, 0) }
        val result = s.swap(1, 0, 1, 1)
        val cleared = result.events.filterIsInstance<GameEvent.PieceCleared>()
            .map { it.cell.r * 3 + it.cell.c }.toSet()
        assertEquals(setOf(1, 4, 7, 3, 5), cleared)
        assertEquals(SpecialKind.RAY, result.events.filterIsInstance<GameEvent.SpecialActivated>().single().kind)
    }

    @Test
    fun `a ray strips one coral layer on its path`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            rayH(1, 0, 0)
            coral(1, 2, 2)
        }
        val result = s.swap(1, 0, 1, 1)
        // The group damages one layer, then the ray fires through the rest.
        assertEquals(Blockers.NONE, result.state.blockerAt(1, 2))
        val remainings = result.events.filterIsInstance<GameEvent.LayerDamaged>().map { it.remaining }
        assertEquals(listOf(1, 0), remainings)
    }

    @Test
    fun `a jelly explodes twice and is consumed`() {
        val s = boardOf("GBG", "YGB", "YBY") { jelly(1, 0, 0) }
        val result = s.swap(1, 0, 1, 1)
        val activations = result.events.filterIsInstance<GameEvent.SpecialActivated>()
        assertEquals(2, activations.count { it.kind == SpecialKind.JELLY })
        assertTrue(result.state.pieces.none { Pieces.isJelly(it) })
        assertTrue(result.state.pieces.all { it == Pieces.EMPTY })
    }

    @Test
    fun `a fish clears its neighbours and flies to a silt target`() {
        val s = boardOf(
            "BYGB",
            "YGBY",
            "GBYG",
            "BYGB",
        ) {
            fish(0, 0, 0)
            silt(3, 0, 1)
            silt(3, 3, 1)
            objective(Objective.ClearSilt(2))
            seed(7)
        }
        val result = s.swap(0, 0, 1, 0)
        assertEquals(SpecialKind.LANTERN_FISH, result.events.filterIsInstance<GameEvent.SpecialActivated>().single().kind)
        val cleared = result.events.filterIsInstance<GameEvent.PieceCleared>().map { it.cell.r * 4 + it.cell.c }.toSet()
        assertTrue(cleared.containsAll(setOf(0, 8, 5))) // up, down, right of the fish's landing cell
        val remaining = listOf(result.state.siltAt(3, 0), result.state.siltAt(3, 3))
        assertEquals(1, remaining.count { it == 0 })
        assertEquals(1, remaining.count { it == 1 })
        assertEquals(1, result.events.filterIsInstance<GameEvent.SiltCleared>().size)
    }

    @Test
    fun `a pearl hit by another special clears the most frequent colour`() {
        val s = boardOf("GBG", "YGB", "YBB") {
            rayH(1, 0, 0)
            pearl(1, 2)
        }
        val result = s.swap(1, 0, 1, 1)
        // Green is the most frequent colour; the ray already cleared one green itself.
        val green = result.events.filterIsInstance<GameEvent.PieceCleared>().count { it.color == 4 }
        assertEquals(3, green)
        assertTrue(result.state.pieces.none { it == Pieces.PEARL })
    }

    @Test
    fun `an adjacent match damages coral and the oil and pops a bubble`() {
        val s = boardOf("GBG", "YGB", "YBB") {
            rayH(1, 0, 0)
            coral(1, 2, 2)
            oil(0, 0)
            bubble(2, 0)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(Blockers.NONE, result.state.blockerAt(0, 0))
        assertEquals(Blockers.NONE, result.state.blockerAt(2, 0))
        // The group damages one layer and then the ray fires through the rest.
        assertEquals(Blockers.NONE, result.state.blockerAt(1, 2))
        assertEquals(2, result.events.filterIsInstance<GameEvent.LayerDamaged>().count { it.target == DamageTarget.CORAL })
        assertEquals(1, result.state.seahorsesFreed)
    }

    @Test
    fun `clearing a piece removes one silt layer`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            silt(1, 1, 2)
            objective(Objective.ClearSilt(3))
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(1, result.state.siltAt(1, 1))
        assertEquals(1, result.events.filterIsInstance<GameEvent.SiltCleared>().size)
        assertEquals(1, result.state.progress[0][0])
    }

    @Test
    fun `a lit cell lights its whole group`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            glow(1, 1)
            plain(1, 0, 0)
        }
        val result = s.swap(1, 0, 1, 1)
        assertTrue(result.state.isLit(0, 1))
        assertTrue(result.state.isLit(1, 1))
        assertTrue(result.state.isLit(2, 1))
        assertFalse(result.state.isLit(0, 0))
    }

    @Test
    fun `a ray fired from a lit cell lights its line`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            rayH(1, 0, 0)
            glow(1, 1)
        }
        val result = s.swap(1, 0, 1, 1)
        assertTrue(result.state.isLit(1, 0))
        assertTrue(result.state.isLit(1, 1))
        assertTrue(result.state.isLit(1, 2))
    }

    @Test
    fun `a barnacled piece keeps its place when its own match fires`() {
        val s = boardOf(
            "BYG",
            "GGB",
            "YGB",
        ) { barnacle(1, 1, 2) }
        val result = s.swap(1, 2, 0, 2)
        assertEquals(Pieces.plain(4), result.state.pieceAt(1, 1))
        assertEquals(1, result.state.overlayAt(1, 1))
        assertEquals(2, result.events.filterIsInstance<GameEvent.PieceCleared>().size)
    }

    @Test
    fun `a netted piece breaks its net when it matches`() {
        val s = boardOf(
            "BYG",
            "GGB",
            "YGB",
        ) { net(1, 1) }
        val result = s.swap(1, 2, 0, 2)
        assertEquals(0, result.state.overlayAt(1, 1))
        assertTrue(result.events.any { it is GameEvent.LayerDamaged && it.target == DamageTarget.NET })
    }

    @Test
    fun `an adjacent match leaves a netted piece untouched`() {
        val s = boardOf(
            "BYG",
            "GGB",
            "YGB",
        ) { net(0, 1) }
        val result = s.swap(1, 2, 0, 2)
        assertEquals(4, result.state.overlayAt(0, 1))
    }

    @Test
    fun `a match of four creates a ray that fires along the cross direction`() {
        val s = boardOf(
            "BGBB",
            "YBGY",
            "GYBG",
        )
        // Row 0 has B _ B B: dropping a B into the gap makes four in a row.
        val result = s.swap(1, 1, 0, 1)
        val created = result.events.filterIsInstance<GameEvent.SpecialCreated>()
        assertEquals(1, created.size)
        assertEquals(SpecialKind.RAY, created.single().kind)
        assertEquals(Pieces.rayAlongColumn(0), result.state.pieceAt(0, 1))
    }

    @Test
    fun `five in a line creates an abyss pearl`() {
        val s = boardOf(
            "BBGBB",
            "YGBYG",
            "GYYGY",
            "BGYBG",
        )
        // Row 0 has B B _ B B: dropping the B below into the gap makes five.
        val result = s.swap(1, 2, 0, 2)
        val created = result.events.filterIsInstance<GameEvent.SpecialCreated>()
        assertEquals(SpecialKind.ABYSS_PEARL, created.single().kind)
        assertEquals(Pieces.PEARL, result.state.pieceAt(0, 2))
    }
}
