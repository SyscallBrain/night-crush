package pt.tiago.nightcrush.core.model

import pt.tiago.nightcrush.core.ENGINE_VERSION

/**
 * The complete logical state of a level in progress.
 *
 * The engine mutates a copy of this state and never touches the input one, so a
 * move is a pure function from state to state. Every array is indexed by
 * `r * width + c`. The class is deliberately array-based: the solver runs
 * hundreds of thousands of moves per level and allocations on the hot path cost
 * more than readability here.
 */
class GameState(
    val width: Int,
    val height: Int,
    var level: LevelRef = LevelRef(),
    val playable: BooleanArray = BooleanArray(width * height) { true },
    var pieces: IntArray = IntArray(width * height),
    /** Puffer countdowns, by cell. */
    var pieceData: IntArray = IntArray(width * height),
    var overlays: IntArray = IntArray(width * height),
    var blockers: IntArray = IntArray(width * height),
    /** Coral layers live in the code; this holds oyster counter, sinkhole hits, octopus hp. */
    var blockerData: IntArray = IntArray(width * height),
    /** Anchor cell index for 2x2 blockers (oyster, sinkhole), -1 elsewhere. */
    var blockerAnchor: IntArray = IntArray(width * height) { -1 },
    /** Octopus ink timer. */
    var blockerTimer: IntArray = IntArray(width * height),
    var silt: IntArray = IntArray(width * height),
    var floorFlags: IntArray = IntArray(width * height),
    /** For portal-in cells: the cell index pieces emerge at; -1 elsewhere. */
    var portalTarget: IntArray = IntArray(width * height) { -1 },
    /** Conveyor path: next cell index on the belt, -1 when off the belt, -2 at the end of an open belt. */
    var conveyorNext: IntArray = IntArray(width * height) { -1 },
    /** Available piece colours for this variant, by colour index. */
    var colors: IntArray = intArrayOf(0, 1, 2, 3, 4),
    /** Per-column spawners (new pieces fall in from the top of the column). */
    var spawner: BooleanArray = BooleanArray(width) { true },
    var lanternSpawner: BooleanArray = BooleanArray(width),
    var limitType: LimitType = LimitType.MOVES,
    var limitValue: Int = 40,
    var movesLeft: Int = 40,
    var movesUsed: Int = 0,
    var timeLeftSec: Int = 0,
    var objectives: List<Objective> = emptyList(),
    /** Progress slices, one IntArray per objective, sized by [Objective.slots]. */
    var progress: Array<IntArray> = emptyArray(),
    var lanternTotal: Int = 0,
    var lanternMaxOnBoard: Int = 0,
    var lanternEvery: Int = 0,
    var lanternsSpawned: Int = 0,
    var lanternsOnBoard: Int = 0,
    var lanternsDelivered: Int = 0,
    /** One lantern per spawner column per move, however many refills a move needs. */
    var lanternSpawnedThisMove: BooleanArray = BooleanArray(width),
    /** One SplitMix64 state per column, plus the auxiliary stream. */
    var rngColumns: LongArray = LongArray(width),
    var rngAux: Long = 0,
    var score: Int = 0,
    var outcome: Outcome = Outcome.Playing,
    var maxCascade: Int = 0,
    /** Specials created this level, indexed by [SpecialOrderKind.ordinal]. */
    var specialsCreated: IntArray = IntArray(SpecialOrderKind.entries.size),
    /** Combos made this level, indexed by [ComboKind.ordinal]. */
    var combosMade: IntArray = IntArray(ComboKind.entries.size),
    var seahorsesFreed: Int = 0,
    /** Fall order stamps used to pick where a cascade-created special is born. */
    var landStamp: IntArray = IntArray(width * height),
    var landCounter: Int = 0,
    /** Puzzle boards: having no valid move loses instead of shuffling. */
    val stuckLoses: Boolean = false,
) {
    val size: Int get() = width * height

    fun idx(r: Int, c: Int): Int = r * width + c
    fun rowOf(index: Int): Int = index / width
    fun colOf(index: Int): Int = index % width
    fun inBounds(r: Int, c: Int): Boolean = r in 0 until height && c in 0 until width
    fun isPlayable(index: Int): Boolean = playable[index]

    /** True when nothing can rest in the cell: holes, blockers, and overlay screens. */
    fun isObstructed(index: Int): Boolean =
        !playable[index] || blockers[index] != Blockers.NONE || overlays[index] != 0

    fun isEmpty(index: Int): Boolean =
        playable[index] && pieces[index] == Pieces.EMPTY && blockers[index] == Blockers.NONE

    /** True when a piece could land here and move later. */
    fun isOpen(index: Int): Boolean =
        playable[index] && pieces[index] == Pieces.EMPTY && blockers[index] == Blockers.NONE

    fun isGlow(index: Int): Boolean = floorFlags[index] and FloorFlags.GLOW != 0
    fun isExit(index: Int): Boolean = floorFlags[index] and FloorFlags.EXIT != 0
    fun isPortalIn(index: Int): Boolean = floorFlags[index] and FloorFlags.PORTAL_IN != 0
    fun isPortalOut(index: Int): Boolean = floorFlags[index] and FloorFlags.PORTAL_OUT != 0

    fun playableCount(): Int = playable.count { it }

    /** Deep copy: applyMove mutates this, never the input state. */
    fun copyState(): GameState = GameState(
        width = width,
        height = height,
        level = level,
        playable = playable.copyOf(),
        pieces = pieces.copyOf(),
        pieceData = pieceData.copyOf(),
        overlays = overlays.copyOf(),
        blockers = blockers.copyOf(),
        blockerData = blockerData.copyOf(),
        blockerAnchor = blockerAnchor.copyOf(),
        blockerTimer = blockerTimer.copyOf(),
        silt = silt.copyOf(),
        floorFlags = floorFlags.copyOf(),
        portalTarget = portalTarget.copyOf(),
        conveyorNext = conveyorNext.copyOf(),
        colors = colors.copyOf(),
        spawner = spawner.copyOf(),
        lanternSpawner = lanternSpawner.copyOf(),
        limitType = limitType,
        limitValue = limitValue,
        movesLeft = movesLeft,
        movesUsed = movesUsed,
        timeLeftSec = timeLeftSec,
        objectives = objectives,
        progress = Array(progress.size) { progress[it].copyOf() },
        lanternTotal = lanternTotal,
        lanternMaxOnBoard = lanternMaxOnBoard,
        lanternEvery = lanternEvery,
        lanternsSpawned = lanternsSpawned,
        lanternsOnBoard = lanternsOnBoard,
        lanternsDelivered = lanternsDelivered,
        lanternSpawnedThisMove = lanternSpawnedThisMove.copyOf(),
        rngColumns = rngColumns.copyOf(),
        rngAux = rngAux,
        score = score,
        outcome = outcome,
        maxCascade = maxCascade,
        specialsCreated = specialsCreated.copyOf(),
        combosMade = combosMade.copyOf(),
        seahorsesFreed = seahorsesFreed,
        landStamp = landStamp.copyOf(),
        landCounter = landCounter,
        stuckLoses = stuckLoses,
    )

    init {
        require(width in 1..9 && height in 1..9) { "Board must be at most 9x9" }
    }
}

/** A player action. Boosters are wired in the meta phase; the model is frozen here. */
sealed interface Move {
    data class Swap(val a: Cell, val b: Cell) : Move
    data class Harpoon(val target: Cell) : Move
    data class FreeSwap(val a: Cell, val b: Cell) : Move
    data object Shuffle : Move
    data object TimeUp : Move
}

/** Extra state the app tracks about a level, not part of the engine. */
data class LevelInfo(
    val ordinal: Int,
    val worldId: String,
    val titlePt: String,
    val titleEn: String,
    val isFullMoon: Boolean,
    val difficulty: Difficulty,
    val revision: Int,
    val engineVersion: Int = ENGINE_VERSION,
)
