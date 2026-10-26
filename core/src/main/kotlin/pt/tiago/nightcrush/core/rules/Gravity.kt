package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.random.SplitMix64

/**
 * Gravity: straight falls, whirlpool teleports, the diagonal slide over ledges,
 * refills from the spawners, and lantern exits. Everything is deterministic.
 */
internal object Gravity {

    /** Resolve the board until it is stable. Returns true if anything moved. */
    fun settle(r: Resolution): Boolean {
        r.snapshot()
        var changedAny = false
        var guard = 0
        while (true) {
            var changed = false
            changed = fallStraight(r) || changed
            changed = teleport(r) || changed
            changed = slideDiagonal(r) || changed
            if (changed) {
                changedAny = true
                if (++guard > 600) break
                continue
            }
            if (refill(r)) {
                changedAny = true
                if (++guard > 600) break
                continue
            }
            break
        }
        deliverLanterns(r)
        r.snapshot()
        return changedAny
    }

    /** Move a nullable piece between cells, carrying overlays, counters and armed flags. */
    fun movePiece(r: Resolution, from: Int, to: Int, viaPortal: Boolean = false, slid: Boolean = false) {
        val s = r.s
        s.pieces[to] = s.pieces[from]
        s.pieces[from] = Pieces.EMPTY
        s.overlays[to] = s.overlays[from]
        s.overlays[from] = Overlays.NONE
        s.pieceData[to] = s.pieceData[from]
        s.pieceData[from] = 0
        r.armedJelly[to] = r.armedJelly[from]
        r.armedJelly[from] = false
        r.jellyRadius[to] = r.jellyRadius[from]
        r.jellyRadius[from] = 0
        if (r.pendingJellies.remove(from)) r.pendingJellies.add(to)
        s.landStamp[to] = ++s.landCounter
        s.landStamp[from] = 0
        if (viaPortal) r.emit(GameEvent.Fell(cell(s, from), cell(s, to), viaPortal = true))
        else if (slid) r.emit(GameEvent.Slid(cell(s, from), cell(s, to)))
        else r.emit(GameEvent.Fell(cell(s, from), cell(s, to)))
        if (s.pieces[to] == Pieces.LANTERN && s.isExit(to)) deliverLantern(r, to)
    }

    fun cell(s: GameState, i: Int): Cell = Cell(s.rowOf(i), s.colOf(i))

    private fun fallStraight(r: Resolution): Boolean {
        val s = r.s
        var moved = false
        for (c in 0 until s.width) {
            var write = -1
            for (row in s.height - 1 downTo 0) {
                val i = s.idx(row, c)
                if (!s.playable[i] || s.blockers[i] != Blockers.NONE || s.overlays[i] != Overlays.NONE) {
                    write = -1
                    continue
                }
                if (s.pieces[i] != Pieces.EMPTY) {
                    if (write >= 0 && write != i) {
                        movePiece(r, i, write)
                        moved = true
                        // The next landing spot is directly above in the same column.
                        write -= s.width
                    }
                } else if (write == -1) {
                    write = i
                }
            }
        }
        return moved
    }

    private fun teleport(r: Resolution): Boolean {
        val s = r.s
        var moved = false
        for (i in 0 until s.size) {
            if (!s.isPortalIn(i) || s.pieces[i] == Pieces.EMPTY) continue
            val target = s.portalTarget[i]
            if (target < 0) continue
            if (!s.playable[target] || !s.isEmpty(target)) continue
            movePiece(r, i, target, viaPortal = true)
            moved = true
        }
        return moved
    }

    private fun slideDiagonal(r: Resolution): Boolean {
        val s = r.s
        var moved = false
        for (row in 1 until s.height) {
            for (c in 0 until s.width) {
                val i = s.idx(row, c)
                if (!s.isOpen(i)) continue
                // Candy's diagonal slide only fills pockets covered by an obstruction
                // directly above; open columns are fed from the top instead.
                val above = s.idx(row - 1, c)
                val covered = !s.playable[above] ||
                    s.blockers[above] != Blockers.NONE ||
                    s.overlays[above] != Overlays.NONE
                if (!covered) continue
                for (dc in intArrayOf(-1, 1)) {
                    val sourceCol = c + dc
                    if (sourceCol < 0 || sourceCol >= s.width) continue
                    val src = s.idx(row - 1, sourceCol)
                    if (!s.playable[src] || s.pieces[src] == Pieces.EMPTY) continue
                    if (s.blockers[src] != Blockers.NONE || s.overlays[src] != Overlays.NONE) continue
                    movePiece(r, src, i, slid = true)
                    moved = true
                    break
                }
                if (moved) continue
            }
        }
        return moved
    }

    private fun refill(r: Resolution): Boolean {
        val s = r.s
        var spawned = false
        for (c in 0 until s.width) {
            if (!s.spawner[c]) continue
            var open = true
            for (row in 0 until s.height) {
                val i = s.idx(row, c)
                if (!open) continue
                if (!s.playable[i]) {
                    open = false
                    continue
                }
                if (s.blockers[i] != Blockers.NONE || s.overlays[i] != Overlays.NONE) {
                    open = false
                    continue
                }
                if (s.pieces[i] == Pieces.EMPTY) {
                    val code = nextSpawn(r, c)
                    s.pieces[i] = code
                    if (code == Pieces.LANTERN) {
                        s.lanternsSpawned++
                        s.lanternsOnBoard++
                        s.lanternSpawnedThisMove[c] = true
                    }
                    s.landStamp[i] = ++s.landCounter
                    r.emit(GameEvent.Spawned(cell(s, i), fromOffset = -(row + 1)))
                    if (s.pieces[i] == Pieces.LANTERN && s.isExit(i)) deliverLantern(r, i)
                    spawned = true
                }
            }
        }
        return spawned
    }

    private fun nextSpawn(r: Resolution, c: Int): Int {
        val s = r.s
        val lanternReady = !s.lanternSpawnedThisMove[c] &&
            s.lanternSpawner[c] &&
            s.lanternsSpawned < s.lanternTotal &&
            s.lanternsOnBoard < s.lanternMaxOnBoard &&
            s.lanternEvery > 0 &&
            // One lantern falls due every N moves and waits for the next refill of
            // its column, so a cadence is never lost to a move that missed it.
            s.lanternsSpawned < s.movesUsed / s.lanternEvery
        if (lanternReady) {
            return Pieces.LANTERN
        }
        // One SplitMix64 stream per column, pinned by the certificate seed.
        val stream = SplitMix64(s.rngColumns[c])
        val colorIndex = s.colors[stream.nextInt(s.colors.size)]
        s.rngColumns[c] = stream.state
        return Pieces.plain(colorIndex)
    }

    private fun deliverLanterns(r: Resolution) {
        val s = r.s
        for (i in 0 until s.size) {
            if (s.isExit(i) && s.pieces[i] == Pieces.LANTERN) deliverLantern(r, i)
        }
    }

    private fun deliverLantern(r: Resolution, i: Int) {
        val s = r.s
        s.pieces[i] = Pieces.EMPTY
        s.overlays[i] = Overlays.NONE
        s.pieceData[i] = 0
        s.lanternsOnBoard = (s.lanternsOnBoard - 1).coerceAtLeast(0)
        s.lanternsDelivered++
        r.addScore(1000 * r.cascadeIndex)
        r.emit(GameEvent.LanternDelivered(cell(s, i)))
        ObjectiveTracker.onLanternDelivered(r)
        r.emit(GameEvent.ScoreDelta(1000 * r.cascadeIndex, i))
    }
}
