package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Pieces

/**
 * Match detection over runs of equal colours, merged into groups.
 *
 * Groups that share a cell (an L, a T, or a run crossed by a square) are one
 * group; classification priority is five-in-a-line > L/T > four > 2x2 > three.
 */
object MatchFinder {

    data class Run(val cells: IntArray, val horizontal: Boolean) {
        val length: Int get() = cells.size
    }

    data class Group(val cells: IntArray, val runs: List<Run>, val kind: MatchKind, val color: Int)

    /**
     * Fast probe used by swap validation: could the piece at [cell] be part of a
     * run of three or a 2x2 square right now? Only cells near [cell] are examined.
     */
    fun touchesMatch(state: GameState, cell: Int): Boolean {
        val code = state.pieces[cell]
        if (!Pieces.isMatchable(code)) return false
        val color = Pieces.colorOf(code)
        val r = state.rowOf(cell)
        val c = state.colOf(cell)
        fun at(rr: Int, cc: Int): Int =
            if (rr in 0 until state.height && cc in 0 until state.width) {
                val other = state.pieces[state.idx(rr, cc)]
                if (Pieces.isMatchable(other) && Pieces.colorOf(other) == color) 1 else 0
            } else {
                -1
            }

        var horizontal = 1
        var k = 1
        while (at(r, c + k) == 1) { horizontal++; k++ }
        k = 1
        while (at(r, c - k) == 1) { horizontal++; k++ }
        if (horizontal >= 3) return true

        var vertical = 1
        k = 1
        while (at(r + k, c) == 1) { vertical++; k++ }
        k = 1
        while (at(r - k, c) == 1) { vertical++; k++ }
        if (vertical >= 3) return true

        // 2x2 squares that contain the cell.
        for (dr in -1..0) {
            for (dc in -1..0) {
                val top = r + dr
                val left = c + dc
                if (top < 0 || left < 0 || top + 1 >= state.height || left + 1 >= state.width) continue
                val a = at(top, left)
                val b = at(top, left + 1)
                val d = at(top + 1, left)
                val e = at(top + 1, left + 1)
                if (a == 1 && b == 1 && d == 1 && e == 1) return true
            }
        }
        return false
    }

    fun find(state: GameState): List<Group> {
        val n = state.size
        val parent = IntArray(n) { it }

        fun root(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val next = parent[c]
                parent[c] = r
                c = next
            }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = root(a)
            val rb = root(b)
            if (ra == rb) return
            // Keep the smallest index as the root so group order is deterministic.
            if (ra < rb) parent[rb] = ra else parent[ra] = rb
        }

        val runs = ArrayList<Run>()

        for (r in 0 until state.height) {
            var c = 0
            while (c < state.width) {
                val first = state.pieces[state.idx(r, c)]
                if (!Pieces.isMatchable(first)) {
                    c++
                    continue
                }
                val color = Pieces.colorOf(first)
                var end = c + 1
                while (end < state.width) {
                    val code = state.pieces[state.idx(r, end)]
                    if (!Pieces.isMatchable(code) || Pieces.colorOf(code) != color) break
                    end++
                }
                if (end - c >= 3) {
                    val cells = IntArray(end - c) { state.idx(r, c + it) }
                    runs.add(Run(cells, horizontal = true))
                    for (k in 1 until cells.size) union(cells[0], cells[k])
                }
                c = end
            }
        }
        for (c in 0 until state.width) {
            var r = 0
            while (r < state.height) {
                val first = state.pieces[state.idx(r, c)]
                if (!Pieces.isMatchable(first)) {
                    r++
                    continue
                }
                val color = Pieces.colorOf(first)
                var end = r + 1
                while (end < state.height) {
                    val code = state.pieces[state.idx(end, c)]
                    if (!Pieces.isMatchable(code) || Pieces.colorOf(code) != color) break
                    end++
                }
                if (end - r >= 3) {
                    val cells = IntArray(end - r) { state.idx(r + it, c) }
                    runs.add(Run(cells, horizontal = false))
                    for (k in 1 until cells.size) union(cells[0], cells[k])
                }
                r = end
            }
        }

        // 2x2 squares of one colour are match groups of their own.
        val squareTops = ArrayList<Int>()
        for (r in 0 until state.height - 1) {
            for (c in 0 until state.width - 1) {
                val a = state.idx(r, c)
                val code = state.pieces[a]
                if (!Pieces.isMatchable(code)) continue
                val color = Pieces.colorOf(code)
                val b = state.idx(r, c + 1)
                val d = state.idx(r + 1, c)
                val e = state.idx(r + 1, c + 1)
                val same = Pieces.isMatchable(state.pieces[b]) && Pieces.colorOf(state.pieces[b]) == color &&
                    Pieces.isMatchable(state.pieces[d]) && Pieces.colorOf(state.pieces[d]) == color &&
                    Pieces.isMatchable(state.pieces[e]) && Pieces.colorOf(state.pieces[e]) == color
                if (!same) continue
                squareTops.add(a)
                union(a, b)
                union(a, d)
                union(a, e)
            }
        }

        // Collect the cells of every group.
        val inAny = BooleanArray(n)
        for (run in runs) for (cell in run.cells) inAny[cell] = true
        for (top in squareTops) {
            inAny[top] = true
            inAny[top + 1] = true
            inAny[top + state.width] = true
            inAny[top + state.width + 1] = true
        }
        val byRoot = HashMap<Int, ArrayList<Int>>()
        for (i in 0 until n) {
            if (!inAny[i]) continue
            byRoot.getOrPut(root(i)) { ArrayList() }.add(i)
        }

        val groups = ArrayList<Group>(byRoot.size)
        for ((groupRoot, cells) in byRoot) {
            val groupRuns = runs.filter { root(it.cells[0]) == groupRoot }
            val color = Pieces.colorOf(state.pieces[cells[0]])
            val hasFive = groupRuns.any { it.length >= 5 }
            val hasHorizontal = groupRuns.any { it.horizontal }
            val hasVertical = groupRuns.any { !it.horizontal }
            val hasFour = groupRuns.any { it.length == 4 }
            val hasSquare = squareTops.any { root(it) == groupRoot }
            val kind = when {
                hasFive -> MatchKind.FIVE
                hasHorizontal && hasVertical -> MatchKind.LT
                hasFour -> MatchKind.FOUR
                hasSquare -> MatchKind.SQUARE
                else -> MatchKind.THREE
            }
            groups.add(Group(cells.sorted().toIntArray(), groupRuns, kind, color))
        }
        groups.sortBy { it.cells[0] }
        return groups
    }
}
