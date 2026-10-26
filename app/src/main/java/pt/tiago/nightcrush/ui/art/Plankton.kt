package pt.tiago.nightcrush.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.ui.theme.Current
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Plankton
import kotlin.math.roundToInt
import kotlin.math.sin

private class Mote(val x: Float, val y: Float, val radius: Float, val speed: Float, val phase: Float, val color: Color)

/**
 * The ambient sea behind every screen (design 2.7): about 30 specks of plankton
 * drifting upwards and a soft swell of light at the bottom. With reduced motion
 * the specks stay still.
 */
@Composable
fun PlanktonBackground(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val motes = remember {
        val random = java.util.Random(20261013)
        val colors = listOf(Glow, Current, Plankton)
        List(30) {
            Mote(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.8f + random.nextFloat() * 1.8f,
                speed = 0.004f + random.nextFloat() * 0.01f,
                phase = random.nextFloat() * 6.28f,
                color = colors[it % colors.size],
            )
        }
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            // About 30 fps is plenty for specks this slow.
            val now = withFrameNanos { it }
            val next = (now - start) / 1_000_000_000f
            if (next - seconds >= 1f / 30f) seconds = next
        }
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Swell of bioluminescent water at the bottom of the screen.
        val swell = Path().apply {
            moveTo(0f, h * 0.9f)
            cubicTo(w * 0.25f, h * 0.86f, w * 0.5f, h * 0.94f, w, h * 0.88f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(swell, Brush.verticalGradient(listOf(Glow.copy(alpha = 0.06f), Glow.copy(alpha = 0f)), startY = h * 0.86f, endY = h))
        val density = density
        for (mote in motes) {
            val y = ((mote.y - seconds * mote.speed) % 1f + 1f) % 1f
            val x = mote.x + 0.012f * sin(seconds * 0.6f + mote.phase)
            val twinkle = 0.35f + 0.3f * (0.5f + 0.5f * sin(seconds * 1.3f + mote.phase))
            val center = Offset(x * w, y * h)
            drawCircle(mote.color.copy(alpha = twinkle * 0.25f), radius = mote.radius * density * 3f, center = center)
            drawCircle(mote.color.copy(alpha = twinkle), radius = mote.radius * density, center = center)
        }
    }
}

/** One piece sprite as a small icon, e.g. next to a "collect" goal. */
@Composable
fun PieceIcon(color: Int, palette: PiecePalette, style: PieceSprites.Style, size: Dp = 22.dp) {
    val context = LocalContext.current
    val sprites = remember { PieceSprites(context) }
    Canvas(modifier = Modifier.size(size)) {
        val px = this.size.minDimension.roundToInt().coerceAtLeast(1)
        val sprite = sprites.pieceSprite(Pieces.plain(color), style, palette, px) ?: return@Canvas
        drawImage(sprite)
    }
}
