package pt.tiago.nightcrush.core.testutil

import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.FloorFlags
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LimitType
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.rules.MoveResult

/** Test-only board builder: ASCII boards plus per-cell decorations. */
class BoardEdit(private val s: GameState) {

    fun piece(r: Int, c: Int, code: Int) {
        s.pieces[s.idx(r, c)] = code
    }

    fun plain(r: Int, c: Int, color: Int) = piece(r, c, Pieces.plain(color))
    fun rayH(r: Int, c: Int, color: Int) = piece(r, c, Pieces.rayAlongRow(color))
    fun rayV(r: Int, c: Int, color: Int) = piece(r, c, Pieces.rayAlongColumn(color))
    fun jelly(r: Int, c: Int, color: Int) = piece(r, c, Pieces.jelly(color))
    fun fish(r: Int, c: Int, color: Int) = piece(r, c, Pieces.lanternFish(color))
    fun lantern(r: Int, c: Int) = piece(r, c, Pieces.LANTERN)
    fun pearl(r: Int, c: Int) = piece(r, c, Pieces.PEARL)

    fun puffer(r: Int, c: Int, color: Int, counter: Int) {
        piece(r, c, Pieces.puffer(color))
        s.pieceData[s.idx(r, c)] = counter
    }

    fun barnacle(r: Int, c: Int, layers: Int = 1) {
        s.overlays[s.idx(r, c)] = Overlays.barnacle(layers)
    }

    fun net(r: Int, c: Int) {
        s.overlays[s.idx(r, c)] = Overlays.NET
    }

    fun coral(r: Int, c: Int, layers: Int = 1) {
        s.blockers[s.idx(r, c)] = Blockers.coral(layers)
        s.pieces[s.idx(r, c)] = Pieces.EMPTY
        s.overlays[s.idx(r, c)] = Overlays.NONE
    }

    fun oil(r: Int, c: Int) {
        s.blockers[s.idx(r, c)] = Blockers.OIL
        s.pieces[s.idx(r, c)] = Pieces.EMPTY
    }

    fun bubble(r: Int, c: Int) {
        s.blockers[s.idx(r, c)] = Blockers.SEAHORSE_BUBBLE
        s.pieces[s.idx(r, c)] = Pieces.EMPTY
    }

    fun sinkhole(r: Int, c: Int, hits: Int = 3) {
        val anchor = s.idx(r, c)
        for (rr in r until r + 2) {
            for (cc in c until c + 2) {
                val i = s.idx(rr, cc)
                s.blockers[i] = Blockers.SINKHOLE
                s.blockerData[i] = hits
                s.blockerAnchor[i] = anchor
                s.pieces[i] = Pieces.EMPTY
            }
        }
    }

    fun oyster(r: Int, c: Int, color: Int, counter: Int) {
        val anchor = s.idx(r, c)
        for (rr in r until r + 2) {
            for (cc in c until c + 2) {
                val i = s.idx(rr, cc)
                s.blockers[i] = Blockers.oyster(color)
                s.blockerData[i] = counter
                s.blockerAnchor[i] = anchor
                s.pieces[i] = Pieces.EMPTY
            }
        }
    }

    fun octopus(r: Int, c: Int, hp: Int = 5) {
        val i = s.idx(r, c)
        s.blockers[i] = Blockers.OCTOPUS
        s.blockerData[i] = hp
        s.pieces[i] = Pieces.EMPTY
    }

    fun silt(r: Int, c: Int, layers: Int) {
        s.silt[s.idx(r, c)] = layers
    }

    fun glow(r: Int, c: Int, lit: Boolean = true) {
        val i = s.idx(r, c)
        s.floorFlags[i] = if (lit) s.floorFlags[i] or FloorFlags.GLOW else s.floorFlags[i] and FloorFlags.GLOW.inv()
    }

    fun exit(r: Int, c: Int) {
        val i = s.idx(r, c)
        s.floorFlags[i] = s.floorFlags[i] or FloorFlags.EXIT
    }

    fun portal(fromR: Int, fromC: Int, toR: Int, toC: Int) {
        val from = s.idx(fromR, fromC)
        val to = s.idx(toR, toC)
        s.floorFlags[from] = s.floorFlags[from] or FloorFlags.PORTAL_IN
        s.floorFlags[to] = s.floorFlags[to] or FloorFlags.PORTAL_OUT
        s.portalTarget[from] = to
    }

    fun conveyorLoop(vararg cells: Pair<Int, Int>) {
        wireConveyor(cells.toList(), loop = true)
    }

    fun conveyorOpen(vararg cells: Pair<Int, Int>) {
        wireConveyor(cells.toList(), loop = false)
    }

    private fun wireConveyor(cells: List<Pair<Int, Int>>, loop: Boolean) {
        val idx = cells.map { s.idx(it.first, it.second) }
        for (k in idx.indices) {
            s.conveyorNext[idx[k]] = if (k == idx.size - 1) (if (loop) idx[0] else -2) else idx[k + 1]
        }
    }

    fun spawner(c: Int, lantern: Boolean = false) {
        s.spawner[c] = true
        s.lanternSpawner[c] = lantern
    }

    fun refillAll() {
        for (c in 0 until s.width) s.spawner[c] = true
    }

    fun noRefill() {
        for (c in 0 until s.width) {
            s.spawner[c] = false
            s.lanternSpawner[c] = false
        }
    }

    fun colors(vararg indexes: Int) {
        s.colors = indexes
    }

    fun moves(n: Int) {
        s.limitType = LimitType.MOVES
        s.limitValue = n
        s.movesLeft = n
    }

    fun time(seconds: Int) {
        s.limitType = LimitType.TIME
        s.timeLeftSec = seconds
        s.movesLeft = 0
    }

    fun lanternRules(total: Int, maxOnBoard: Int, everyMoves: Int) {
        s.lanternTotal = total
        s.lanternMaxOnBoard = maxOnBoard
        s.lanternEvery = everyMoves
    }

    fun objective(objective: Objective) {
        s.objectives = s.objectives + objective
        s.progress = s.progress + IntArray(objective.slots)
    }

    fun difficulty(d: Difficulty) {
        s.level = s.level.copy(difficulty = d)
    }

    fun seed(seed: Long) {
        val base = pt.tiago.nightcrush.core.random.SplitMix64(seed)
        for (c in 0 until s.width) s.rngColumns[c] = base.nextLong()
        s.rngAux = base.nextLong()
    }

    fun usedMoves(n: Int) {
        s.movesUsed = n
    }
}

/** Settle the board with no events: gravity, slides, portals and refill. */
fun GameState.settle(): GameState {
    val copy = copyState()
    val resolution = pt.tiago.nightcrush.core.rules.Resolution(copy, null)
    pt.tiago.nightcrush.core.rules.Gravity.settle(resolution)
    return copy
}

fun boardOf(vararg rows: String, edit: BoardEdit.() -> Unit = {}): GameState {
    val width = rows.maxOf { it.length }
    val height = rows.size
    val s = GameState(width = width, height = height)
    for (r in 0 until height) {
        val row = rows[r]
        for (c in 0 until width) {
            val ch = row.getOrNull(c) ?: ' '
            val i = s.idx(r, c)
            when (ch) {
                ' ' -> s.playable[i] = false
                '.' -> Unit
                'L' -> s.pieces[i] = Pieces.LANTERN
                '*' -> s.pieces[i] = Pieces.PEARL
                'B' -> s.pieces[i] = Pieces.plain(0)
                'Y' -> s.pieces[i] = Pieces.plain(1)
                'P' -> s.pieces[i] = Pieces.plain(2)
                'T' -> s.pieces[i] = Pieces.plain(3)
                'G' -> s.pieces[i] = Pieces.plain(4)
                'C' -> s.pieces[i] = Pieces.plain(5)
                else -> error("Unknown board char '$ch'")
            }
        }
    }
    for (c in 0 until width) {
        s.spawner[c] = false
        s.lanternSpawner[c] = false
    }
    s.limitValue = 99
    s.movesLeft = 99
    BoardEdit(s).edit()
    return s
}

fun GameState.pieceAt(r: Int, c: Int): Int = pieces[idx(r, c)]
fun GameState.blockerAt(r: Int, c: Int): Int = blockers[idx(r, c)]
fun GameState.siltAt(r: Int, c: Int): Int = silt[idx(r, c)]
fun GameState.overlayAt(r: Int, c: Int): Int = overlays[idx(r, c)]
fun GameState.isLit(r: Int, c: Int): Boolean = isGlow(idx(r, c))

fun GameState.swap(r1: Int, c1: Int, r2: Int, c2: Int): MoveResult =
    GameEngine.applyMove(this, Move.Swap(Cell(r1, c1), Cell(r2, c2)))

fun GameState.play(move: Move): MoveResult = GameEngine.applyMove(this, move)

/** Single-character view for assertion messages. */
fun GameState.render(): String = buildString {
    for (r in 0 until height) {
        for (c in 0 until width) {
            val i = idx(r, c)
            if (!playable[i]) {
                append('#')
                continue
            }
            val code = pieces[i]
            if (blockers[i] != Blockers.NONE) {
                append(
                    when {
                        blockers[i] in 1..3 -> 'c'
                        blockers[i] == Blockers.OIL -> 'o'
                        blockers[i] == Blockers.SEAHORSE_BUBBLE -> 'b'
                        Blockers.isOyster(blockers[i]) -> 'O'
                        blockers[i] == Blockers.SINKHOLE -> 'S'
                        else -> 'x'
                    },
                )
                continue
            }
            append(
                when {
                    code == Pieces.EMPTY -> '.'
                    code == Pieces.LANTERN -> 'L'
                    code == Pieces.PEARL -> '*'
                    code in 7..12 -> 'h'
                    code in 13..18 -> 'v'
                    code in 19..24 -> 'j'
                    code in 25..30 -> 'f'
                    code in 31..36 -> 'u'
                    else -> "BYPTGC"[Pieces.colorOf(code)]
                },
            )
        }
        append('\n')
    }
}
