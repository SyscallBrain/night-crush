package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.testutil.blockerAt
import pt.tiago.nightcrush.core.testutil.boardOf
import pt.tiago.nightcrush.core.testutil.overlayAt
import pt.tiago.nightcrush.core.testutil.pieceAt
import pt.tiago.nightcrush.core.testutil.play
import pt.tiago.nightcrush.core.testutil.swap

class BlockersTest {

    private fun cyclic(edit: pt.tiago.nightcrush.core.testutil.BoardEdit.() -> Unit = {}) = boardOf(
        "BYGBY",
        "YGBYG",
        "GBYGB",
        "BYGBY",
        "YGBYG",
        edit = edit,
    )

    /** Plays the first available swap, keeping boards deterministic. */
    private fun firstSwap(state: pt.tiago.nightcrush.core.model.GameState): MoveResult =
        GameEngine.applyMove(state, GameEngine.validSwaps(state).first())

    @Test
    fun `a puffer ticks down once per move`() {
        val s = boardOf("BGB", "GBY", "YYG") { puffer(2, 0, 1, 3) }
        val result = s.swap(0, 1, 1, 1)
        assertEquals(2, result.state.pieceData[result.state.idx(2, 0)])
        assertTrue(result.events.any { it is GameEvent.PufferTick })
        assertEquals(Outcome.Playing, result.state.outcome)
    }

    @Test
    fun `a puffer that reaches zero explodes and loses the level`() {
        val s = boardOf("BGB", "GBY", "YYG") { puffer(2, 0, 1, 1) }
        val result = s.swap(0, 1, 1, 1)
        assertEquals(Outcome.Lost.PufferExploded, result.state.outcome)
    }

    @Test
    fun `matching a puffer saves it before the countdown`() {
        val s = boardOf("BYG", "GGB", "YGB") { puffer(1, 1, 4, 1) }
        val result = s.swap(1, 2, 0, 2)
        assertTrue(result.state.pieces.none { Pieces.isPuffer(it) })
        assertEquals(Outcome.Playing, result.state.outcome)
    }

    @Test
    fun `black tide grows into a neighbour when nothing destroyed it`() {
        val s = boardOf("BGB", "GBY", "YYY") { oil(2, 2) }
        val result = s.swap(0, 1, 1, 1)
        val spreads = result.events.filterIsInstance<GameEvent.OilSpread>()
        assertEquals(1, spreads.size)
        val target = spreads.single().to
        val index = result.state.idx(target.r, target.c)
        assertEquals(Blockers.OIL, result.state.blockers[index])
        assertEquals(Pieces.EMPTY, result.state.pieces[index])
    }

    @Test
    fun `black tide does not grow on the move it was destroyed`() {
        val s = boardOf("BGB", "GBY", "YYG") {
            oil(1, 0)
            oil(2, 2)
        }
        // The row-0 match is adjacent to the oil at (1,0), which is destroyed;
        // the second slick must not grow in the same move.
        val result = s.swap(0, 1, 1, 1)
        assertEquals(Blockers.NONE, result.state.blockerAt(1, 0))
        assertTrue(result.events.none { it is GameEvent.OilSpread })
    }

    @Test
    fun `a sinkhole swallows a neighbouring piece every two moves`() {
        val s = cyclic {
            refillAll()
            seed(5)
            sinkhole(1, 1, 3)
        }
        // The cyclic pattern has no plain moves of its own; shuffle first.
        val playable = GameEngine.applyMove(s, Move.Shuffle).state
        val first = firstSwap(playable)
        assertTrue(first.events.none { it is GameEvent.SinkholeSwallow })
        val second = firstSwap(first.state)
        assertEquals(1, second.events.filterIsInstance<GameEvent.SinkholeSwallow>().size)
    }

    @Test
    fun `a sinkhole takes three hits and then closes`() {
        val s = cyclic { sinkhole(1, 1, 3) }
        val once = s.play(Move.Harpoon(Cell(1, 1)))
        assertEquals(2, once.state.blockerData[once.state.idx(1, 1)])
        val twice = once.state.play(Move.Harpoon(Cell(1, 1)))
        assertEquals(1, twice.state.blockerData[twice.state.idx(1, 1)])
        val thrice = twice.state.play(Move.Harpoon(Cell(1, 1)))
        assertEquals(Blockers.NONE, thrice.state.blockerAt(1, 1))
        assertEquals(Blockers.NONE, thrice.state.blockerAt(2, 2))
    }

    @Test
    fun `an octopus inks two pieces every three moves`() {
        val s = cyclic {
            refillAll()
            seed(5)
            octopus(2, 2, 5)
        }
        var state = GameEngine.applyMove(s, Move.Shuffle).state
        var ink: GameEvent.OctopusInk? = null
        repeat(3) {
            val result = firstSwap(state)
            state = result.state
            result.events.filterIsInstance<GameEvent.OctopusInk>().firstOrNull()?.let { ink = it }
        }
        val inked = ink ?: error("no ink in three moves")
        assertEquals(2, inked.targets.size)
        for (target in inked.targets) {
            assertEquals(4, state.overlayAt(target.r, target.c))
        }
    }

    @Test
    fun `an adjacent match damages the octopus`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            rayH(1, 0, 0)
            octopus(0, 2, 5)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(4, result.state.blockerData[result.state.idx(0, 2)])
    }

    @Test
    fun `a giant oyster opens after enough adjacent groups of its colour`() {
        val s = boardOf(
            "BGYB",
            "YGYY",
            "BBYY",
            "YGBY",
        ) { oyster(1, 2, 4, 1) }
        // Column 1 becomes G G G when the blue at (2,1) swaps with the green at (3,1),
        // and the group touches the oyster through (1,1) and (2,1).
        val result = s.swap(2, 1, 3, 1)
        assertTrue(result.events.any { it is GameEvent.OysterOpened })
        assertEquals(Blockers.NONE, result.state.blockerAt(1, 2))
        assertEquals(Blockers.NONE, result.state.blockerAt(2, 3))
    }

    @Test
    fun `a special hit still damages the oyster`() {
        val s = boardOf(
            "BGYB",
            "YGYY",
            "BBYY",
            "YGBY",
        ) { oyster(1, 2, 4, 2) }
        val result = s.play(Move.Harpoon(Cell(1, 2)))
        assertEquals(1, result.state.blockerData[result.state.idx(1, 2)])
    }

    @Test
    fun `a seahorse bubble pops from an adjacent match`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            bubble(0, 0)
        }
        val result = s.swap(1, 0, 1, 1)
        assertTrue(result.events.any { it is GameEvent.SeahorseFreed })
        assertEquals(1, result.state.seahorsesFreed)
        assertEquals(Blockers.NONE, result.state.blockerAt(0, 0))
    }

    @Test
    fun `an adjacent match damages a barnacle without touching the piece`() {
        val s = boardOf("GBG", "YGB", "YBY") {
            plain(1, 0, 0)
            barnacle(0, 0, 1)
        }
        val result = s.swap(1, 0, 1, 1)
        assertEquals(0, result.state.overlayAt(0, 0))
        val damage = result.events.filterIsInstance<GameEvent.LayerDamaged>()
            .filter { it.target == DamageTarget.BARNACLE }
        assertEquals(1, damage.size)
        assertEquals(0, damage.single().remaining)
        // Only the three matched pieces left the board.
        assertEquals(3, result.events.filterIsInstance<GameEvent.PieceCleared>().size)
    }

    @Test
    fun `coral at three layers takes three hits`() {
        val s = boardOf("BGB", "GBY", "YYG") { coral(2, 1, 3) }
        val twice = s.play(Move.Harpoon(Cell(2, 1))).state.play(Move.Harpoon(Cell(2, 1)))
        assertEquals(1, twice.state.blockerAt(2, 1))
        val thrice = twice.state.play(Move.Harpoon(Cell(2, 1)))
        assertEquals(Blockers.NONE, thrice.state.blockerAt(2, 1))
    }
}
