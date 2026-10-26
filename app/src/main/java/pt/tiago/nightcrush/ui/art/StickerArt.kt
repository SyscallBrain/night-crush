package pt.tiago.nightcrush.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.tiago.nightcrush.game.StickerBook
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Current
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The 108 stickers: nine sea creatures (positions 1–9: fish, crab, scallop,
 * starfish, seahorse, jellyfish, turtle, octopus, manta), each tinted with the
 * colour of its world. Positions 7–8 are rare and 9 is epic, as in StickerBook.
 */
object StickerArt {

    /** One tint per world, from the warm south coast to the deep Atlantic. */
    val worldTints = listOf(
        Color(0xFF3EF2C6), Color(0xFFFFB86B), Color(0xFF8CF26B), Color(0xFF5AB0FF),
        Color(0xFF7FE0FF), Color(0xFFFF8A7A), Color(0xFF9ECE6A), Color(0xFFC58BFF),
        Color(0xFFF2E86D), Color(0xFF66D9C2), Color(0xFF7A8CFF), Color(0xFFB0C4FF),
    )

    fun tint(page: Int): Color = worldTints[(page - 1).mod(worldTints.size)]

    fun rarityColor(position: Int): Color = when (StickerBook.rarityOf(StickerBook.stickerId(1, position))) {
        StickerBook.Rarity.COMMON -> Glow
        StickerBook.Rarity.RARE -> Current
        StickerBook.Rarity.EPIC -> Plankton
    }

    fun page(id: String): Int = id.removePrefix("w").substringBefore('-').toIntOrNull() ?: 1
    fun position(id: String): Int = id.substringAfter('-').toIntOrNull() ?: 1
}

/** A round sticker. Missing stickers show a dim silhouette, so the page reads as a set to complete. */
@Composable
fun StickerTile(page: Int, position: Int, owned: Boolean, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Canvas(modifier = modifier.size(size)) { drawSticker(page, position, owned) }
}

fun DrawScope.drawSticker(page: Int, position: Int, owned: Boolean) {
    val tint = StickerArt.tint(page)
    val ring = StickerArt.rarityColor(position)
    val r = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    if (owned) {
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Abyss.copy(alpha = 0.9f)), center, r), r, center)
        drawCircle(ring, r - r * 0.04f, center, style = Stroke(width = r * 0.08f))
    } else {
        drawCircle(Abyss.copy(alpha = 0.6f), r, center)
        drawCircle(Reef, r - r * 0.03f, center, style = Stroke(width = r * 0.05f))
    }
    val ink = if (owned) Foam else Reef
    val fill = if (owned) tint else Reef.copy(alpha = 0.6f)
    // Draw in a unit box inset inside the circle.
    val box = r * 1.5f
    val left = center.x - box / 2f
    val top = center.y - box / 2f
    fun p(x: Float, y: Float) = Offset(left + x * box, top + y * box)
    when (position) {
        1 -> { // fish
            val tail = Path().apply { moveTo(p(0.68f, 0.5f).x, p(0.68f, 0.5f).y); lineTo(p(0.92f, 0.3f)); lineTo(p(0.92f, 0.7f)); close() }
            drawPath(tail, fill)
            drawOval(fill, topLeft = p(0.12f, 0.3f), size = Size(box * 0.62f, box * 0.4f))
            drawCircle(Abyss, box * 0.045f, p(0.3f, 0.45f))
            drawLine(ink.copy(alpha = 0.6f), p(0.48f, 0.36f), p(0.48f, 0.64f), strokeWidth = box * 0.03f)
        }
        2 -> { // crab
            for (side in listOf(-1f, 1f)) {
                for (k in 0 until 3) {
                    val y = 0.56f + k * 0.08f
                    drawLine(fill, p(0.5f + side * 0.2f, y), p(0.5f + side * 0.42f, y + 0.08f), strokeWidth = box * 0.045f, cap = StrokeCap.Round)
                }
                drawLine(fill, p(0.5f + side * 0.16f, 0.5f), p(0.5f + side * 0.28f, 0.32f), strokeWidth = box * 0.05f, cap = StrokeCap.Round)
                drawCircle(fill, box * 0.1f, p(0.5f + side * 0.3f, 0.25f))
            }
            drawOval(fill, topLeft = p(0.26f, 0.44f), size = Size(box * 0.48f, box * 0.28f))
            drawCircle(Abyss, box * 0.035f, p(0.43f, 0.52f))
            drawCircle(Abyss, box * 0.035f, p(0.57f, 0.52f))
        }
        3 -> { // scallop
            val shell = Path().apply {
                moveTo(p(0.5f, 0.84f).x, p(0.5f, 0.84f).y)
                lineTo(p(0.14f, 0.42f))
                cubicTo(p(0.2f, 0.12f), p(0.8f, 0.12f), p(0.86f, 0.42f))
                close()
            }
            drawPath(shell, fill)
            for (k in 0..4) {
                val t = k / 4f
                val edge = p(0.2f + 0.6f * t, 0.3f - 0.1f * sin((t * PI).toFloat()))
                drawLine(Abyss.copy(alpha = 0.45f), p(0.5f, 0.82f), edge, strokeWidth = box * 0.025f)
            }
            drawRect(fill, topLeft = p(0.4f, 0.8f), size = Size(box * 0.2f, box * 0.08f))
        }
        4 -> { // starfish
            val star = Path()
            for (k in 0 until 10) {
                val angle = -PI / 2 + k * PI / 5
                val radius = if (k % 2 == 0) 0.42f else 0.17f
                val point = p(0.5f + radius * cos(angle).toFloat(), 0.53f + radius * sin(angle).toFloat())
                if (k == 0) star.moveTo(point.x, point.y) else star.lineTo(point)
            }
            star.close()
            drawPath(star, fill)
            drawCircle(Abyss.copy(alpha = 0.35f), box * 0.05f, p(0.5f, 0.53f))
        }
        5 -> { // seahorse
            val body = Path().apply {
                moveTo(p(0.52f, 0.3f).x, p(0.52f, 0.3f).y)
                cubicTo(p(0.34f, 0.42f), p(0.36f, 0.62f), p(0.52f, 0.68f))
                cubicTo(p(0.64f, 0.74f), p(0.56f, 0.9f), p(0.44f, 0.84f))
            }
            drawPath(body, fill, style = Stroke(width = box * 0.13f, cap = StrokeCap.Round))
            drawCircle(fill, box * 0.11f, p(0.54f, 0.24f))
            drawLine(fill, p(0.6f, 0.26f), p(0.78f, 0.3f), strokeWidth = box * 0.07f, cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.7f), p(0.5f, 0.44f), p(0.6f, 0.42f), strokeWidth = box * 0.04f, cap = StrokeCap.Round)
            drawCircle(Abyss, box * 0.03f, p(0.56f, 0.22f))
        }
        6 -> { // jellyfish
            for (k in 0 until 4) {
                val x = 0.32f + k * 0.12f
                val tentacle = Path().apply {
                    moveTo(p(x, 0.5f).x, p(x, 0.5f).y)
                    cubicTo(p(x - 0.06f, 0.62f), p(x + 0.06f, 0.72f), p(x, 0.88f))
                }
                drawPath(tentacle, fill.copy(alpha = 0.8f), style = Stroke(width = box * 0.04f, cap = StrokeCap.Round))
            }
            drawArc(fill, 180f, 180f, true, topLeft = p(0.24f, 0.22f), size = Size(box * 0.52f, box * 0.56f))
            drawCircle(Foam.copy(alpha = if (owned) 0.5f else 0f), box * 0.05f, p(0.42f, 0.38f))
        }
        7 -> { // turtle
            for ((x, y) in listOf(0.28f to 0.32f, 0.72f to 0.32f, 0.28f to 0.72f, 0.72f to 0.72f)) {
                drawOval(fill.copy(alpha = 0.8f), topLeft = p(x - 0.1f, y - 0.06f), size = Size(box * 0.2f, box * 0.12f))
            }
            drawCircle(fill, box * 0.08f, p(0.5f, 0.18f))
            drawOval(fill, topLeft = p(0.26f, 0.28f), size = Size(box * 0.48f, box * 0.5f))
            val hex = Path()
            for (k in 0 until 6) {
                val angle = k * PI / 3
                val point = p(0.5f + 0.12f * cos(angle).toFloat(), 0.53f + 0.12f * sin(angle).toFloat())
                if (k == 0) hex.moveTo(point.x, point.y) else hex.lineTo(point)
            }
            hex.close()
            drawPath(hex, Abyss.copy(alpha = 0.4f), style = Stroke(width = box * 0.03f))
        }
        8 -> { // octopus
            for (k in 0 until 6) {
                val x = 0.25f + k * 0.1f
                val arm = Path().apply {
                    moveTo(p(x, 0.5f).x, p(x, 0.5f).y)
                    cubicTo(p(x + (if (k < 3) -0.1f else 0.1f), 0.66f), p(x, 0.78f), p(x + (if (k < 3) -0.06f else 0.06f), 0.86f))
                }
                drawPath(arm, fill, style = Stroke(width = box * 0.06f, cap = StrokeCap.Round))
            }
            drawOval(fill, topLeft = p(0.3f, 0.14f), size = Size(box * 0.4f, box * 0.42f))
            drawCircle(Abyss, box * 0.04f, p(0.42f, 0.38f))
            drawCircle(Abyss, box * 0.04f, p(0.58f, 0.38f))
        }
        else -> { // manta ray (epic)
            val manta = Path().apply {
                moveTo(p(0.5f, 0.3f).x, p(0.5f, 0.3f).y)
                cubicTo(p(0.66f, 0.3f), p(0.82f, 0.4f), p(0.94f, 0.52f))
                cubicTo(p(0.76f, 0.56f), p(0.62f, 0.6f), p(0.5f, 0.66f))
                cubicTo(p(0.38f, 0.6f), p(0.24f, 0.56f), p(0.06f, 0.52f))
                cubicTo(p(0.18f, 0.4f), p(0.34f, 0.3f), p(0.5f, 0.3f))
                close()
            }
            drawPath(manta, fill)
            drawLine(fill, p(0.5f, 0.64f), p(0.5f, 0.92f), strokeWidth = box * 0.03f, cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.6f), p(0.42f, 0.28f), p(0.4f, 0.2f), strokeWidth = box * 0.04f, cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.6f), p(0.58f, 0.28f), p(0.6f, 0.2f), strokeWidth = box * 0.04f, cap = StrokeCap.Round)
            if (owned) {
                for (k in 0 until 5) {
                    drawCircle(Plankton.copy(alpha = 0.8f), box * 0.02f, p(0.32f + k * 0.09f, 0.46f + 0.03f * sin(k.toFloat())))
                }
            }
        }
    }
}

private fun Path.lineTo(point: Offset) = lineTo(point.x, point.y)

private fun Path.cubicTo(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
