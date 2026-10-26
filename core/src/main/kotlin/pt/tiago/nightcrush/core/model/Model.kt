package pt.tiago.nightcrush.core.model

import pt.tiago.nightcrush.core.ENGINE_VERSION

/** The six piece colours, in the fixed order used by the design. */
enum class PieceColor {
    BLUE, YELLOW, PURPLE, TEAL, GREEN, CORAL;

    companion object {
        fun fromName(name: String): PieceColor =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: throw ModelException("Unknown piece colour: $name")
    }
}

/** Special pieces created by matches, plus the abyss pearl. */
enum class SpecialKind { RAY, JELLY, LANTERN_FISH, ABYSS_PEARL }

/** The ten blockers of the design. */
enum class BlockerKind { CORAL, OIL, SEAHORSE_BUBBLE, GIANT_OYSTER, SINKHOLE, OCTOPUS }

/** Overlays that sit on top of a piece. */
enum class OverlayKind { BARNACLE, NET }

enum class Difficulty { EASY, MEDIUM, HARD }

enum class LimitType { MOVES, TIME }

/** Why a level ended. */
sealed interface Outcome {
    data object Playing : Outcome
    data object Won : Outcome
    sealed interface Lost : Outcome {
        data object OutOfMoves : Lost
        data object OutOfTime : Lost
        data object PufferExploded : Lost
    }
}

/** A single cell of the board grid, in board coordinates (row 0 is the top). */
data class Cell(val r: Int, val c: Int)

/**
 * Piece encoding for the engine arrays.
 *
 * Codes are stable and hashed into certificates: never renumber them.
 * 0        empty
 * 1..6     plain piece, colour index (0..5)
 * 7..12    ray that fires along its row (created by a vertical four)
 * 13..18   ray that fires along its column (created by a horizontal four)
 * 19..24   jellyfish (two-phase 3x3 explosion)
 * 25..30   lantern fish (clears neighbours, then flies to a target)
 * 31..36   pufferfish piece, colour index, countdown stored separately
 * 37       lantern (ingredient)
 * 38       abyss pearl
 */
object Pieces {
    const val EMPTY = 0
    const val LANTERN = 37
    const val PEARL = 38
    const val COLOR_NONE = -1

    fun plain(color: Int): Int = 1 + color
    fun rayAlongRow(color: Int): Int = 7 + color
    fun rayAlongColumn(color: Int): Int = 13 + color
    fun jelly(color: Int): Int = 19 + color
    fun lanternFish(color: Int): Int = 25 + color
    fun puffer(color: Int): Int = 31 + color

    /** Colour index of any coloured piece, or COLOR_NONE. */
    fun colorOf(code: Int): Int = when (code) {
        in 1..36 -> (code - 1) % 6
        else -> COLOR_NONE
    }

    /** Special kind of a code, or null for plain pieces, lanterns and pearls. */
    fun specialOf(code: Int): SpecialKind? = when (code) {
        in 7..12 -> SpecialKind.RAY
        in 13..18 -> SpecialKind.RAY
        in 19..24 -> SpecialKind.JELLY
        in 25..30 -> SpecialKind.LANTERN_FISH
        else -> null
    }

    /** True when the code can take part in a colour match. */
    fun isMatchable(code: Int): Boolean = code in 1..36

    /** True for ray, jelly and lantern-fish pieces (pearls and puffers are not swap-specials). */
    fun isSwapSpecial(code: Int): Boolean =
        specialOf(code) != null || code == PEARL

    fun isRay(code: Int): Boolean = code in 7..18
    fun isJelly(code: Int): Boolean = code in 19..24
    fun isLanternFish(code: Int): Boolean = code in 25..30
    fun isPuffer(code: Int): Boolean = code in 31..36
}

/** Overlay encoding: 0 none, 1..3 barnacle layers, 4 net. */
object Overlays {
    const val NONE = 0
    const val NET = 4
    fun barnacle(layers: Int): Int = layers
    fun barnacleLayers(code: Int): Int = if (code in 1..3) code else 0
    fun isBarnacle(code: Int): Boolean = code in 1..3
    fun isNet(code: Int): Boolean = code == NET
}

/** Blocker encoding: 0 none, 1..3 coral layers, 4 oil, 5 bubble, 6..11 oyster (colour 0..5), 12 sinkhole, 13 octopus. */
object Blockers {
    const val NONE = 0
    const val OIL = 4
    const val SEAHORSE_BUBBLE = 5
    const val OYSTER_FIRST = 6
    const val OYSTER_LAST = 11
    const val SINKHOLE = 12
    const val OCTOPUS = 13

    fun coral(layers: Int): Int = layers
    fun coralLayers(code: Int): Int = if (code in 1..3) code else 0

    fun oyster(color: Int): Int = OYSTER_FIRST + color
    fun isOyster(code: Int): Boolean = code in OYSTER_FIRST..OYSTER_LAST
    fun oysterColor(code: Int): Int = if (isOyster(code)) code - OYSTER_FIRST else -1

    /** Blocker kinds in their canonical order (indexes used by pooled blocker objectives). */
    val KIND_ORDER: List<BlockerKind> = listOf(
        BlockerKind.CORAL, BlockerKind.OIL, BlockerKind.SEAHORSE_BUBBLE,
        BlockerKind.GIANT_OYSTER, BlockerKind.SINKHOLE, BlockerKind.OCTOPUS,
    )

    fun kindOf(code: Int): BlockerKind? = when {
        code in 1..3 -> BlockerKind.CORAL
        code == OIL -> BlockerKind.OIL
        code == SEAHORSE_BUBBLE -> BlockerKind.SEAHORSE_BUBBLE
        isOyster(code) -> BlockerKind.GIANT_OYSTER
        code == SINKHOLE -> BlockerKind.SINKHOLE
        code == OCTOPUS -> BlockerKind.OCTOPUS
        else -> null
    }

    fun codeOf(kind: BlockerKind): Int = when (kind) {
        BlockerKind.CORAL -> 1
        BlockerKind.OIL -> OIL
        BlockerKind.SEAHORSE_BUBBLE -> SEAHORSE_BUBBLE
        BlockerKind.GIANT_OYSTER -> OYSTER_FIRST
        BlockerKind.SINKHOLE -> SINKHOLE
        BlockerKind.OCTOPUS -> OCTOPUS
    }
}

/** Floor flag bits. */
object FloorFlags {
    const val GLOW = 1
    const val EXIT = 2
    const val PORTAL_IN = 4
    const val PORTAL_OUT = 8
}

class ModelException(message: String) : IllegalArgumentException(message)

/** Runtime identity of the level a state belongs to. */
data class LevelRef(
    val id: String = "test",
    val worldId: String = "world-00",
    val ordinal: Int = 0,
    val revision: Int = 1,
    val difficulty: Difficulty = Difficulty.EASY,
    val engineVersion: Int = ENGINE_VERSION,
    val seedHex: String = "0000000000000000",
)
