package pt.tiago.nightcrush.ui.art

import androidx.compose.ui.graphics.toArgb
import pt.tiago.nightcrush.ui.theme.PieceBlue
import pt.tiago.nightcrush.ui.theme.PieceCoral
import pt.tiago.nightcrush.ui.theme.PieceGreen
import pt.tiago.nightcrush.ui.theme.PiecePurple
import pt.tiago.nightcrush.ui.theme.PieceTeal
import pt.tiago.nightcrush.ui.theme.PieceYellow

/** One palette of six piece colours, in PieceColor order. */
class PiecePalette(val id: String, val colors: IntArray) {
    override fun equals(other: Any?): Boolean = other is PiecePalette && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/**
 * The default bioluminescent palette plus colour-blind alternatives. Shapes are
 * always distinct, so colour is never the only clue.
 */
object PiecePalettes {

    val normal = PiecePalette(
        "normal",
        intArrayOf(
            PieceBlue.toArgb(),
            PieceYellow.toArgb(),
            PiecePurple.toArgb(),
            PieceTeal.toArgb(),
            PieceGreen.toArgb(),
            PieceCoral.toArgb(),
        ),
    )

    // Okabe-Ito / Tol inspired sets, ordered BLUE, YELLOW, PURPLE, TEAL, GREEN, CORAL.
    val deuteranopia = PiecePalette(
        "deuteranopia",
        intArrayOf(0xFF0072B2.toInt(), 0xFFF0E442.toInt(), 0xFFCC79A7.toInt(), 0xFF56B4E9.toInt(), 0xFF009E73.toInt(), 0xFFE69F00.toInt()),
    )

    val protanopia = PiecePalette(
        "protanopia",
        intArrayOf(0xFF4477AA.toInt(), 0xFFDDCC77.toInt(), 0xFFAA4499.toInt(), 0xFF66CCEE.toInt(), 0xFF44AA99.toInt(), 0xFFEE6677.toInt()),
    )

    val tritanopia = PiecePalette(
        "tritanopia",
        intArrayOf(0xFF0072B2.toInt(), 0xFFFFCC66.toInt(), 0xFFEE3377.toInt(), 0xFF009988.toInt(), 0xFFBBBBBB.toInt(), 0xFFCC3311.toInt()),
    )

    fun byName(name: String): PiecePalette = when (name.lowercase()) {
        "deuteranopia" -> deuteranopia
        "protanopia" -> protanopia
        "tritanopia" -> tritanopia
        else -> normal
    }
}
