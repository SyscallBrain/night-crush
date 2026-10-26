package pt.tiago.nightcrush.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.FloorFlags
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.rules.BoardSnapshot
import pt.tiago.nightcrush.game.AnimationTimeline
import pt.tiago.nightcrush.ui.art.PiecePalette
import pt.tiago.nightcrush.ui.art.PieceSprites
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Current
import pt.tiago.nightcrush.ui.theme.Deep
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef
import pt.tiago.nightcrush.ui.theme.Syne
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The whole board in one canvas: cells, floors, blockers, pieces, effects.
 * Sprites are pre-rendered and blitted. While a move animates, every frame is
 * drawn from the board snapshot of the current timeline phase, never from the
 * final state, so pieces only appear once they have actually arrived.
 */
@Composable
fun BoardCanvas(
    state: GameState,
    timeline: AnimationTimeline?,
    selected: Cell?,
    hint: Pair<Cell, Cell>?,
    style: PieceSprites.Style,
    palette: PiecePalette,
    reducedMotion: Boolean,
    contrastSymbols: Boolean = false,
    onCellTap: (Cell) -> Unit,
    onSwap: (Cell, Cell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sprites = remember { PieceSprites(context) }
    val measurer = rememberTextMeasurer()
    var clock by remember { mutableLongStateOf(0L) }
    var ambient by remember { mutableLongStateOf(0L) }
    val tap by rememberUpdatedState(onCellTap)
    val swap by rememberUpdatedState(onSwap)

    LaunchedEffect(timeline) {
        clock = 0L
        if (timeline == null) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (clock < timeline.totalMillis) {
            val now = withFrameNanos { it }
            clock = (now - start) / 1_000_000L
        }
    }
    // Only the hint pulses while idle, so an idle board does not redraw every frame.
    LaunchedEffect(hint, reducedMotion) {
        if (hint == null || reducedMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            ambient = (now - start) / 1_000_000L
        }
    }

    val width = state.width
    val height = state.height

    Canvas(
        modifier = modifier.pointerInput(width, height) {
            awaitEachGesture {
                val cell = min(size.width / width.toFloat(), size.height / height.toFloat())
                val originX = (size.width - cell * width) / 2f
                val originY = (size.height - cell * height) / 2f
                fun cellAt(position: Offset): Cell? {
                    val x = position.x - originX
                    val y = position.y - originY
                    if (x < 0f || y < 0f) return null
                    val c = (x / cell).toInt()
                    val r = (y / cell).toInt()
                    if (r !in 0 until height || c !in 0 until width) return null
                    return Cell(r, c)
                }
                val down = awaitFirstDown()
                val startCell = cellAt(down.position) ?: return@awaitEachGesture
                var triggered = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (triggered) continue
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (max(abs(dx), abs(dy)) > cell * 0.35f) {
                        val neighbor = if (abs(dx) > abs(dy)) {
                            Cell(startCell.r, startCell.c + if (dx > 0) 1 else -1)
                        } else {
                            Cell(startCell.r + if (dy > 0) 1 else -1, startCell.c)
                        }
                        if (neighbor.r in 0 until height && neighbor.c in 0 until width) {
                            swap(startCell, neighbor)
                        }
                        triggered = true
                    }
                }
                if (!triggered) tap(startCell)
            }
        },
    ) {
        val cell = min(size.width / width.toFloat(), size.height / height.toFloat())
        val originX = (size.width - cell * width) / 2f
        val originY = (size.height - cell * height) / 2f
        val geometry = Geometry(cell, originX, originY, width)
        val phase = timeline?.phaseAt(clock)
        val board: BoardSnapshot = phase?.board ?: timeline?.final ?: BoardSnapshot.of(state)

        val shake = if (reducedMotion) Offset.Zero else timeline?.shakes?.firstOrNull { clock in it.start..it.end }?.let {
            val progress = (clock - it.start).toFloat() / (it.end - it.start).coerceAtLeast(1)
            val amplitude = cell * 0.05f * it.strength * (1f - progress)
            Offset(kotlin.math.sin(progress * 40f) * amplitude, kotlin.math.cos(progress * 33f) * amplitude)
        } ?: Offset.Zero

        translate(shake.x, shake.y) {
            drawBoardBackground(state, geometry)
            drawFloors(state, board, geometry)
            drawBlockers(state, board, sprites, geometry)
            drawPieces(state, board, phase, clock, ambient, sprites, style, palette, geometry, selected, hint, contrastSymbols)
            if (timeline != null) drawEffects(timeline, clock, geometry, measurer)
        }
    }
}

private val SILT = Color(0xFF9C8450)
private val SILT_RIM = Color(0xFFE2C77E)

private class Geometry(val cell: Float, val originX: Float, val originY: Float, val columns: Int) {
    val sizePx: Int = cell.roundToInt()
    fun x(c: Float): Float = originX + c * cell
    fun y(r: Float): Float = originY + r * cell
}

private fun DrawScope.drawBoardBackground(state: GameState, g: Geometry) {
    val radius = g.cell * 0.18f
    for (r in 0 until state.height) {
        for (c in 0 until state.width) {
            if (!state.playable[state.idx(r, c)]) continue
            val topLeft = Offset(g.x(c.toFloat()) + g.cell * 0.02f, g.y(r.toFloat()) + g.cell * 0.02f)
            val size = Size(g.cell * 0.96f, g.cell * 0.96f)
            drawRoundRect(color = Deep, topLeft = topLeft, size = size, cornerRadius = CornerRadius(radius, radius))
            drawRoundRect(
                color = Reef.copy(alpha = 0.7f),
                topLeft = topLeft,
                size = size,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = g.cell * 0.02f),
            )
        }
    }
}

private fun DrawScope.drawFloors(state: GameState, board: BoardSnapshot, g: Geometry) {
    val cell = g.cell
    for (r in 0 until state.height) {
        for (c in 0 until state.width) {
            val i = state.idx(r, c)
            if (!state.playable[i]) continue
            val x = g.x(c.toFloat())
            val y = g.y(r.toFloat())
            val center = Offset(x + cell / 2, y + cell / 2)
            if (board.silt[i] > 0) {
                // Silt: a muddy gold bed with a dashed rim, clearly a mark to clear (also the puzzle marks).
                val layers = board.silt[i]
                drawRoundRect(
                    color = SILT.copy(alpha = 0.32f + 0.18f * layers),
                    topLeft = Offset(x + cell * 0.05f, y + cell * 0.05f),
                    size = Size(cell * 0.9f, cell * 0.9f),
                    cornerRadius = CornerRadius(cell * 0.16f, cell * 0.16f),
                )
                drawRoundRect(
                    color = SILT_RIM,
                    topLeft = Offset(x + cell * 0.08f, y + cell * 0.08f),
                    size = Size(cell * 0.84f, cell * 0.84f),
                    cornerRadius = CornerRadius(cell * 0.14f, cell * 0.14f),
                    style = Stroke(
                        width = cell * (if (layers > 1) 0.05f else 0.035f),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.1f, cell * 0.07f)),
                    ),
                )
            }
            if (board.floorFlags[i] and FloorFlags.GLOW != 0) {
                drawCircle(color = Plankton.copy(alpha = 0.26f), radius = cell * 0.46f, center = center)
            }
            if (board.floorFlags[i] and FloorFlags.EXIT != 0) {
                drawCircle(color = Current.copy(alpha = 0.8f), radius = cell * 0.24f, center = center, style = Stroke(width = cell * 0.05f))
            }
            if (state.isPortalIn(i) || state.isPortalOut(i)) {
                drawCircle(
                    color = Mist.copy(alpha = 0.7f),
                    radius = cell * 0.32f,
                    center = center,
                    style = Stroke(width = cell * 0.04f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.12f, cell * 0.1f))),
                )
            }
            if (state.conveyorNext[i] >= 0 || state.conveyorNext[i] == -2) {
                drawRoundRect(
                    color = Current.copy(alpha = 0.10f),
                    topLeft = Offset(x + cell * 0.02f, y + cell * 0.02f),
                    size = Size(cell * 0.96f, cell * 0.96f),
                    cornerRadius = CornerRadius(cell * 0.18f, cell * 0.18f),
                )
            }
        }
    }
}

private fun DrawScope.drawBlockers(state: GameState, board: BoardSnapshot, sprites: PieceSprites, g: Geometry) {
    val sizePx = g.sizePx
    for (r in 0 until state.height) {
        for (c in 0 until state.width) {
            val i = state.idx(r, c)
            val blocker = board.blockers[i]
            if (blocker == Blockers.NONE) continue
            val bitmap: ImageBitmap = when {
                blocker in 1..3 -> sprites.coralBlocker(sizePx, blocker)
                blocker == Blockers.OIL -> sprites.oilBlocker(sizePx)
                blocker == Blockers.SEAHORSE_BUBBLE -> sprites.bubble(sizePx)
                Blockers.isOyster(blocker) -> sprites.oyster(
                    sizePx,
                    PieceSprites.PiecePaletteSpan(Blockers.oysterColor(blocker), board.blockerData[i]),
                )
                blocker == Blockers.SINKHOLE -> sprites.sinkhole(sizePx)
                blocker == Blockers.OCTOPUS -> sprites.octopus(sizePx)
                else -> continue
            }
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(g.x(c.toFloat()).roundToInt(), g.y(r.toFloat()).roundToInt()),
                dstSize = IntSize(sizePx, sizePx),
            )
        }
    }
}

private fun DrawScope.drawPieces(
    state: GameState,
    board: BoardSnapshot,
    phase: AnimationTimeline.Phase?,
    clock: Long,
    ambient: Long,
    sprites: PieceSprites,
    style: PieceSprites.Style,
    palette: PiecePalette,
    g: Geometry,
    selected: Cell?,
    hint: Pair<Cell, Cell>?,
    contrastSymbols: Boolean,
) {
    val sizePx = g.sizePx
    val width = state.width

    /** Draws one piece centred on a (possibly fractional) cell position. */
    fun drawPiece(code: Int, r: Float, c: Float, puffer: Int, scale: Float = 1f, alpha: Float = 1f) {
        val sprite = sprites.pieceSprite(code, style, palette, sizePx) ?: return
        val scaled = (sizePx * scale).roundToInt().coerceAtLeast(1)
        val offset = IntOffset(
            (g.x(c) + (sizePx - scaled) / 2f).roundToInt(),
            (g.y(r) + (sizePx - scaled) / 2f).roundToInt(),
        )
        val dstSize = IntSize(scaled, scaled)
        drawImage(image = sprite, dstOffset = offset, dstSize = dstSize, alpha = alpha)
        val color = Pieces.colorOf(code)
        if (contrastSymbols && color >= 0) {
            drawContrastSymbol(color, Offset(offset.x + scaled / 2f, offset.y + scaled / 2f), scaled * 0.13f, alpha)
        }
        when {
            Pieces.isRay(code) ->
                drawImage(sprites.ray(style, Pieces.colorOf(code), sizePx, code in 7..12), dstOffset = offset, dstSize = dstSize, alpha = alpha)
            Pieces.isJelly(code) ->
                drawImage(sprites.jellyRing(sizePx, ((clock / 250) % 3).toInt()), dstOffset = offset, dstSize = dstSize, alpha = alpha)
            Pieces.isLanternFish(code) ->
                drawImage(sprites.fishFin(sizePx), dstOffset = offset, dstSize = dstSize, alpha = alpha)
            Pieces.isPuffer(code) ->
                drawImage(sprites.pufferOverlay(sizePx, puffer <= 2), dstOffset = offset, dstSize = dstSize, alpha = alpha)
        }
    }

    fun drawOverlay(overlay: Int, r: Int, c: Int) {
        if (overlay == Overlays.NONE) return
        val bitmap = if (Overlays.isBarnacle(overlay)) {
            sprites.barnacleOverlay(sizePx, Overlays.barnacleLayers(overlay))
        } else {
            sprites.netOverlay(sizePx)
        }
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(g.x(c.toFloat()).roundToInt(), g.y(r.toFloat()).roundToInt()),
            dstSize = IntSize(sizePx, sizePx),
        )
    }

    val swapPhase = phase as? AnimationTimeline.SwapPhase
    val clearPhase = phase as? AnimationTimeline.ClearPhase
    val fallPhase = phase as? AnimationTimeline.FallPhase

    // Resting pieces.
    for (r in 0 until state.height) {
        for (c in 0 until state.width) {
            val i = r * width + c
            if (swapPhase != null && (i == swapPhase.a || i == swapPhase.b)) {
                drawOverlay(board.overlays[i], r, c)
                continue
            }
            if (fallPhase != null && i in fallPhase.landing) continue
            val code = board.pieces[i]
            if (code != Pieces.EMPTY) {
                var scale = 1f
                if (phase == null) {
                    if (selected != null && selected.r == r && selected.c == c) scale = 1.14f
                    if (hint != null && (hint.first == Cell(r, c) || hint.second == Cell(r, c))) {
                        scale *= 0.92f + 0.12f * kotlin.math.sin((ambient % 900) / 900f * 6.28318f)
                    }
                }
                if (clearPhase != null && i in clearPhase.created) {
                    scale *= 0.6f + 0.4f * clearPhase.progress(clock)
                }
                drawPiece(code, r.toFloat(), c.toFloat(), board.pieceData[i], scale)
            }
            drawOverlay(board.overlays[i], r, c)
        }
    }

    when {
        swapPhase != null -> {
            val p = swapPhase.progress(clock)
            // A rejected swap travels halfway and returns; an accepted one completes.
            val t = if (swapPhase.rejected) (if (p < 0.5f) p else 1f - p) else p
            val eased = t * t * (3f - 2f * t)
            val ar = (swapPhase.a / width).toFloat(); val ac = (swapPhase.a % width).toFloat()
            val br = (swapPhase.b / width).toFloat(); val bc = (swapPhase.b % width).toFloat()
            val codeA = board.pieces[swapPhase.a]
            val codeB = board.pieces[swapPhase.b]
            if (codeB != Pieces.EMPTY) drawPiece(codeB, br + (ar - br) * eased, bc + (ac - bc) * eased, board.pieceData[swapPhase.b])
            if (codeA != Pieces.EMPTY) drawPiece(codeA, ar + (br - ar) * eased, ac + (bc - ac) * eased, board.pieceData[swapPhase.a], scale = 1.06f)
        }
        clearPhase != null -> {
            val p = clearPhase.progress(clock)
            for (cleared in clearPhase.cleared) {
                drawPiece(
                    cleared.code,
                    (cleared.cell / width).toFloat(),
                    (cleared.cell % width).toFloat(),
                    puffer = 9,
                    scale = 1f + 0.4f * p,
                    alpha = (1f - p).coerceIn(0f, 1f),
                )
            }
        }
        fallPhase != null -> {
            val position = FloatArray(2)
            val elapsed = clock - fallPhase.start
            // New pieces come from above the first playable cell of their column;
            // they fade in as they cross its edge instead of floating over the void.
            val topRow = IntArray(width) { c -> (0 until state.height).firstOrNull { state.playable[it * width + c] } ?: 0 }
            for (mover in fallPhase.movers) {
                val code = board.pieces[mover.to]
                if (code == Pieces.EMPTY) continue
                val local = (elapsed.toFloat() / mover.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
                // Accelerate like a falling body, then rest.
                mover.positionAt(local * local, position)
                val column = position[1].roundToInt().coerceIn(0, width - 1)
                val above = topRow[column] - position[0]
                val alpha = if (above <= 0f) 1f else (1f - above * 1.4f).coerceIn(0f, 1f)
                if (alpha <= 0f) continue
                drawPiece(code, position[0], position[1], board.pieceData[mover.to], alpha = alpha)
                drawOverlay(board.overlays[mover.to], position[0].roundToInt(), position[1].roundToInt())
            }
        }
    }
}

private fun DrawScope.drawEffects(timeline: AnimationTimeline, clock: Long, g: Geometry, measurer: TextMeasurer) {
    val cell = g.cell
    val columns = g.columns
    fun center(index: Int) = Offset(g.x((index % columns).toFloat()) + cell / 2, g.y((index / columns).toFloat()) + cell / 2)
    for (beam in timeline.beams) {
        if (clock !in beam.start..beam.end) continue
        val p = ((clock - beam.start).toFloat() / (beam.end - beam.start).coerceAtLeast(1)).coerceIn(0f, 1f)
        for (index in beam.cells) {
            drawCircle(color = Glow.copy(alpha = (1f - p) * 0.7f), radius = cell * 0.4f * (1f - p * 0.4f), center = center(index))
        }
    }
    for (ring in timeline.rings) {
        if (clock !in ring.start..ring.end) continue
        val p = ((clock - ring.start).toFloat() / (ring.end - ring.start).coerceAtLeast(1)).coerceIn(0f, 1f)
        drawCircle(
            color = Glow.copy(alpha = (1f - p) * 0.5f),
            radius = cell * (if (ring.big) 2.2f else 1.6f) * p,
            center = center(ring.cell),
            style = Stroke(width = cell * 0.06f),
        )
    }
    for (float in timeline.floats) {
        if (clock !in float.start..float.end) continue
        val p = ((clock - float.start).toFloat() / (float.end - float.start).coerceAtLeast(1)).coerceIn(0f, 1f)
        val at = center(float.cell)
        drawText(
            textMeasurer = measurer,
            text = float.text,
            topLeft = Offset(at.x - cell * 0.4f, at.y - cell * 0.8f - cell * 0.5f * p),
            style = TextStyle(color = Foam.copy(alpha = 1f - p), fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
    // Only the newest combo word shows; stacking them made unreadable overlaps.
    val word = timeline.words.filter { clock in it.start..it.end }.maxByOrNull { it.start } ?: return
    val p = ((clock - word.start).toFloat() / (word.end - word.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    val pop = if (p < 0.15f) 0.4f + 0.68f * (p / 0.15f) else 1.08f - 0.08f * ((p - 0.15f) / 0.85f)
    val style = TextStyle(
        color = Glow,
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = Syne,
        shadow = Shadow(color = Abyss, offset = Offset.Zero, blurRadius = 18f),
    )
    var layout = measurer.measure(word.text, style = style)
    // Shrink long words (e.g. "Chuva de raias!") so they always fit the board.
    val fit = (size.width * 0.9f / layout.size.width).coerceAtMost(1f)
    val scale = pop * fit
    layout = measurer.measure(word.text, style = style.copy(fontSize = (34 * scale).sp))
    val boardTop = g.y(0f)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            (size.width - layout.size.width) / 2f,
            boardTop + (size.height - 2 * boardTop) * 0.42f - layout.size.height / 2f - p * cell,
        ),
        alpha = (1f - p * 0.9f).coerceIn(0f, 1f),
    )
}

/**
 * A black-and-white mark inside each colour (dot, bar, plus, two dots, triangle,
 * cross), for players who cannot rely on colour or on the glow of the shapes.
 */
private fun DrawScope.drawContrastSymbol(color: Int, center: Offset, r: Float, alpha: Float) {
    val outline = Stroke(width = r * 0.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    val inner = Stroke(width = r * 0.45f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    fun mark(stroke: Stroke, tint: Color) {
        when (color) {
            0 -> drawCircle(tint, radius = r * 0.55f + if (stroke === outline) r * 0.3f else 0f, center = center, alpha = alpha)
            1 -> drawLine(tint, Offset(center.x - r, center.y), Offset(center.x + r, center.y), stroke.width, stroke.cap, alpha = alpha)
            2 -> {
                drawLine(tint, Offset(center.x - r, center.y), Offset(center.x + r, center.y), stroke.width, stroke.cap, alpha = alpha)
                drawLine(tint, Offset(center.x, center.y - r), Offset(center.x, center.y + r), stroke.width, stroke.cap, alpha = alpha)
            }
            3 -> for (dx in floatArrayOf(-0.6f, 0.6f)) {
                drawCircle(tint, radius = r * 0.32f + if (stroke === outline) r * 0.25f else 0f, center = Offset(center.x + dx * r, center.y), alpha = alpha)
            }
            4 -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x, center.y - r)
                    lineTo(center.x + r, center.y + r * 0.8f)
                    lineTo(center.x - r, center.y + r * 0.8f)
                    close()
                }
                drawPath(path, tint, style = Stroke(width = stroke.width, join = androidx.compose.ui.graphics.StrokeJoin.Round), alpha = alpha)
            }
            else -> {
                drawLine(tint, Offset(center.x - r, center.y - r), Offset(center.x + r, center.y + r), stroke.width, stroke.cap, alpha = alpha)
                drawLine(tint, Offset(center.x - r, center.y + r), Offset(center.x + r, center.y - r), stroke.width, stroke.cap, alpha = alpha)
            }
        }
    }
    mark(outline, Color.Black)
    mark(inner, Color.White)
}
