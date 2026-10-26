package pt.tiago.nightcrush.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk level format (content/levels/world-NN/level-NNNN.json).
 * Shared by the level tool and the app: one schema, one parser.
 */
@Serializable
data class LevelFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val engineVersion: Int = 0,
    val id: String,
    val ordinal: Int,
    val worldId: String,
    val titlePt: String,
    val titleEn: String = "",
    val familyId: String = "handmade",
    val learningTags: List<String> = emptyList(),
    val revision: Int = 1,
    val isFullMoon: Boolean = false,
    val tutorial: TutorialSpec? = null,
    val variants: Map<String, VariantSpec> = emptyMap(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class TutorialSpec(val steps: List<TutorialStepSpec>)

@Serializable
data class TutorialStepSpec(
    val textKey: String,
    val highlight: List<List<Int>> = emptyList(),
    val forcedSwap: List<List<Int>>? = null,
)

@Serializable
data class VariantSpec(
    val seedHex: String,
    val width: Int,
    val height: Int,
    val mask: List<String>,
    val colors: List<String>,
    val cells: List<CellSpec> = emptyList(),
    /** Null means: every column with a playable top cell spawns. */
    val spawners: List<SpawnerSpec>? = null,
    val exits: List<PosSpec> = emptyList(),
    val portals: List<PortalSpec> = emptyList(),
    /** Cells of the belt in order; the belt wraps around. */
    val conveyor: List<PosSpec> = emptyList(),
    val limit: LimitSpec,
    val objectives: List<ObjectiveSpec>,
    val lanternRules: LanternRulesSpec? = null,
    val stars: StarsSpec,
    val expectedMetrics: MetricsSpec? = null,
    /**
     * Puzzle board: no refills expected, and being stuck loses (see GameState.stuckLoses).
     * Never written when false, so rewriting a campaign level keeps its bytes and hash.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val puzzle: Boolean = false,
)

@Serializable
data class PosSpec(val r: Int, val c: Int)

@Serializable
data class PortalSpec(val from: PosSpec, val to: PosSpec)

@Serializable
data class SpawnerSpec(val c: Int, val lanterns: Boolean = false)

@Serializable
data class LimitSpec(val type: String, val value: Int)

@Serializable
data class StarsSpec(val two: Int, val three: Int)

@Serializable
data class MetricsSpec(
    val humanWinRate: Double = 0.0,
    val solverMoves: Int = 0,
    val solverMillis: Long = 0,
)

@Serializable
data class LanternRulesSpec(val total: Int, val maxOnBoard: Int, val everyMoves: Int)

@Serializable
data class PieceSpec(
    /** normal | lantern | puffer */
    val kind: String,
    val color: String? = null,
    val counter: Int? = null,
)

@Serializable
data class OverlaySpec(val kind: String, val layers: Int = 1)

@Serializable
data class BlockerSpec(
    /** coral | oil | seahorseBubble | giantOyster | sinkhole | octopus */
    val kind: String,
    val layers: Int = 1,
    val color: String? = null,
    val counter: Int = 0,
    val hp: Int = 5,
)

@Serializable
data class FloorSpec(val silt: Int = 0, val glow: Boolean = false, val exit: Boolean = false)

@Serializable
data class CellSpec(
    val r: Int,
    val c: Int,
    val piece: PieceSpec? = null,
    val overlay: OverlaySpec? = null,
    val blocker: BlockerSpec? = null,
    val floor: FloorSpec? = null,
)

@Serializable
data class ObjectiveSpec(
    /** collect | silt | deliverLanterns | orders | clearBlockers | spreadGlow | freeSeahorses */
    val type: String,
    val colors: Map<String, Int> = emptyMap(),
    val count: Int = 0,
    val orders: List<OrderSpec> = emptyList(),
    val blockers: Map<String, Int> = emptyMap(),
)

@Serializable
data class OrderSpec(
    /** makeSpecial | makeCombo | collect | breakLayers */
    val kind: String,
    val value: String? = null,
    @SerialName("color") val colorName: String? = null,
    val count: Int,
)

/** A swap, serialized for saved matches and certificates. */
@Serializable
data class MoveSpec(val a: List<Int>, val b: List<Int>) {
    fun toMove(): Move = Move.Swap(Cell(a[0], a[1]), Cell(b[0], b[1]))

    companion object {
        fun of(move: Move.Swap): MoveSpec = MoveSpec(listOf(move.a.r, move.a.c), listOf(move.b.r, move.b.c))
    }
}
