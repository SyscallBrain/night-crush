package pt.tiago.nightcrush.game

import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.SpecialKind
import pt.tiago.nightcrush.core.rules.BoardSnapshot
import pt.tiago.nightcrush.core.rules.GameEvent
import pt.tiago.nightcrush.core.rules.MoveResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns the engine's event list into timed phases the board canvas can draw.
 *
 * The engine emits a [BoardSnapshot] around every gravity pass, so each phase
 * knows the exact board it starts from and the board it ends on: a swap, the
 * clears of one cascade step, or the falls of one cascade step. Pieces in the
 * same fall phase move together. The engine result never depends on this.
 */
class AnimationTimeline(
    val totalMillis: Long,
    val phases: List<Phase>,
    val final: BoardSnapshot,
    val beams: List<BeamTrack>,
    val rings: List<RingTrack>,
    val floats: List<FloatTrack>,
    val shakes: List<ShakeTrack>,
    val words: List<WordTrack>,
) {
    sealed class Phase(val start: Long, val end: Long, val board: BoardSnapshot) {
        fun progress(clock: Long): Float =
            ((clock - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /** Two pieces trade places; a rejected swap goes halfway and comes back. */
    class SwapPhase(start: Long, end: Long, board: BoardSnapshot, val a: Int, val b: Int, val rejected: Boolean) :
        Phase(start, end, board)

    /** [board] is the result of the clears; [cleared] fade out over it and [created] pop in. */
    class ClearPhase(start: Long, end: Long, board: BoardSnapshot, val cleared: List<Cleared>, val created: Set<Int>) :
        Phase(start, end, board)

    /** [board] is where everything lands; [movers] travel there along their paths. */
    class FallPhase(start: Long, end: Long, board: BoardSnapshot, val movers: List<Mover>) :
        Phase(start, end, board) {
        val landing: Set<Int> = movers.mapTo(HashSet()) { it.to }
    }

    /** Anything else that changed (belts, oil, shuffle): shown as it ends up. */
    class SettlePhase(start: Long, end: Long, board: BoardSnapshot) : Phase(start, end, board)

    class Cleared(val cell: Int, val code: Int)

    /**
     * One piece in motion. [rows] and [cols] are the corners of its path in
     * board units (rows above the board are negative); [duration] is its own
     * travel time from the start of the phase.
     */
    class Mover(val to: Int, val rows: FloatArray, val cols: FloatArray, val duration: Long) {
        private val lengths = FloatArray(rows.size)
        val length: Float

        init {
            var total = 0f
            for (i in 1 until rows.size) {
                val dr = rows[i] - rows[i - 1]
                val dc = cols[i] - cols[i - 1]
                total += sqrt(dr * dr + dc * dc)
                lengths[i] = total
            }
            length = total
        }

        /** Position along the path at [t] in 0..1, written into [out] as (row, col). */
        fun positionAt(t: Float, out: FloatArray) {
            if (rows.size == 1 || length <= 0f) {
                out[0] = rows.last(); out[1] = cols.last(); return
            }
            val target = length * t
            for (i in 1 until rows.size) {
                if (target <= lengths[i] || i == rows.size - 1) {
                    val segment = (lengths[i] - lengths[i - 1]).coerceAtLeast(1e-4f)
                    val f = ((target - lengths[i - 1]) / segment).coerceIn(0f, 1f)
                    out[0] = rows[i - 1] + (rows[i] - rows[i - 1]) * f
                    out[1] = cols[i - 1] + (cols[i] - cols[i - 1]) * f
                    return
                }
            }
        }
    }

    class BeamTrack(val cells: List<Int>, val start: Long, val end: Long)
    class RingTrack(val cell: Int, val start: Long, val end: Long, val big: Boolean)
    class FloatTrack(val cell: Int, val text: String, val start: Long, val end: Long)
    class ShakeTrack(val start: Long, val end: Long, val strength: Float)
    class WordTrack(val text: String, val start: Long, val end: Long)

    /** The phase on screen at [clock], or null once the timeline is over. */
    fun phaseAt(clock: Long): Phase? {
        if (clock >= totalMillis) return null
        for (phase in phases) if (clock < phase.end) return phase
        return null
    }

    companion object {
        const val SWAP_MS = 160L
        const val REJECT_MS = 220L
        const val CLEAR_MS = 200L
        const val BEAM_MS = 240L
        const val RING_MS = 260L
        const val FISH_MS = 380L
        const val PEARL_MS = 420L
        const val FALL_BASE_MS = 60L
        const val FALL_PER_CELL_MS = 45L
        const val SETTLE_MS = 140L
        const val WORD_MS = 1100L
        const val FLOAT_MS = 900L

        /** After this much animation the rest of a long cascade is skipped to the end. */
        const val BUDGET_MS = 4_000L

        fun cascadeWord(index: Int, words: List<String>): String? = when {
            index <= 1 -> null
            index == 2 -> words[0]
            index == 3 -> words[1]
            index == 4 -> words[2]
            else -> words[3]
        }

        fun build(
            pre: GameState,
            result: MoveResult,
            speed: Float,
            words: List<String>,
            formatPoints: (Int) -> String,
            reducedMotion: Boolean = false,
        ): AnimationTimeline {
            val baseScale = (1f / speed.coerceIn(0.5f, 3f)) * (if (reducedMotion) 0.5f else 1f)
            val phases = ArrayList<Phase>()
            val beams = ArrayList<BeamTrack>()
            val rings = ArrayList<RingTrack>()
            val floats = ArrayList<FloatTrack>()
            val shakes = ArrayList<ShakeTrack>()
            val wordTracks = ArrayList<WordTrack>()
            val width = pre.width

            var current = BoardSnapshot.of(pre)
            val final = BoardSnapshot.of(result.state)
            var clock = 0L
            var cascade = 1
            val segment = ArrayList<GameEvent>()

            // Later cascade steps run faster so long chains stay watchable.
            fun scale(): Float = baseScale / (1f + 0.15f * (cascade - 1)).coerceAtMost(2.2f)
            fun ms(value: Long): Long = (value * scale()).toLong().coerceAtLeast(16L)

            fun closeSegment(next: BoardSnapshot) {
                val events = segment.toList()
                segment.clear()
                if (clock >= BUDGET_MS) {
                    current = next
                    return
                }
                val swap = events.firstOrNull { it is GameEvent.SwapStarted } as GameEvent.SwapStarted?
                val start = clock
                when {
                    swap != null -> {
                        val a = pre.idx(swap.a.r, swap.a.c)
                        val b = pre.idx(swap.b.r, swap.b.c)
                        clock += ms(SWAP_MS)
                        phases.add(SwapPhase(start, clock, current, a, b, rejected = false))
                    }
                    events.any { it is GameEvent.Fell || it is GameEvent.Slid || it is GameEvent.Spawned } -> {
                        val movers = buildMovers(events, width, ::ms)
                        val longest = movers.maxOfOrNull { it.duration } ?: ms(FALL_BASE_MS)
                        clock += longest
                        phases.add(FallPhase(start, clock, next, movers))
                    }
                    events.any {
                        it is GameEvent.PieceCleared || it is GameEvent.SpecialActivated ||
                            it is GameEvent.ComboActivated || it is GameEvent.LayerDamaged ||
                            it is GameEvent.SpecialCreated || it is GameEvent.SiltCleared
                    } -> {
                        var duration = ms(CLEAR_MS)
                        val cleared = ArrayList<Cleared>()
                        val created = HashSet<Int>()
                        for (event in events) {
                            when (event) {
                                is GameEvent.PieceCleared ->
                                    cleared.add(Cleared(pre.idx(event.cell.r, event.cell.c), event.code))
                                is GameEvent.SpecialCreated -> created.add(pre.idx(event.cell.r, event.cell.c))
                                is GameEvent.SpecialActivated -> {
                                    val cell = pre.idx(event.cell.r, event.cell.c)
                                    val area = event.area.map { pre.idx(it.r, it.c) }
                                    when (event.kind) {
                                        SpecialKind.RAY -> {
                                            beams.add(BeamTrack(area, start, start + ms(BEAM_MS)))
                                            duration = max(duration, ms(BEAM_MS))
                                        }
                                        SpecialKind.JELLY -> {
                                            rings.add(RingTrack(cell, start, start + ms(RING_MS), big = false))
                                            duration = max(duration, ms(RING_MS))
                                        }
                                        SpecialKind.LANTERN_FISH -> {
                                            val target = area.lastOrNull() ?: cell
                                            rings.add(RingTrack(target, start + ms(FISH_MS) / 2, start + ms(FISH_MS), big = false))
                                            duration = max(duration, ms(FISH_MS))
                                        }
                                        SpecialKind.ABYSS_PEARL -> {
                                            beams.add(BeamTrack(area, start, start + ms(PEARL_MS)))
                                            duration = max(duration, ms(PEARL_MS))
                                        }
                                    }
                                }
                                is GameEvent.ComboActivated -> {
                                    beams.add(BeamTrack(event.area.map { pre.idx(it.r, it.c) }, start, start + ms(PEARL_MS)))
                                    if (!reducedMotion && shakes.size < 3) {
                                        shakes.add(ShakeTrack(start, start + ms(PEARL_MS), 1f))
                                    }
                                    duration = max(duration, ms(PEARL_MS))
                                }
                                is GameEvent.GlowSpread -> {
                                    val first = event.cells.firstOrNull() ?: continue
                                    rings.add(RingTrack(pre.idx(first.r, first.c), start, start + ms(RING_MS), big = true))
                                }
                                is GameEvent.OysterOpened -> if (!reducedMotion) {
                                    shakes.add(ShakeTrack(start, start + ms(CLEAR_MS), 0.5f))
                                }
                                else -> Unit
                            }
                        }
                        clock += duration
                        phases.add(ClearPhase(start, clock, next, cleared, created))
                    }
                    !next.sameAs(current) -> {
                        clock += ms(SETTLE_MS)
                        phases.add(SettlePhase(start, clock, next))
                    }
                }
                current = next
            }

            for (event in result.events) {
                when (event) {
                    is GameEvent.Board -> closeSegment(event.snapshot)
                    is GameEvent.SwapRejected -> {
                        val a = pre.idx(event.a.r, event.a.c)
                        val b = pre.idx(event.b.r, event.b.c)
                        segment.clear()
                        phases.add(SwapPhase(clock, clock + ms(REJECT_MS), current, a, b, rejected = true))
                        clock += ms(REJECT_MS)
                    }
                    is GameEvent.CascadeStep -> {
                        cascade = event.index
                        val word = cascadeWord(event.index, words)
                        if (word != null && clock < BUDGET_MS) {
                            wordTracks.add(WordTrack(word, clock, clock + ms(WORD_MS)))
                        }
                        if (event.index >= 4 && !reducedMotion && shakes.size < 3) {
                            shakes.add(ShakeTrack(clock, clock + ms(CLEAR_MS), 0.4f))
                        }
                    }
                    is GameEvent.FinaleStarted -> {
                        cascade = 1
                        wordTracks.add(WordTrack(words.getOrElse(4) { "" }, clock, clock + ms(WORD_MS)))
                        segment.add(event)
                    }
                    is GameEvent.ScoreDelta -> {
                        if (event.at in 0 until pre.size && floats.size < 12 && clock < BUDGET_MS) {
                            floats.add(FloatTrack(event.at, formatPoints(event.points), clock, clock + ms(FLOAT_MS)))
                        }
                    }
                    else -> segment.add(event)
                }
            }
            // Whatever happens after the last gravity pass (belts, oil, shuffles) ends on the final board.
            closeSegment(final)
            if (clock >= BUDGET_MS) {
                phases.add(SettlePhase(clock, clock + ms(SETTLE_MS), final))
                clock += ms(SETTLE_MS)
            }

            return AnimationTimeline(
                totalMillis = clock,
                phases = phases,
                final = final,
                beams = beams,
                rings = rings,
                floats = floats,
                shakes = shakes,
                words = wordTracks,
            )
        }

        /**
         * Follows every piece through one gravity pass: straight falls, slides and
         * portals extend its path; spawned pieces enter from above the board,
         * stacked in the order they appear.
         */
        private fun buildMovers(events: List<GameEvent>, width: Int, ms: (Long) -> Long): List<Mover> {
            val pathRows = HashMap<Int, ArrayList<Float>>()
            val pathCols = HashMap<Int, ArrayList<Float>>()
            val spawnsPerColumn = HashMap<Int, Int>()
            for (event in events) {
                if (event is GameEvent.Spawned) spawnsPerColumn.merge(event.cell.c, 1, Int::plus)
            }
            for (event in events) {
                when (event) {
                    is GameEvent.Fell, is GameEvent.Slid -> {
                        val (from, to, portal) = when (event) {
                            is GameEvent.Fell -> Triple(event.from, event.to, event.viaPortal)
                            is GameEvent.Slid -> Triple(event.from, event.to, false)
                            else -> continue
                        }
                        val fromIndex = from.r * width + from.c
                        val toIndex = to.r * width + to.c
                        val rows = pathRows.remove(fromIndex) ?: arrayListOf(from.r.toFloat())
                        val cols = pathCols.remove(fromIndex) ?: arrayListOf(from.c.toFloat())
                        if (portal) {
                            // Whirlpools: the piece re-enters just above the exit.
                            rows.add(to.r - 0.5f); cols.add(to.c.toFloat())
                        }
                        rows.add(to.r.toFloat()); cols.add(to.c.toFloat())
                        pathRows[toIndex] = rows
                        pathCols[toIndex] = cols
                    }
                    is GameEvent.Spawned -> {
                        val index = event.cell.r * width + event.cell.c
                        val drop = spawnsPerColumn[event.cell.c] ?: 1
                        pathRows[index] = arrayListOf((event.cell.r - drop).toFloat(), event.cell.r.toFloat())
                        pathCols[index] = arrayListOf(event.cell.c.toFloat(), event.cell.c.toFloat())
                    }
                    else -> Unit
                }
            }
            return pathRows.map { (to, rows) ->
                val cols = pathCols.getValue(to)
                var length = 0f
                for (i in 1 until rows.size) length += abs(rows[i] - rows[i - 1]) + abs(cols[i] - cols[i - 1])
                Mover(
                    to = to,
                    rows = rows.toFloatArray(),
                    cols = cols.toFloatArray(),
                    duration = ms(FALL_BASE_MS + (FALL_PER_CELL_MS * length).toLong()),
                )
            }
        }
    }
}
