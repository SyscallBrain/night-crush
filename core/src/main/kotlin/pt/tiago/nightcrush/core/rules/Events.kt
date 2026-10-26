package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.BlockerKind
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.ComboKind
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.SpecialKind

/** Result of one move: the new state, its ordered events and the verdict. */
data class MoveResult(
    val state: GameState,
    val events: List<GameEvent>,
    val outcome: Outcome,
)

/** What a match looked like, after group classification. */
enum class MatchKind { THREE, FOUR, LT, SQUARE, FIVE }

/** Why a piece left the board. */
enum class ClearCause { MATCH, SPECIAL, COMBO, CONVEYOR, OIL, SINKHOLE, OCTOPUS, FINALE, BOOSTER }

/** Damageable things that report layers or health. */
enum class DamageTarget {
    CORAL, OIL, SEAHORSE_BUBBLE, GIANT_OYSTER, SINKHOLE, OCTOPUS, BARNACLE, NET;

    companion object {
        fun ofBlocker(kind: BlockerKind): DamageTarget = when (kind) {
            BlockerKind.CORAL -> CORAL
            BlockerKind.OIL -> OIL
            BlockerKind.SEAHORSE_BUBBLE -> SEAHORSE_BUBBLE
            BlockerKind.GIANT_OYSTER -> GIANT_OYSTER
            BlockerKind.SINKHOLE -> SINKHOLE
            BlockerKind.OCTOPUS -> OCTOPUS
        }
    }
}

/**
 * Everything the UI animates. The engine returns an ordered, immutable event list
 * for every move; frame rate never changes the result.
 */
sealed interface GameEvent {
    data class SwapStarted(val a: Cell, val b: Cell) : GameEvent
    data class SwapRejected(val a: Cell, val b: Cell) : GameEvent
    data class Matched(val cells: List<Cell>, val kind: MatchKind, val color: Int) : GameEvent
    data class SpecialCreated(val cell: Cell, val kind: SpecialKind, val color: Int) : GameEvent
    data class SpecialActivated(val cell: Cell, val kind: SpecialKind, val area: List<Cell>) : GameEvent
    data class ComboActivated(val combo: ComboKind, val area: List<Cell>) : GameEvent
    data class PieceCleared(val cell: Cell, val color: Int, val code: Int, val cause: ClearCause) : GameEvent
    data class LayerDamaged(val cell: Cell, val target: DamageTarget, val remaining: Int) : GameEvent
    data class SiltCleared(val cell: Cell) : GameEvent
    data class GlowSpread(val cells: List<Cell>) : GameEvent
    data class Fell(val from: Cell, val to: Cell, val viaPortal: Boolean = false) : GameEvent
    data class Slid(val from: Cell, val to: Cell) : GameEvent
    data class Spawned(val cell: Cell, val fromOffset: Int) : GameEvent
    data class LanternDelivered(val cell: Cell) : GameEvent
    data class SeahorseFreed(val cell: Cell) : GameEvent
    data class ConveyorMoved(val path: List<Cell>) : GameEvent
    data class OilSpread(val from: Cell, val to: Cell) : GameEvent
    data class PufferTick(val cell: Cell, val remaining: Int) : GameEvent
    data class OctopusInk(val targets: List<Cell>) : GameEvent
    data class SinkholeSwallow(val cell: Cell) : GameEvent
    data class OysterOpened(val anchor: Cell) : GameEvent
    data class CascadeStep(val index: Int) : GameEvent
    data object Shuffled : GameEvent
    data object FinaleStarted : GameEvent
    data class ScoreDelta(val points: Int, val at: Int) : GameEvent
    data class ObjectiveProgress(val objective: Int, val slot: Int, val value: Int) : GameEvent
    data class Finished(val outcome: Outcome) : GameEvent

    /**
     * The board as it stands at this point of the move, emitted around every
     * gravity pass and after swaps and shuffles. Animation only: the UI draws
     * between consecutive snapshots instead of rebuilding the board from events.
     */
    class Board(val snapshot: BoardSnapshot) : GameEvent
}

/** The drawable layers of a board at one instant. Arrays are private copies. */
class BoardSnapshot(
    val pieces: IntArray,
    val pieceData: IntArray,
    val overlays: IntArray,
    val blockers: IntArray,
    val blockerData: IntArray,
    val silt: IntArray,
    val floorFlags: IntArray,
) {
    fun sameAs(other: BoardSnapshot): Boolean =
        pieces.contentEquals(other.pieces) &&
            pieceData.contentEquals(other.pieceData) &&
            overlays.contentEquals(other.overlays) &&
            blockers.contentEquals(other.blockers) &&
            blockerData.contentEquals(other.blockerData) &&
            silt.contentEquals(other.silt) &&
            floorFlags.contentEquals(other.floorFlags)

    companion object {
        fun of(s: GameState): BoardSnapshot = BoardSnapshot(
            pieces = s.pieces.copyOf(),
            pieceData = s.pieceData.copyOf(),
            overlays = s.overlays.copyOf(),
            blockers = s.blockers.copyOf(),
            blockerData = s.blockerData.copyOf(),
            silt = s.silt.copyOf(),
            floorFlags = s.floorFlags.copyOf(),
        )
    }
}
