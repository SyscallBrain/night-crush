package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LimitType
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces

/**
 * The single game engine. One move in, one pure result out: the state is copied,
 * resolved and returned with an ordered event list. The solver, the certificate
 * verifier and the app all call this function, so rules can never diverge.
 */
object GameEngine {

    fun applyMove(state: GameState, move: Move, recordEvents: Boolean = true): MoveResult {
        val copy = state.copyState()
        // Per-move markers reset here, before anything resolves.
        java.util.Arrays.fill(copy.lanternSpawnedThisMove, false)
        val events = if (recordEvents) ArrayList<GameEvent>(64) else null
        val r = Resolution(copy, events)
        when (move) {
            is Move.Swap -> swap(r, move)
            is Move.Harpoon -> harpoon(r, move)
            is Move.FreeSwap -> freeSwap(r, move)
            Move.Shuffle -> {
                Shuffle.run(r, force = true)
                r.flushScore()
            }
            Move.TimeUp -> timeUp(r)
        }
        r.flushScore()
        return MoveResult(copy, events ?: emptyList(), copy.outcome)
    }

    private fun swap(r: Resolution, move: Move.Swap) {
        val s = r.s
        if (!s.inBounds(move.a.r, move.a.c) || !s.inBounds(move.b.r, move.b.c)) return
        val a = s.idx(move.a.r, move.a.c)
        val b = s.idx(move.b.r, move.b.c)
        r.emit(GameEvent.SwapStarted(move.a, move.b))
        if (!isAdjacent(s, a, b) || !r.isSwappable(a) || !r.isSwappable(b)) {
            r.emit(GameEvent.SwapRejected(move.a, move.b))
            return
        }
        if (s.pieces[a] == Pieces.LANTERN && s.pieces[b] == Pieces.LANTERN) {
            r.emit(GameEvent.SwapRejected(move.a, move.b))
            return
        }
        r.swapCells(a, b)
        val forced = detectForced(s, a, b)
        if (forced == null) {
            val groups = MatchFinder.find(s).filter { it.cells.contains(a) || it.cells.contains(b) }
            if (groups.isEmpty()) {
                r.swapCells(a, b)
                r.emit(GameEvent.SwapRejected(move.a, move.b))
                return
            }
            r.snapshot()
            spendMove(r)
            r.resolveCascades(groups, a, b)
        } else {
            r.snapshot()
            spendMove(r)
            r.applyForced(forced)
            r.runPendingActivations()
            r.resolveCascades(null, -1, -1)
        }
        PostMove.run(r)
    }

    private fun harpoon(r: Resolution, move: Move.Harpoon) {
        val s = r.s
        if (!s.inBounds(move.target.r, move.target.c)) return
        val cell = s.idx(move.target.r, move.target.c)
        if (!s.playable[cell]) return
        when {
            s.blockers[cell] != Blockers.NONE -> r.damageBlocker(cell, 1, ClearCause.BOOSTER)
            s.pieces[cell] != Pieces.EMPTY -> r.clearCell(cell, ClearCause.BOOSTER, points = 60)
            s.silt[cell] > 0 -> r.clearCell(cell, ClearCause.BOOSTER, points = 0)
        }
        r.runPendingActivations()
        r.resolveCascades(null, -1, -1)
        PostMove.run(r)
    }

    private fun freeSwap(r: Resolution, move: Move.FreeSwap) {
        val s = r.s
        if (!s.inBounds(move.a.r, move.a.c) || !s.inBounds(move.b.r, move.b.c)) return
        val a = s.idx(move.a.r, move.a.c)
        val b = s.idx(move.b.r, move.b.c)
        r.emit(GameEvent.SwapStarted(move.a, move.b))
        if (!isAdjacent(s, a, b) || !r.isSwappable(a) || !r.isSwappable(b)) {
            r.emit(GameEvent.SwapRejected(move.a, move.b))
            return
        }
        r.swapCells(a, b)
        r.snapshot()
        val forced = detectForced(s, a, b)
        if (forced != null) {
            r.applyForced(forced)
            r.runPendingActivations()
            r.resolveCascades(null, -1, -1)
        } else {
            val groups = MatchFinder.find(s).filter { it.cells.contains(a) || it.cells.contains(b) }
            r.resolveCascades(groups, a, b)
        }
        PostMove.run(r)
    }

    private fun timeUp(r: Resolution) {
        val s = r.s
        if (s.outcome != Outcome.Playing) return
        if (ObjectiveTracker.allComplete(s)) {
            PostMove.finale(r)
        } else {
            s.outcome = Outcome.Lost.OutOfTime
            r.emit(GameEvent.Finished(Outcome.Lost.OutOfTime))
        }
    }

    private fun spendMove(r: Resolution) {
        val s = r.s
        if (s.limitType == LimitType.MOVES) s.movesLeft -= 1
        s.movesUsed += 1
    }

    private fun isAdjacent(s: GameState, a: Int, b: Int): Boolean {
        val ra = s.rowOf(a)
        val ca = s.colOf(a)
        val rb = s.rowOf(b)
        val cb = s.colOf(b)
        return kotlin.math.abs(ra - rb) + kotlin.math.abs(ca - cb) == 1
    }

    /** True when at least one swap is available; drives the shuffle rule. */
    fun hasValidMove(state: GameState): Boolean {
        for (i in 0 until state.size) {
            val r0 = state.rowOf(i)
            val c0 = state.colOf(i)
            if (c0 < state.width - 1 && swapResolves(state, i, i + 1)) return true
            if (r0 < state.height - 1 && swapResolves(state, i, i + state.width)) return true
        }
        return false
    }

    /**
     * True when gravity and the spawners can refill every free cell of an emptied
     * board. Content tooling uses it to reject shapes that starve some cells.
     */
    fun refillsEverywhere(state: GameState): Boolean {
        val copy = state.copyState()
        fun free(i: Int) = copy.playable[i] && copy.blockers[i] == Blockers.NONE && copy.overlays[i] == Overlays.NONE
        for (i in 0 until copy.size) if (free(i)) copy.pieces[i] = Pieces.EMPTY
        Gravity.settle(Resolution(copy, null))
        return (0 until copy.size).none { free(it) && copy.pieces[it] == Pieces.EMPTY }
    }

    /** Every valid swap on the board, for hints and for the shuffle check. */
    fun validSwaps(state: GameState, limit: Int = Int.MAX_VALUE): List<Move.Swap> {
        val out = ArrayList<Move.Swap>()
        for (i in 0 until state.size) {
            if (out.size >= limit) break
            val r0 = state.rowOf(i)
            val c0 = state.colOf(i)
            if (c0 < state.width - 1 && swapResolves(state, i, i + 1)) {
                out.add(Move.Swap(cell(state, i), cell(state, i + 1)))
                if (out.size >= limit) break
            }
            if (r0 < state.height - 1 && swapResolves(state, i, i + state.width)) {
                out.add(Move.Swap(cell(state, i), cell(state, i + state.width)))
            }
        }
        return out
    }

    private fun cell(s: GameState, i: Int) = Cell(s.rowOf(i), s.colOf(i))

    private fun swapResolves(state: GameState, a: Int, b: Int): Boolean {
        if (state.pieces[a] == Pieces.EMPTY || state.pieces[b] == Pieces.EMPTY) return false
        if (state.blockers[a] != Blockers.NONE || state.blockers[b] != Blockers.NONE) return false
        if (state.overlays[a] != Overlays.NONE || state.overlays[b] != Overlays.NONE) return false
        if (state.pieces[a] == Pieces.LANTERN && state.pieces[b] == Pieces.LANTERN) return false
        if (detectForced(state, a, b) != null) return true
        // Only the two swapped cells change, so any new group must touch one of them.
        swapForProbe(state, a, b)
        val connects = MatchFinder.touchesMatch(state, a) || MatchFinder.touchesMatch(state, b)
        swapForProbe(state, a, b)
        return connects
    }

    private fun swapForProbe(s: GameState, a: Int, b: Int) {
        val piece = s.pieces[a]; s.pieces[a] = s.pieces[b]; s.pieces[b] = piece
        val data = s.pieceData[a]; s.pieceData[a] = s.pieceData[b]; s.pieceData[b] = data
        val overlay = s.overlays[a]; s.overlays[a] = s.overlays[b]; s.overlays[b] = overlay
        val stamp = s.landStamp[a]; s.landStamp[a] = s.landStamp[b]; s.landStamp[b] = stamp
    }
}

/**
 * Which special use a swap triggers by itself, without a match: the pearl with
 * anything, a lantern fish with anything, or two specials together.
 */
internal sealed interface ForcedUse {
    data class Combo(val combo: pt.tiago.nightcrush.core.model.ComboKind, val first: Int, val second: Int) : ForcedUse
    data class Fish(val cell: Int) : ForcedUse
}

internal fun detectForced(s: GameState, a: Int, b: Int): ForcedUse? {
    val pa = s.pieces[a]
    val pb = s.pieces[b]
    if (pa == Pieces.PEARL || pb == Pieces.PEARL) {
        val pearlCell = if (pa == Pieces.PEARL) a else b
        val otherCell = if (pearlCell == a) b else a
        val other = s.pieces[otherCell]
        val combo = when {
            other == Pieces.PEARL -> pt.tiago.nightcrush.core.model.ComboKind.PEARL_PEARL
            Pieces.isRay(other) -> pt.tiago.nightcrush.core.model.ComboKind.PEARL_RAY
            Pieces.isJelly(other) -> pt.tiago.nightcrush.core.model.ComboKind.PEARL_JELLY
            Pieces.isLanternFish(other) -> pt.tiago.nightcrush.core.model.ComboKind.PEARL_FISH
            Pieces.isMatchable(other) -> pt.tiago.nightcrush.core.model.ComboKind.PEARL_PLAIN
            else -> return null
        }
        return ForcedUse.Combo(combo, pearlCell, otherCell)
    }
    if (Pieces.isLanternFish(pa) || Pieces.isLanternFish(pb)) {
        val fishCell = if (Pieces.isLanternFish(pa)) a else b
        val otherCell = if (fishCell == a) b else a
        val other = s.pieces[otherCell]
        val combo = when {
            Pieces.isLanternFish(other) -> pt.tiago.nightcrush.core.model.ComboKind.FISH_FISH
            Pieces.isRay(other) -> pt.tiago.nightcrush.core.model.ComboKind.FISH_RAY
            Pieces.isJelly(other) -> pt.tiago.nightcrush.core.model.ComboKind.FISH_JELLY
            else -> null
        }
        return if (combo != null) ForcedUse.Combo(combo, fishCell, otherCell) else ForcedUse.Fish(fishCell)
    }
    val aSpecial = Pieces.isRay(pa) || Pieces.isJelly(pa)
    val bSpecial = Pieces.isRay(pb) || Pieces.isJelly(pb)
    if (aSpecial && bSpecial) {
        val combo = when {
            Pieces.isRay(pa) && Pieces.isRay(pb) -> pt.tiago.nightcrush.core.model.ComboKind.RAY_RAY
            Pieces.isJelly(pa) && Pieces.isJelly(pb) -> pt.tiago.nightcrush.core.model.ComboKind.JELLY_JELLY
            else -> pt.tiago.nightcrush.core.model.ComboKind.RAY_JELLY
        }
        return ForcedUse.Combo(combo, a, b)
    }
    return null
}
