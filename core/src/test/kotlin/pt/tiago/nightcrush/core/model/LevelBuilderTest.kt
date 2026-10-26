package pt.tiago.nightcrush.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.ENGINE_VERSION

class LevelBuilderTest {

    private fun sample(): LevelFile = LevelFile(
        engineVersion = ENGINE_VERSION,
        id = "level-0001",
        ordinal = 1,
        worldId = "world-01",
        titlePt = "Ria Formosa",
        titleEn = "Ria Formosa",
        variants = mapOf(
            "easy" to VariantSpec(
                seedHex = "0000000000000001",
                width = 3,
                height = 3,
                mask = listOf("XXX", "XXX", "XXX"),
                colors = listOf("BLUE", "YELLOW"),
                cells = listOf(
                    CellSpec(0, 0, piece = PieceSpec("normal", "BLUE")),
                    CellSpec(1, 0, piece = PieceSpec("normal", "YELLOW")),
                    CellSpec(2, 0, piece = PieceSpec("normal", "BLUE")),
                    CellSpec(0, 1, piece = PieceSpec("normal", "YELLOW")),
                    CellSpec(1, 1, piece = PieceSpec("normal", "BLUE")),
                    CellSpec(2, 1, piece = PieceSpec("normal", "YELLOW")),
                    CellSpec(0, 2, piece = PieceSpec("normal", "BLUE")),
                    CellSpec(1, 2, piece = PieceSpec("normal", "YELLOW")),
                    CellSpec(2, 2, piece = PieceSpec("normal", "BLUE")),
                    CellSpec(1, 2, floor = FloorSpec(silt = 1)),
                ),
                limit = LimitSpec("moves", 20),
                objectives = listOf(ObjectiveSpec("collect", colors = mapOf("BLUE" to 3))),
                stars = StarsSpec(1000, 2000),
            ),
        ),
    )

    @Test
    fun `a simple variant builds a live state`() {
        val state = LevelBuilder.start(sample(), Difficulty.EASY)
        assertEquals(3, state.width)
        assertEquals(3, state.height)
        assertEquals(Pieces.plain(0), state.pieces[state.idx(0, 0)])
        assertEquals(Pieces.plain(1), state.pieces[state.idx(1, 0)])
        assertEquals(20, state.movesLeft)
        assertEquals(LimitType.MOVES, state.limitType)
        assertTrue(state.spawner.all { it })
        assertEquals(1, state.objectives.size)
        assertEquals(6, state.progress[0].size)
        assertEquals(1, state.silt[state.idx(1, 2)])
        assertEquals("level-0001", state.level.id)
        assertEquals(Difficulty.EASY, state.level.difficulty)
    }

    @Test
    fun `an engine mismatch is refused`() {
        val broken = sample().copy(engineVersion = ENGINE_VERSION + 1)
        assertThrows(ModelException::class.java) { LevelBuilder.start(broken, Difficulty.EASY) }
    }

    @Test
    fun `a 2x2 oyster fills four cells around one anchor`() {
        val level = sample().let { base ->
            base.copy(
                variants = base.variants.mapValues { (_, variant) ->
                    variant.copy(
                        cells = variant.cells + CellSpec(
                            1, 0,
                            blocker = BlockerSpec("giantOyster", color = "BLUE", counter = 2),
                        ),
                    )
                },
            )
        }
        val state = LevelBuilder.start(level, Difficulty.EASY)
        assertEquals(BlockerKind.GIANT_OYSTER, Blockers.kindOf(state.blockers[state.idx(1, 0)]))
        assertEquals(Blockers.oyster(0), state.blockers[state.idx(2, 1)])
        assertEquals(state.idx(1, 0), state.blockerAnchor[state.idx(2, 1)])
        assertEquals(2, state.blockerData[state.idx(2, 1)])
        assertEquals(Pieces.EMPTY, state.pieces[state.idx(1, 0)])
    }

    @Test
    fun `pre-level boosters are placed deterministically from the aux stream`() {
        val a = LevelBuilder.start(sample(), Difficulty.EASY, listOf(PreLevelBooster.RAY, PreLevelBooster.ABYSS_PEARL))
        val b = LevelBuilder.start(sample(), Difficulty.EASY, listOf(PreLevelBooster.RAY, PreLevelBooster.ABYSS_PEARL))
        assertEquals(a.pieces.toList(), b.pieces.toList())
        assertTrue(a.pieces.any { Pieces.isRay(it) })
        assertTrue(a.pieces.any { it == Pieces.PEARL })
    }

    @Test
    fun `the level file round-trips through json`() {
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(LevelFile.serializer(), sample())
        val parsed = json.decodeFromString(LevelFile.serializer(), text)
        assertEquals(sample(), parsed)
    }

    @Test
    fun `portals and exits survive the build`() {
        val level = sample().let { base ->
            base.copy(
                variants = base.variants.mapValues { (_, variant) ->
                    variant.copy(
                        portals = listOf(PortalSpec(PosSpec(0, 0), PosSpec(0, 2))),
                        exits = listOf(PosSpec(2, 2)),
                    )
                },
            )
        }
        val state = LevelBuilder.start(level, Difficulty.EASY)
        assertTrue(state.isPortalIn(state.idx(0, 0)))
        assertTrue(state.isPortalOut(state.idx(0, 2)))
        assertEquals(state.idx(0, 2), state.portalTarget[state.idx(0, 0)])
        assertTrue(state.isExit(state.idx(2, 2)))
    }
}
