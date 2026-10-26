package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.random.SplitMix64
import kotlin.math.roundToInt

/**
 * Everything that happens at the end of a move: conveyor belts, puffer
 * countdowns, oil growth, the periodic blockers, the finale rain and the
 * win/lose verdict.
 */
internal object PostMove {

    fun run(r: Resolution) {
        conveyor(r)
        val pufferExploded = pufferTick(r)
        oilSpread(r)
        sinkholeFeed(r)
        octopusInk(r)
        r.flushScore()
        if (r.s.outcome != Outcome.Playing) return
        when {
            ObjectiveTracker.allComplete(r.s) -> finale(r)
            pufferExploded -> lose(r, Outcome.Lost.PufferExploded)
            r.s.limitType == pt.tiago.nightcrush.core.model.LimitType.MOVES && r.s.movesLeft <= 0 ->
                lose(r, Outcome.Lost.OutOfMoves)
            // Puzzles never refill, so being stuck is final: shuffling would invent
            // a solution the puzzle does not have.
            !GameEngine.hasValidMove(r.s) -> if (r.s.stuckLoses) lose(r, Outcome.Lost.OutOfMoves) else Shuffle.run(r)
        }
    }

    internal fun lose(r: Resolution, reason: Outcome.Lost) {
        r.s.outcome = reason
        r.emit(GameEvent.Finished(reason))
    }
    // -------------------------------------------------------------- conveyor

    private fun conveyor(r: Resolution) {
        val s = r.s
        val onBelt = ArrayList<Int>()
        for (i in 0 until s.size) if (s.conveyorNext[i] >= 0 || s.conveyorNext[i] == -2) onBelt.add(i)
        if (onBelt.isEmpty()) return
        val start = onBelt.min()
        val order = ArrayList<Int>()
        var cursor = start
        while (cursor >= 0 && !order.contains(cursor)) {
            order.add(cursor)
            val next = s.conveyorNext[cursor]
            cursor = if (next == -2) -1 else next
        }
        if (order.isEmpty()) return
        val contents = order.map { Content.of(s, it) }
        // The belt rotates: every content moves to the next cell, the last wraps to
        // the first. Open paths wrap too, so nothing is ever lost.
        for (i in order.indices) {
            val to = order[(i + 1) % order.size]
            contents[i].writeTo(s, to)
        }
        for (i in order.indices) {
            if (s.pieces[order[i]] == Pieces.LANTERN && s.isExit(order[i])) {
                deliverLanternOnExit(r, order[i])
            }
        }
        r.emit(GameEvent.ConveyorMoved(order.map { Gravity.cell(s, it) }))
        r.resolveCascades(null, -1, -1, startStep = r.cascadeIndex + 1)
    }

    private fun deliverLanternOnExit(r: Resolution, cell: Int) {
        val s = r.s
        s.pieces[cell] = Pieces.EMPTY
        s.overlays[cell] = Overlays.NONE
        s.pieceData[cell] = 0
        s.lanternsOnBoard = (s.lanternsOnBoard - 1).coerceAtLeast(0)
        s.lanternsDelivered++
        r.addScore(1000 * r.cascadeIndex)
        r.emit(GameEvent.LanternDelivered(Gravity.cell(s, cell)))
        ObjectiveTracker.onLanternDelivered(r)
        r.emit(GameEvent.ScoreDelta(1000 * r.cascadeIndex, cell))
    }

    // ---------------------------------------------------------------- puffers

    /** Returns true when a counter reached zero. */
    private fun pufferTick(r: Resolution): Boolean {
        val s = r.s
        var exploded = false
        for (i in 0 until s.size) {
            if (!Pieces.isPuffer(s.pieces[i])) continue
            s.pieceData[i] -= 1
            r.emit(GameEvent.PufferTick(Gravity.cell(s, i), s.pieceData[i]))
            if (s.pieceData[i] <= 0) exploded = true
        }
        return exploded
    }

    // ------------------------------------------------------------- black tide

    private fun oilSpread(r: Resolution) {
        val s = r.s
        if (r.oilsDestroyedThisMove) return
        val pairs = ArrayList<IntArray>()
        for (i in 0 until s.size) {
            if (s.blockers[i] != Blockers.OIL) continue
            val r0 = s.rowOf(i)
            val c0 = s.colOf(i)
            for (neighbor in intArrayOf(
                if (r0 > 0) s.idx(r0 - 1, c0) else -1,
                if (r0 < s.height - 1) s.idx(r0 + 1, c0) else -1,
                if (c0 > 0) s.idx(r0, c0 - 1) else -1,
                if (c0 < s.width - 1) s.idx(r0, c0 + 1) else -1,
            )) {
                if (neighbor < 0) continue
                if (!s.playable[neighbor]) continue
                if (s.blockers[neighbor] != Blockers.NONE) continue
                if (s.overlays[neighbor] != Overlays.NONE) continue
                if (!Pieces.isMatchable(s.pieces[neighbor])) continue
                pairs.add(intArrayOf(i, neighbor))
            }
        }
        if (pairs.isEmpty()) return
        pairs.sortWith(compareBy({ it[1] }, { it[0] }))
        val stream = SplitMix64(s.rngAux)
        val pick = pairs[stream.nextInt(pairs.size)]
        s.rngAux = stream.state
        val target = pick[1]
        s.pieces[target] = Pieces.EMPTY
        s.pieceData[target] = 0
        s.blockers[target] = Blockers.OIL
        r.emit(GameEvent.OilSpread(Gravity.cell(s, pick[0]), Gravity.cell(s, target)))
    }

    // -------------------------------------------------------------- sinkholes

    private fun sinkholeFeed(r: Resolution) {
        val s = r.s
        if (s.movesUsed % 2 != 0) return
        val anchors = (0 until s.size).filter {
            s.blockers[it] == Blockers.SINKHOLE && s.blockerAnchor[it] == it
        }
        for (anchor in anchors) {
            val candidates = LinkedHashSet<Int>()
            val r0 = s.rowOf(anchor)
            val c0 = s.colOf(anchor)
            for (rr in r0 until minOf(r0 + 2, s.height)) {
                for (cc in c0 until minOf(c0 + 2, s.width)) {
                    val cell = s.idx(rr, cc)
                    for (neighbor in neighborCells(s, cell)) {
                        if (s.blockers[neighbor] != Blockers.NONE) continue
                        if (s.overlays[neighbor] != Overlays.NONE) continue
                        if (!Pieces.isMatchable(s.pieces[neighbor])) continue
                        candidates.add(neighbor)
                    }
                }
            }
            if (candidates.isEmpty()) continue
            val sorted = candidates.sorted()
            val stream = SplitMix64(s.rngAux)
            val target = sorted[stream.nextInt(sorted.size)]
            s.rngAux = stream.state
            s.pieces[target] = Pieces.EMPTY
            s.pieceData[target] = 0
            r.emit(GameEvent.SinkholeSwallow(Gravity.cell(s, target)))
        }
    }

    // ----------------------------------------------------------------- octopus

    private fun octopusInk(r: Resolution) {
        val s = r.s
        if (s.movesUsed % 3 != 0) return
        for (i in 0 until s.size) {
            if (s.blockers[i] != Blockers.OCTOPUS || s.blockerData[i] <= 0) continue
            val candidates = ArrayList<Int>()
            val r0 = s.rowOf(i)
            val c0 = s.colOf(i)
            for (rr in maxOf(0, r0 - 2)..minOf(s.height - 1, r0 + 2)) {
                for (cc in maxOf(0, c0 - 2)..minOf(s.width - 1, c0 + 2)) {
                    val cell = s.idx(rr, cc)
                    if (cell == i) continue
                    if (s.blockers[cell] != Blockers.NONE) continue
                    if (s.overlays[cell] != Overlays.NONE) continue
                    if (!Pieces.isMatchable(s.pieces[cell])) continue
                    candidates.add(cell)
                }
            }
            if (candidates.isEmpty()) continue
            val stream = SplitMix64(s.rngAux)
            val picked = ArrayList<Int>(2)
            repeat(2) {
                if (candidates.isEmpty()) return@repeat
                val index = stream.nextInt(candidates.size)
                picked.add(candidates.removeAt(index))
            }
            s.rngAux = stream.state
            for (cell in picked) s.overlays[cell] = Overlays.NET
            r.emit(GameEvent.OctopusInk(picked.sorted().map { Gravity.cell(s, it) }))
        }
    }

    // ----------------------------------------------------------------- finale

    internal fun finale(r: Resolution) {
        val s = r.s
        r.emit(GameEvent.FinaleStarted)
        val rain = if (s.limitType == pt.tiago.nightcrush.core.model.LimitType.MOVES) {
            s.movesLeft
        } else {
            s.timeLeftSec / 5
        }
        val stream = SplitMix64(s.rngAux)
        var remaining = rain
        val used = HashSet<Int>()
        while (remaining > 0) {
            val candidates = (0 until s.size).filter {
                s.playable[it] && it !in used &&
                    Pieces.colorOf(s.pieces[it]) >= 0 &&
                    Pieces.specialOf(s.pieces[it]) == null &&
                    !Pieces.isPuffer(s.pieces[it]) &&
                    s.overlays[it] == Overlays.NONE
            }
            if (candidates.isEmpty()) break
            val cell = candidates[stream.nextInt(candidates.size)]
            used.add(cell)
            val vertical = stream.nextInt(2) == 0
            val color = Pieces.colorOf(s.pieces[cell])
            s.pieces[cell] = if (vertical) Pieces.rayAlongColumn(color) else Pieces.rayAlongRow(color)
            r.addScore(1000)
            remaining--
        }
        s.rngAux = stream.state
        r.finaleFireAllRays()
        r.resolveCascades(null, -1, -1, startStep = maxOf(1, r.cascadeIndex + 1))
        r.flushScore()
        val multiplier = when (s.level.difficulty) {
            Difficulty.EASY -> 1.0
            Difficulty.MEDIUM -> 1.5
            Difficulty.HARD -> 2.0
        }
        s.score = (s.score * multiplier).roundToInt()
        s.outcome = Outcome.Won
        r.emit(GameEvent.Finished(Outcome.Won))
    }

    // -------------------------------------------------------------- helpers

    private fun neighborCells(s: GameState, cell: Int): List<Int> {
        val r0 = s.rowOf(cell)
        val c0 = s.colOf(cell)
        val out = ArrayList<Int>(4)
        if (r0 > 0) out.add(s.idx(r0 - 1, c0))
        if (r0 < s.height - 1) out.add(s.idx(r0 + 1, c0))
        if (c0 > 0) out.add(s.idx(r0, c0 - 1))
        if (c0 < s.width - 1) out.add(s.idx(r0, c0 + 1))
        return out
    }

    /** Snapshot of everything that rides on a conveyor cell. */
    private class Content(
        val piece: Int,
        val data: Int,
        val overlay: Int,
        val stamp: Int,
    ) {
        fun writeTo(s: GameState, cell: Int) {
            s.pieces[cell] = piece
            s.pieceData[cell] = data
            s.overlays[cell] = overlay
            s.landStamp[cell] = stamp
        }

        companion object {
            fun of(s: GameState, cell: Int) = Content(s.pieces[cell], s.pieceData[cell], s.overlays[cell], s.landStamp[cell])
        }
    }
}