package pt.tiago.nightcrush.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.data.Profile
import pt.tiago.nightcrush.game.ProfileReducer
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Plankton
import kotlin.math.PI
import kotlin.math.sin

/**
 * "The coast that lights up" (design 2.7 and 8.7): a night panorama per world
 * with 50 points of light on the shore, one per level. A level won on any
 * difficulty lights its point; three stars make it brighter and cast a
 * reflection on the water.
 */
object CoastArt {

    private val FAR_LAND = Color(0xFF0C2A38)
    private val NEAR_LAND = Color(0xFF040D13)

    /** The shape of each world's shore: base height, roughness and a landmark. */
    private class Shape(val base: Float, val amplitude: Float, val waves: Float, val landmark: Landmark)

    private enum class Landmark { NONE, LIGHTHOUSE, ARCH, PEAKS, ISLETS, BIG_WAVE, RIDGE }

    private val shapes = listOf(
        Shape(0.70f, 0.03f, 3f, Landmark.ISLETS), // Ria Formosa: flat lagoon islands
        Shape(0.48f, 0.08f, 2f, Landmark.LIGHTHOUSE), // Sagres: high cliffs and the cape light
        Shape(0.52f, 0.12f, 1.5f, Landmark.NONE), // Arrábida: green hills over the sea
        Shape(0.62f, 0.10f, 4f, Landmark.LIGHTHOUSE), // Berlengas: rocky island with its fort light
        Shape(0.50f, 0.06f, 2f, Landmark.BIG_WAVE), // Nazaré: the headland and the giant wave
        Shape(0.50f, 0.10f, 5f, Landmark.NONE), // Costa Vicentina: broken cliffs
        Shape(0.42f, 0.14f, 3f, Landmark.PEAKS), // Fajãs dos Açores: volcanic peaks
        Shape(0.58f, 0.07f, 6f, Landmark.NONE), // Porto Moniz: lava rock pools
        Shape(0.54f, 0.05f, 2f, Landmark.ARCH), // Gruta de Benagil: the sea cave arch
        Shape(0.68f, 0.05f, 5f, Landmark.ISLETS), // Ilhas Selvagens: tiny islets
        Shape(0.60f, 0.12f, 4f, Landmark.RIDGE), // Fossa Abissal: undersea ridges
        Shape(0.74f, 0.02f, 2f, Landmark.NONE), // Atlântico Profundo: open ocean
    )

    /** 0 = dark, 1 = won, 2 = won with three stars; one entry per level of the world. */
    fun lights(profile: Profile, from: Int, to: Int): IntArray = IntArray(to - from + 1) { index ->
        val ordinal = from + index
        var best = 0
        for (difficulty in Difficulty.entries) {
            val progress = profile.levels[ProfileReducer.key(ordinal, difficulty)] ?: continue
            if (progress.wins > 0) best = maxOf(best, if (progress.stars >= 3) 2 else 1)
        }
        best
    }

    fun DrawScope.drawCoast(world: Int, lights: IntArray, horizonFraction: Float = 0.72f) {
        val shape = shapes[world.mod(shapes.size)]
        val w = size.width
        val h = size.height
        val horizon = h * horizonFraction
        val random = java.util.Random(7_919L * (world + 1))

        // Sky with a crescent moon and a scatter of stars.
        // The sky brightens towards the horizon so the shore reads as a silhouette.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF06141D), Color(0xFF15475A)), endY = horizon))
        repeat(28) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * horizon * 0.7f
            drawCircle(Foam.copy(alpha = 0.25f + random.nextFloat() * 0.45f), radius = 0.6f + random.nextFloat() * 1.2f, center = Offset(x, y))
        }
        val moon = Offset(w * 0.84f, h * 0.2f)
        val moonR = h * 0.09f
        drawCircle(Plankton.copy(alpha = 0.12f), moonR * 2.4f, moon)
        drawCircle(Color(0xFFF6F1C8), moonR, moon)
        drawCircle(Color(0xFF0A222E), moonR * 0.86f, Offset(moon.x + moonR * 0.42f, moon.y - moonR * 0.18f))

        // Water: darker towards the bottom, with faint swell lines.
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF103747), Abyss), startY = horizon, endY = h),
            topLeft = Offset(0f, horizon),
            size = Size(w, h - horizon),
        )
        repeat(6) { k ->
            val y = horizon + (h - horizon) * (0.15f + k * 0.15f)
            drawLine(Glow.copy(alpha = 0.05f), Offset(w * 0.05f, y), Offset(w * 0.95f, y), strokeWidth = 1f)
        }

        // Far shore, then near shore: both from one height function per world.
        val phase = random.nextFloat() * 6.28f
        fun ridge(x: Float, scale: Float, shift: Float): Float {
            val t = x / w
            val wobble = sin((t * shape.waves * 2 * PI + phase + shift).toFloat()) * 0.6f +
                sin((t * shape.waves * 5.3 * PI + phase * 1.7f).toFloat()) * 0.4f
            return h * (shape.base - shape.amplitude * scale * wobble)
        }
        fun landPath(scale: Float, shift: Float, lift: Float): Path = Path().apply {
            moveTo(0f, horizon)
            var x = 0f
            val step = w / 60f
            while (x <= w + step) {
                val y = (ridge(x, scale, shift) - lift).coerceAtMost(horizon)
                lineTo(x, y)
                x += step
            }
            lineTo(w, horizon)
            close()
        }
        drawPath(landPath(0.7f, 1.3f, h * 0.06f), FAR_LAND)
        if (shape.landmark != Landmark.ISLETS) drawPath(landPath(1f, 0f, 0f), NEAR_LAND)

        when (shape.landmark) {
            Landmark.ISLETS -> repeat(5) { k ->
                val cx = w * (0.1f + k * 0.2f)
                drawOval(NEAR_LAND, topLeft = Offset(cx - w * 0.08f, horizon - h * 0.05f), size = Size(w * 0.16f, h * 0.1f))
            }
            Landmark.LIGHTHOUSE -> {
                val x = w * 0.2f
                val top = ridge(x, 1f, 0f) - h * 0.16f
                drawRect(Color(0xFFDDE8E6), topLeft = Offset(x - w * 0.008f, top), size = Size(w * 0.016f, h * 0.16f))
                drawCircle(Plankton.copy(alpha = 0.2f), h * 0.08f, Offset(x, top))
                drawCircle(Plankton, h * 0.018f, Offset(x, top))
            }
            Landmark.ARCH -> {
                val cx = w * 0.62f
                val arch = Path().apply {
                    moveTo(cx - w * 0.12f, horizon)
                    lineTo(cx - w * 0.12f, horizon - h * 0.22f)
                    lineTo(cx + w * 0.12f, horizon - h * 0.22f)
                    lineTo(cx + w * 0.12f, horizon)
                    lineTo(cx + w * 0.05f, horizon)
                    cubicTo(cx + w * 0.05f, horizon - h * 0.14f, cx - w * 0.05f, horizon - h * 0.14f, cx - w * 0.05f, horizon)
                    close()
                }
                drawPath(arch, NEAR_LAND)
            }
            Landmark.PEAKS -> repeat(3) { k ->
                val cx = w * (0.25f + k * 0.28f)
                val peak = Path().apply {
                    moveTo(cx - w * 0.14f, horizon)
                    lineTo(cx, horizon - h * (0.38f + 0.06f * k))
                    lineTo(cx + w * 0.14f, horizon)
                    close()
                }
                drawPath(peak, FAR_LAND)
            }
            Landmark.BIG_WAVE -> {
                val wave = Path().apply {
                    moveTo(w * 0.55f, horizon)
                    cubicTo(w * 0.62f, horizon - h * 0.32f, w * 0.86f, horizon - h * 0.34f, w * 0.9f, horizon - h * 0.16f)
                    cubicTo(w * 0.84f, horizon - h * 0.2f, w * 0.8f, horizon - h * 0.12f, w * 0.86f, horizon)
                    close()
                }
                drawPath(wave, Brush.verticalGradient(listOf(Glow.copy(alpha = 0.35f), Color(0xFF0B2633)), startY = horizon - h * 0.34f, endY = horizon))
            }
            Landmark.RIDGE, Landmark.NONE -> Unit
        }

        // The 50 lights along the near shore, with a reflection for three stars.
        val count = lights.size.coerceAtLeast(1)
        for (i in lights.indices) {
            val x = w * (0.03f + 0.94f * (i + 0.5f) / count)
            val shoreY = if (shape.landmark == Landmark.ISLETS) horizon - h * 0.03f else ridge(x, 1f, 0f)
            val y = (shoreY + (horizon - shoreY) * (0.25f + 0.5f * ((i * 37) % 10) / 10f)).coerceAtMost(horizon - 1f)
            when (lights[i]) {
                0 -> drawCircle(Foam.copy(alpha = 0.12f), radius = 1.2f, center = Offset(x, y))
                1 -> {
                    drawCircle(Plankton.copy(alpha = 0.18f), radius = 6f, center = Offset(x, y))
                    drawCircle(Plankton.copy(alpha = 0.9f), radius = 2f, center = Offset(x, y))
                }
                else -> {
                    drawCircle(Glow.copy(alpha = 0.22f), radius = 9f, center = Offset(x, y))
                    drawCircle(Color.White, radius = 2.6f, center = Offset(x, y))
                    drawLine(
                        Glow.copy(alpha = 0.25f),
                        Offset(x, horizon + 2f),
                        Offset(x, horizon + (h - horizon) * 0.55f),
                        strokeWidth = 2f,
                    )
                }
            }
        }
    }
}

/** The panorama of [world] (0-based), as a composable. */
@Composable
fun CoastPanorama(world: Int, lights: IntArray, modifier: Modifier = Modifier) {
    val key = remember(lights.contentHashCode()) { lights.copyOf() }
    Canvas(modifier = modifier) { with(CoastArt) { drawCoast(world, key) } }
}
