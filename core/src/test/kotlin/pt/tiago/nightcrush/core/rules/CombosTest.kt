package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.ComboKind
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.SpecialKind
import pt.tiago.nightcrush.core.testutil.blockerAt
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.play
import pt.tiago.nightcrush.core.testutil.swap

class CombosTest {

    private fun cyclic(edit: pt.tiago.nightcrush.core.testutil.BoardEdit.() -> Unit = {}) = boardOf(
        "BYGBY",
        "YGBYG",
        "GBYGB",
        "BYGBY",
        "YGBYG",
        edit = edit,
    )

    @Test
    fun `ray plus jelly clears three rows and three columns`() {
        val s = cyclic {
            rayH(2, 1, 0)
            jelly(2, 2, 0)
        }
        val result = s.swap(2, 1, 2, 2)
        val combos = result.events.filterIsInstance<GameEvent.ComboActivated>()
        assertEquals(ComboKind.RAY_JELLY, combos.single().combo)
        // 3 rows + 3 columns over a 5x5 board: 15 + 15 - 9 cells.
        assertEquals(21, result.events.filterIsInstance<GameEvent.PieceCleared>().size)
    }

    @Test
    fun `jelly plus jelly explodes a 5x5 area twice`() {
        val s = cyclic {
            jelly(2, 2, 0)
            jelly(2, 3, 0)
        }
        val result = s.swap(2, 2, 2, 3)
        assertEquals(ComboKind.JELLY_JELLY, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        // The combo itself is the first 5x5 phase; each jellyfish then explodes again
        // once the board has settled.
        assertEquals(2, result.events.filterIsInstance<GameEvent.SpecialActivated>().count { it.kind == SpecialKind.JELLY })
        assertTrue(result.state.pieces.none { Pieces.isJelly(it) })
    }

    @Test
    fun `fish plus fish sends three fish to three blockers`() {
        val s = cyclic {
            fish(2, 2, 0)
            fish(2, 3, 0)
            oil(0, 0)
            oil(0, 4)
            oil(4, 0)
        }
        val result = s.swap(2, 2, 2, 3)
        assertEquals(ComboKind.FISH_FISH, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        assertEquals(3, result.events.filterIsInstance<GameEvent.LayerDamaged>().count { it.target == DamageTarget.OIL })
    }

    @Test
    fun `fish plus ray sends the ray to a blocker`() {
        val s = cyclic {
            fish(2, 2, 0)
            rayV(2, 3, 1)
            oil(0, 0)
        }
        val result = s.play(Move.Swap(Cell(2, 2), Cell(2, 3)))
        assertEquals(ComboKind.FISH_RAY, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        assertEquals(1, result.events.filterIsInstance<GameEvent.LayerDamaged>().count { it.target == DamageTarget.OIL })
    }

    @Test
    fun `pearl plus ray turns every piece of that colour into rays`() {
        val s = cyclic {
            pearl(2, 2)
            rayH(2, 3, 0)
        }
        val result = s.swap(2, 2, 2, 3)
        assertEquals(ComboKind.PEARL_RAY, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        val converted = result.events.filterIsInstance<GameEvent.SpecialActivated>().count { it.kind == SpecialKind.RAY }
        assertTrue("at least the swapped ray and its colour-mates", converted >= 5)
        assertTrue(result.state.pieces.none { Pieces.colorOf(it) == 0 })
    }

    @Test
    fun `pearl plus jelly turns every piece of that colour into jellyfish`() {
        val s = cyclic {
            pearl(2, 2)
            jelly(2, 3, 0)
        }
        val result = s.swap(2, 2, 2, 3)
        assertEquals(ComboKind.PEARL_JELLY, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        val activations = result.events.filterIsInstance<GameEvent.SpecialActivated>().count { it.kind == SpecialKind.JELLY }
        // Each converted jellyfish explodes twice.
        assertTrue("two phases per jellyfish, got $activations", activations >= 6 && activations % 2 == 0)
        // Armed jellyfish that fall into a line must not loop until the cascade guard.
        assertTrue("cascades ${result.state.maxCascade}", result.state.maxCascade < 20)
    }

    @Test
    fun `pearl plus fish turns every piece of that colour into fish`() {
        val s = cyclic {
            pearl(2, 2)
            fish(2, 3, 0)
        }
        val result = s.swap(2, 2, 2, 3)
        assertEquals(ComboKind.PEARL_FISH, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        assertTrue(
            result.events.filterIsInstance<GameEvent.SpecialActivated>()
                .count { it.kind == SpecialKind.LANTERN_FISH } >= 5,
        )
    }

    @Test
    fun `pearl plus pearl clears the board and strips one layer of every blocker`() {
        val s = cyclic {
            pearl(2, 2)
            pearl(2, 3)
            coral(0, 0, 2)
        }
        val result = s.swap(2, 2, 2, 3)
        assertEquals(ComboKind.PEARL_PEARL, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        assertTrue(result.state.pieces.all { it == Pieces.EMPTY })
        assertEquals(1, result.state.blockerAt(0, 0))
    }

    @Test
    fun `fish plus jelly explodes at the target`() {
        val s = cyclic {
            fish(2, 2, 0)
            jelly(2, 3, 0)
            oil(0, 0)
        }
        val result = s.swap(2, 2, 2, 3)
        assertEquals(ComboKind.FISH_JELLY, result.events.filterIsInstance<GameEvent.ComboActivated>().single().combo)
        assertTrue(result.events.filterIsInstance<GameEvent.PieceCleared>().isNotEmpty())
    }
}
