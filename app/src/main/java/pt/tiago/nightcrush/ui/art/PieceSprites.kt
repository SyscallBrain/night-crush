package pt.tiago.nightcrush.ui.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pre-rendered piece and blocker sprites.
 *
 * Everything bioluminescent (radial fill, outline, light core, halo) is painted
 * once per (style, colour, size) into a software Bitmap; the board then only
 * blits. No RenderEffect and no per-frame blur, so API 26 works and the frame
 * budget stays intact.
 */
class PieceSprites(private val context: Context) {

    enum class Style { BIO, GEM, CANDY, NEON }

    class Key(
        val style: Style,
        val palette: PiecePalette,
        val color: Int,
        val sizePx: Int,
    ) {
        override fun equals(other: Any?): Boolean =
            other is Key && other.style == style && other.palette.id == palette.id &&
                other.color == color && other.sizePx == sizePx

        override fun hashCode(): Int {
            var hash = style.ordinal
            hash = hash * 31 + palette.id.hashCode()
            hash = hash * 31 + color
            hash = hash * 31 + sizePx
            return hash
        }
    }

    private val cache = HashMap<Key, ImageBitmap>()
    private val specialCache = HashMap<String, ImageBitmap>()

    fun piece(style: Style, palette: PiecePalette, color: Int, sizePx: Int): ImageBitmap =
        cache.getOrPut(Key(style, palette, color, sizePx)) {
            renderPiece(style, color, palette.colors[color], sizePx)
        }

    /** Decorations are drawn on top of the piece sprite. */
    fun ray(style: Style, color: Int, sizePx: Int, horizontal: Boolean): ImageBitmap {
        val key = "ray:${style.ordinal}:$color:$sizePx:$horizontal"
        return specialCache.getOrPut(key) { renderRay(style, paletteColor(color), sizePx, horizontal) }
    }

    fun jellyRing(sizePx: Int, alphaStep: Int): ImageBitmap {
        val key = "jelly:$sizePx:$alphaStep"
        return specialCache.getOrPut(key) { renderJellyRing(sizePx, alphaStep) }
    }

    fun fishFin(sizePx: Int): ImageBitmap = specialCache.getOrPut("fishfin:$sizePx") { renderFishFin(sizePx) }

    fun pearl(sizePx: Int, palette: PiecePalette): ImageBitmap {
        val key = "pearl:$sizePx:${palette.id}"
        return specialCache.getOrPut(key) { renderPearl(palette, sizePx) }
    }

    fun lantern(sizePx: Int): ImageBitmap = specialCache.getOrPut("lantern:$sizePx") { renderLantern(sizePx) }

    fun bubble(sizePx: Int): ImageBitmap = specialCache.getOrPut("bubble:$sizePx") { renderBubble(sizePx) }

    fun netOverlay(sizePx: Int): ImageBitmap = specialCache.getOrPut("net:$sizePx") { renderNet(sizePx) }

    fun barnacleOverlay(sizePx: Int, layers: Int): ImageBitmap =
        specialCache.getOrPut("barnacle:$sizePx:$layers") { renderBarnacle(sizePx, layers) }

    fun coralBlocker(sizePx: Int, layers: Int): ImageBitmap =
        specialCache.getOrPut("coral:$sizePx:$layers") { renderCoral(sizePx, layers) }

    fun oilBlocker(sizePx: Int): ImageBitmap = specialCache.getOrPut("oil:$sizePx") { renderOil(sizePx) }

    fun oyster(sizePx: Int, color: PiecePaletteSpan): ImageBitmap {
        val key = "oyster:$sizePx:${color.color}:${color.counter}"
        return specialCache.getOrPut(key) { renderOyster(sizePx, color) }
    }

    fun sinkhole(sizePx: Int): ImageBitmap = specialCache.getOrPut("sinkhole:$sizePx") { renderSinkhole(sizePx) }

    fun octopus(sizePx: Int): ImageBitmap = specialCache.getOrPut("octopus:$sizePx") { renderOctopus(sizePx) }

    fun pufferOverlay(sizePx: Int, urgent: Boolean): ImageBitmap =
        specialCache.getOrPut("puffer:$sizePx:$urgent") { renderPuffer(sizePx, urgent) }

    private fun paletteColor(color: Int): Int = PiecePalettes.normal.colors[color]

    /** 0 = empty board cell, otherwise a blittable sprite. */
    fun pieceSprite(
        code: Int,
        style: Style,
        palette: PiecePalette,
        sizePx: Int,
    ): ImageBitmap? {
        val color = Pieces.colorOf(code)
        if (color < 0) {
            return when {
                code == Pieces.LANTERN -> lantern(sizePx)
                code == Pieces.PEARL -> pearl(sizePx, palette)
                else -> null
            }
        }
        return piece(style, palette, color, sizePx)
    }

    data class PiecePaletteSpan(val color: Int, val counter: Int)

    // ------------------------------------------------------------- rendering

    /** [colorIndex] picks the shape, [color] (ARGB) the paint. */
    private fun renderPiece(style: Style, colorIndex: Int, color: Int, sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = pathFor(colorIndex, sizePx)
        when (style) {
            Style.BIO -> paintBioluminescent(canvas, path, color, sizePx)
            Style.GEM -> paintGem(canvas, path, color, sizePx)
            Style.CANDY -> paintCandy(canvas, path, color, sizePx)
            Style.NEON -> paintNeon(canvas, path, color, sizePx)
        }
        return bitmap.asImageBitmap()
    }

    private fun paintBioluminescent(canvas: Canvas, path: Path, color: Int, sizePx: Int) {
        val center = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Halo (outer glow).
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(color, 70)
        paint.maskFilter = BlurMaskFilter(sizePx * 0.09f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)
        paint.maskFilter = null

        // Radial gradient body: centre whitened, border translucent.
        val light = mix(color, Color.WHITE, 0.65f)
        val mid = withAlpha(color, 140)
        val edge = withAlpha(color, 46)
        paint.shader = RadialGradient(
            center, center, sizePx * 0.52f,
            intArrayOf(withAlpha(light, 242), mid, edge),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        // Outline at 75%.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.025f
        paint.color = withAlpha(color, 191)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL

        // Light core, soft white dot.
        paint.color = withAlpha(Color.WHITE, 153)
        paint.maskFilter = BlurMaskFilter(sizePx * 0.05f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(center * 0.82f, center * 0.82f, sizePx * 0.12f, paint)
        paint.maskFilter = null
    }

    private fun paintGem(canvas: Canvas, path: Path, color: Int, sizePx: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
            intArrayOf(mix(color, Color.WHITE, 0.45f), color, mix(color, Color.BLACK, 0.35f)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.02f
        paint.color = withAlpha(Color.WHITE, 110)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.WHITE, 170)
        canvas.drawCircle(sizePx * 0.36f, sizePx * 0.32f, sizePx * 0.06f, paint)
    }

    private fun paintCandy(canvas: Canvas, path: Path, color: Int, sizePx: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            sizePx * 0.35f, sizePx * 0.3f, sizePx * 0.75f,
            intArrayOf(mix(color, Color.WHITE, 0.6f), color, mix(color, Color.BLACK, 0.2f)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.WHITE, 200)
        canvas.drawCircle(sizePx * 0.32f, sizePx * 0.28f, sizePx * 0.09f, paint)
    }

    private fun paintNeon(canvas: Canvas, path: Path, color: Int, sizePx: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.18f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.maskFilter = BlurMaskFilter(sizePx * 0.08f, BlurMaskFilter.Blur.NORMAL)
        paint.color = withAlpha(color, 200)
        canvas.drawPath(path, paint)
        paint.maskFilter = null
        paint.strokeWidth = sizePx * 0.1f
        paint.color = mix(color, Color.WHITE, 0.5f)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.05f, paint)
    }

    private fun renderRay(style: Style, color: Int, sizePx: Int, horizontal: Boolean): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.06f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = withAlpha(Color.WHITE, 220)
        paint.maskFilter = BlurMaskFilter(sizePx * 0.03f, BlurMaskFilter.Blur.NORMAL)
        val mid = sizePx / 2f
        val offset = sizePx * 0.14f
        if (horizontal) {
            canvas.drawLine(sizePx * 0.28f, mid - offset, sizePx * 0.72f, mid - offset, paint)
            canvas.drawLine(sizePx * 0.28f, mid + offset, sizePx * 0.72f, mid + offset, paint)
        } else {
            canvas.drawLine(mid - offset, sizePx * 0.28f, mid - offset, sizePx * 0.72f, paint)
            canvas.drawLine(mid + offset, sizePx * 0.28f, mid + offset, sizePx * 0.72f, paint)
        }
        return bitmap.asImageBitmap()
    }

    private fun renderJellyRing(sizePx: Int, alphaStep: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.06f
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(sizePx * 0.12f, sizePx * 0.08f), 0f)
        val alpha = (255 - alphaStep * 40).coerceIn(60, 255)
        paint.color = withAlpha(Color.WHITE, alpha)
        val inset = sizePx * 0.18f
        canvas.drawOval(RectF(inset, inset, sizePx - inset, sizePx - inset), paint)
        return bitmap.asImageBitmap()
    }

    private fun renderFishFin(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = withAlpha(Color.WHITE, 210)
        val mid = sizePx / 2f
        for (side in intArrayOf(-1, 1)) {
            val path = Path()
            path.moveTo(mid, mid)
            path.lineTo(mid + side * sizePx * 0.3f, mid - sizePx * 0.22f)
            path.lineTo(mid + side * sizePx * 0.3f, mid + sizePx * 0.22f)
            path.close()
            canvas.drawPath(path, paint)
        }
        return bitmap.asImageBitmap()
    }

    private fun renderPearl(palette: PiecePalette, sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = sizePx / 2f
        paint.color = Color.rgb(6, 18, 25)
        canvas.drawCircle(center, center, sizePx * 0.40f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.035f
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60).toDouble())
            paint.color = palette.colors[i]
            canvas.drawArc(
                RectF(
                    center - sizePx * 0.30f, center - sizePx * 0.30f,
                    center + sizePx * 0.30f, center + sizePx * 0.30f,
                ),
                (i * 60).toFloat(), 70f, false, paint,
            )
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                center + cos(angle).toFloat() * sizePx * 0.36f,
                center + sin(angle).toFloat() * sizePx * 0.36f,
                sizePx * 0.05f, paint,
            )
            paint.style = Paint.Style.STROKE
        }
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.WHITE, 180)
        canvas.drawCircle(center - sizePx * 0.12f, center - sizePx * 0.12f, sizePx * 0.07f, paint)
        return bitmap.asImageBitmap()
    }

    private fun renderLantern(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = sizePx / 2f
        paint.color = withAlpha(Color.WHITE, 90)
        paint.maskFilter = BlurMaskFilter(sizePx * 0.1f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(center, center, sizePx * 0.32f, paint)
        paint.maskFilter = null
        paint.color = Color.rgb(242, 232, 109)
        canvas.drawRoundRect(
            RectF(center - sizePx * 0.18f, center - sizePx * 0.28f, center + sizePx * 0.18f, center + sizePx * 0.26f),
            sizePx * 0.06f, sizePx * 0.06f, paint,
        )
        paint.color = Color.rgb(134, 174, 180)
        canvas.drawRect(
            RectF(center - sizePx * 0.22f, center - sizePx * 0.34f, center + sizePx * 0.22f, center - sizePx * 0.26f),
            paint,
        )
        paint.color = withAlpha(Color.WHITE, 210)
        canvas.drawCircle(center, center, sizePx * 0.07f, paint)
        return bitmap.asImageBitmap()
    }

    private fun renderBubble(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = sizePx / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.03f
        paint.color = withAlpha(Color.WHITE, 190)
        canvas.drawCircle(center, center, sizePx * 0.38f, paint)
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.rgb(90, 176, 255), 60)
        canvas.drawCircle(center, center, sizePx * 0.37f, paint)
        // Little seahorse silhouette.
        paint.color = withAlpha(Color.WHITE, 220)
        val path = Path()
        path.moveTo(center - sizePx * 0.05f, center - sizePx * 0.16f)
        path.lineTo(center + sizePx * 0.08f, center - sizePx * 0.12f)
        path.lineTo(center + sizePx * 0.1f, center + sizePx * 0.05f)
        path.lineTo(center, center + sizePx * 0.18f)
        path.lineTo(center - sizePx * 0.08f, center + sizePx * 0.05f)
        path.close()
        canvas.drawPath(path, paint)
        return bitmap.asImageBitmap()
    }

    private fun renderNet(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.02f
        paint.color = withAlpha(Color.rgb(134, 174, 180), 210)
        val step = sizePx / 4f
        for (k in 1..3) {
            canvas.drawLine(0f, k * step, sizePx.toFloat(), k * step, paint)
            canvas.drawLine(k * step, 0f, k * step, sizePx.toFloat(), paint)
        }
        return bitmap.asImageBitmap()
    }

    private fun renderBarnacle(sizePx: Int, layers: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = withAlpha(Color.rgb(227, 251, 246), 60 + layers * 30)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.03f
        val center = sizePx / 2f
        for (k in 0 until 3 + layers) {
            val angle = k * 2.39
            val x = center + cos(angle).toFloat() * sizePx * 0.3f
            val y = center + sin(angle).toFloat() * sizePx * 0.3f
            canvas.drawCircle(x, y, sizePx * 0.07f, paint)
            canvas.drawCircle(x, y, sizePx * 0.035f, paint)
        }
        return bitmap.asImageBitmap()
    }

    private fun renderCoral(sizePx: Int, layers: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(155, 94, 84)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = sizePx * (0.06f + layers * 0.01f)
        val base = sizePx * 0.78f
        for (k in 0 until 3 + layers) {
            val x = sizePx * (0.24f + k * 0.18f)
            canvas.drawLine(x, base, x, sizePx * 0.34f, paint)
            canvas.drawLine(x, sizePx * 0.5f, x - sizePx * 0.07f, sizePx * 0.38f, paint)
            canvas.drawLine(x, sizePx * 0.58f, x + sizePx * 0.07f, sizePx * 0.44f, paint)
        }
        return bitmap.asImageBitmap()
    }

    private fun renderOil(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            sizePx / 2f, sizePx / 2f, sizePx * 0.5f,
            intArrayOf(withAlpha(Color.rgb(24, 20, 43), 240), withAlpha(Color.rgb(12, 10, 24), 220)),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.44f, paint)
        paint.shader = null
        paint.color = withAlpha(Color.rgb(197, 139, 255), 120)
        canvas.drawCircle(sizePx * 0.38f, sizePx * 0.4f, sizePx * 0.07f, paint)
        return bitmap.asImageBitmap()
    }

    private fun renderOyster(sizePx: Int, span: PiecePaletteSpan): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(120, 108, 100)
        canvas.drawOval(RectF(0f, sizePx * 0.2f, sizePx.toFloat(), sizePx * 0.9f), paint)
        paint.color = withAlpha(PiecePalettes.normal.colors[span.color], 210)
        canvas.drawOval(RectF(sizePx * 0.1f, sizePx * 0.3f, sizePx * 0.9f, sizePx * 0.8f), paint)
        if (span.counter < 3) {
            paint.color = withAlpha(Color.WHITE, 200)
            repeat(3 - span.counter) {
                canvas.drawCircle(sizePx * (0.3f + it * 0.2f), sizePx * 0.55f, sizePx * 0.05f, paint)
            }
        }
        return bitmap.asImageBitmap()
    }

    private fun renderSinkhole(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            sizePx / 2f, sizePx / 2f, sizePx * 0.5f,
            intArrayOf(Color.BLACK, withAlpha(Color.rgb(6, 18, 25), 255)),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.46f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.03f
        paint.color = withAlpha(Color.rgb(90, 176, 255), 150)
        canvas.drawArc(RectF(sizePx * 0.12f, sizePx * 0.12f, sizePx * 0.88f, sizePx * 0.88f), 0f, 260f, false, paint)
        return bitmap.asImageBitmap()
    }

    private fun renderOctopus(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(145, 90, 160)
        canvas.drawCircle(sizePx * 0.5f, sizePx * 0.42f, sizePx * 0.26f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.06f
        paint.strokeCap = Paint.Cap.ROUND
        for (k in 0 until 5) {
            val start = sizePx * (0.24f + k * 0.13f)
            canvas.drawLine(start, sizePx * 0.6f, start - sizePx * 0.04f, sizePx * 0.82f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.WHITE, 220)
        canvas.drawCircle(sizePx * 0.42f, sizePx * 0.38f, sizePx * 0.05f, paint)
        canvas.drawCircle(sizePx * 0.58f, sizePx * 0.38f, sizePx * 0.05f, paint)
        return bitmap.asImageBitmap()
    }

    private fun renderPuffer(sizePx: Int, urgent: Boolean): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.04f
        paint.color = if (urgent) Color.rgb(255, 138, 122) else withAlpha(Color.rgb(255, 138, 122), 200)
        val spikes = 12
        for (k in 0 until spikes) {
            val angle = Math.toRadians((k * 360.0 / spikes))
            val r0 = sizePx * 0.34f
            val r1 = sizePx * 0.46f
            canvas.drawLine(
                sizePx / 2f + cos(angle).toFloat() * r0,
                sizePx / 2f + sin(angle).toFloat() * r0,
                sizePx / 2f + cos(angle).toFloat() * r1,
                sizePx / 2f + sin(angle).toFloat() * r1,
                paint,
            )
        }
        return bitmap.asImageBitmap()
    }

    // --------------------------------------------------------------- geometry

    /** Shape paths per colour index, in PieceColor order. */
    private fun pathFor(colorIndex: Int, sizePx: Int): Path {
        val p = Path()
        val s = sizePx / 100f
        fun pt(x: Float, y: Float) = Pair(x * s, y * s)
        when (colorIndex) {
            0 -> { // circle
                p.addCircle(50f * s, 50f * s, 38f * s, Path.Direction.CW)
            }
            1 -> { // five point star
                val cx = 50f
                val cy = 52f
                val outer = 42f
                val inner = 18f
                for (k in 0 until 10) {
                    val radius = if (k % 2 == 0) outer else inner
                    val angle = Math.toRadians((-90 + k * 36).toDouble())
                    val (x, y) = pt(cx + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius)
                    if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            2 -> { // crescent moon: the big circle minus an offset circle
                val outer = Path().apply {
                    addCircle(50f * s, 52f * s, 42f * s, Path.Direction.CW)
                }
                val cut = Path().apply {
                    addCircle(66f * s, 40f * s, 36f * s, Path.Direction.CW)
                }
                p.op(outer, cut, Path.Op.DIFFERENCE)
            }
            3 -> { // diamond
                p.moveTo(50f * s, 9f * s)
                p.lineTo(89f * s, 50f * s)
                p.lineTo(50f * s, 91f * s)
                p.lineTo(11f * s, 50f * s)
                p.close()
            }
            4 -> { // hexagon
                for (k in 0 until 6) {
                    val angle = Math.toRadians((-90 + k * 60).toDouble())
                    val (x, y) = pt(50f + cos(angle).toFloat() * 41f, 52f + sin(angle).toFloat() * 41f)
                    if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            else -> { // coral heart
                p.moveTo(50f * s, 86f * s)
                p.cubicTo(22f * s, 66f * s, 9f * s, 48f * s, 14f * s, 31f * s)
                p.cubicTo(20f * s, 14f * s, 41f * s, 12f * s, 50f * s, 28f * s)
                p.cubicTo(59f * s, 12f * s, 80f * s, 14f * s, 86f * s, 31f * s)
                p.cubicTo(91f * s, 48f * s, 78f * s, 66f * s, 50f * s, 86f * s)
                p.close()
            }
        }
        return p
    }

    companion object {
        fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

        fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) * (1 - t) + Color.red(b) * t).toInt().coerceIn(0, 255),
            (Color.green(a) * (1 - t) + Color.green(b) * t).toInt().coerceIn(0, 255),
            (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt().coerceIn(0, 255),
        )
    }
}
