package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.play
import pt.tiago.nightcrush.core.testutil.swap

/** Moves that clear cells without a match must still leave a full board. */
class NoHolesTest {

    private fun cyclic(edit: pt.tiago.nightcrush.core.testutil.BoardEdit.() -> Unit = {}) = boardOf(
        "BYGBY",
        "YGBYG",
        "GBYGB",
        "BYGBY",
        "YGBYG",
        edit = { refillAll(); edit() },
    )

    private fun assertFull(s: GameState) {
        val holes = (0 until s.size).filter { s.playable[it] && s.pieces[it] == Pieces.EMPTY }
        assertTrue("holes left at $holes", holes.isEmpty())
    }

    @Test
    fun `harpoon on a stable board refills the cleared cell`() {
        assertFull(cyclic().play(Move.Harpoon(Cell(2, 2))).state)
    }

    @Test
    fun `ray plus ray swap refills the cleared cross`() {
        val s = cyclic {
            rayH(2, 1, 0)
            rayV(2, 2, 0)
        }
        assertFull(s.swap(2, 1, 2, 2).state)
    }

    @Test
    fun `free swap of two specials refills the board`() {
        val s = cyclic {
            rayH(2, 1, 0)
            rayV(2, 2, 0)
        }
        assertFull(s.play(Move.FreeSwap(Cell(2, 1), Cell(2, 2))).state)
    }
}
