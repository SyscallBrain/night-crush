package pt.tiago.nightcrush.leveltool

import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Order
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.PreLevelBooster
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.serialization.StateHasher

/** Fast 64-bit fingerprint used only for beam deduplication. */
internal object FastHash {
    fun of(state: GameState): Long {
        var h = -0x340d631b7bdddcdbL
        fun mix(value: Int) {
            h = (h xor (value.toLong() and 0xFFFFFFFFL)) * 0x100000001b3L
        }
        for (i in 0 until state.size) {
            mix(state.pieces[i])
        }
        for (i in 0 until state.size) {
            mix(state.overlays[i])
            mix(state.blockers[i])
        }
        for (i in 0 until state.size) {
            mix(state.pieceData[i])
            mix(state.blockerData[i])
            mix(state.silt[i])
            mix(state.floorFlags[i])
            mix(state.landStamp[i])
        }
        for (i in state.rngColumns.indices) mix(state.rngColumns[i].toInt())
        for (i in state.lanternSpawnedThisMove.indices) mix(if (state.lanternSpawnedThisMove[i]) 1 else 0)
        mix(state.rngAux.toInt())
        mix(state.movesLeft)
        mix(state.movesUsed)
        mix(state.score)
        for (slice in state.progress) for (value in slice) mix(value)
        return h
    }
}

object Solver {

    data class Solution(
        val moves: List<Move.Swap>,
        val hashes: List<String>,
        val score: Int,
        val movesUsed: Int,
        val maxCascade: Int,
    )

    data class Options(
        val beamWidths: List<Int> = listOf(30, 120),
        val millisPerWidth: List<Long> = listOf(1_500, 4_000),
        val maxDepthExtra: Int = 6,
    ) {
        companion object {
            /** Wider beams and longer deadlines for the final gap filling. */
            val DEEP = Options(
                beamWidths = listOf(60, 300, 1_200),
                millisPerWidth = listOf(3_000, 12_000, 40_000),
                maxDepthExtra = 10,
            )
        }
    }

    fun solve(
        level: LevelFile,
        difficulty: Difficulty,
        boosters: List<PreLevelBooster> = emptyList(),
        options: Options = Options(),
        totalBudgetMillis: Long = 12_000,
    ): Solution? {
        val hardDeadline = System.nanoTime() + totalBudgetMillis * 1_000_000L
        for ((index, width) in options.beamWidths.withIndex()) {
            if (System.nanoTime() > hardDeadline) return null
            val deadline = minOf(hardDeadline, System.nanoTime() + options.millisPerWidth[index] * 1_000_000L)
            val solution = search(level, difficulty, boosters, width, deadline, options.maxDepthExtra)
            if (solution != null) return solution
        }
        return null
    }

    private class Node(
        val state: GameState,
        val moves: List<Move.Swap>,
        val value: Double,
        val key: Long,
    )

    private fun search(
        level: LevelFile,
        difficulty: Difficulty,
        boosters: List<PreLevelBooster>,
        beamWidth: Int,
        deadline: Long,
        depthExtra: Int,
    ): Solution? {
        val initial = LevelBuilder.start(level, difficulty, boosters)
        val maxDepth = if (initial.limitType == pt.tiago.nightcrush.core.model.LimitType.MOVES) {
            initial.movesLeft
        } else {
            initial.timeLeftSec / 4
        } + depthExtra
        var beam = listOf(Node(initial, emptyList(), evaluate(initial), FastHash.of(initial)))
        val seen = HashSet<Long>()
        seen.add(beam[0].key)
        var depth = 0
        while (beam.isNotEmpty() && depth < maxDepth) {
            depth++
            val children = ArrayList<Node>(beam.size * 8)
            for (node in beam) {
                if (System.nanoTime() > deadline) return null
                for (swap in GameEngine.validSwaps(node.state)) {
                    val result = GameEngine.applyMove(node.state, swap, recordEvents = false)
                    when (result.state.outcome) {
                        is Outcome.Won -> return replay(level, difficulty, boosters, node.moves + swap)
                        is Outcome.Lost -> continue
                        else -> Unit
                    }
                    val key = FastHash.of(result.state)
                    if (!seen.add(key)) continue
                    children.add(Node(result.state, node.moves + swap, evaluate(result.state), key))
                }
            }
            if (children.isEmpty()) return null
            children.sortByDescending { it.value }
            beam = children.subList(0, minOf(beamWidth, children.size)).toList()
        }
        return null
    }

    private fun replay(
        level: LevelFile,
        difficulty: Difficulty,
        boosters: List<PreLevelBooster>,
        moves: List<Move.Swap>,
    ): Solution {
        var state = LevelBuilder.start(level, difficulty, boosters)
        val hashes = ArrayList<String>(moves.size)
        for (move in moves) {
            state = GameEngine.applyMove(state, move, recordEvents = false).state
            hashes.add(StateHasher.hash(state))
        }
        return Solution(moves, hashes, state.score, state.movesUsed, state.maxCascade)
    }

    /** Heuristic: objective progress dominates, then safety and specials. */
    fun evaluate(state: GameState): Double {
        if (state.outcome is Outcome.Won) return 1e9
        if (state.outcome is Outcome.Lost) return -1e9
        var value = 0.0
        state.objectives.forEachIndexed { index, objective ->
            val slice = state.progress[index]
            value += when (objective) {
                is Objective.CollectColors -> 700.0 * completion(slice, objective.counts)
                is Objective.ClearSilt -> 700.0 * ratio(slice[0], objective.target)
                is Objective.DeliverLanterns -> 800.0 * ratio(slice[0], objective.target)
                is Objective.Orders -> 600.0 * objective.orders.withIndex().sumOf { (slot, order) ->
                    ratio(slice[slot], order.target)
                } / objective.orders.size
                is Objective.ClearBlockers -> 600.0 * completion(slice, objective.counts)
                is Objective.SpreadGlow -> 600.0 * ratio(litCells(state), state.playableCount())
                is Objective.FreeSeahorses -> 700.0 * ratio(slice[0], objective.target)
            }
        }
        val lanternTarget = state.objectives.filterIsInstance<Objective.DeliverLanterns>().firstOrNull()?.target
        val lanternWorth = if (lanternTarget != null && lanternTarget > 0) 800.0 / lanternTarget else 0.0
        var specials = 0
        var pairs = 0
        for (i in 0 until state.size) {
            val code = state.pieces[i]
            if (Pieces.isRay(code) || Pieces.isJelly(code) || Pieces.isLanternFish(code) || code == Pieces.PEARL) {
                specials++
                val r = state.rowOf(i)
                val c = state.colOf(i)
                if (c < state.width - 1 && isSpecial(state.pieces[i + 1])) pairs++
                if (r < state.height - 1 && isSpecial(state.pieces[i + state.width])) pairs++
            }
            if (Pieces.isPuffer(code)) value -= 40.0 * (10 - state.pieceData[i].coerceAtMost(10))
            if (state.blockers[i] == pt.tiago.nightcrush.core.model.Blockers.OIL) value -= 6.0
            // Lanterns are worth more the closer they are to their exit, but always
            // less than delivering them, or the agent would rather keep them.
            if (code == Pieces.LANTERN) {
                value += lanternWorth * 0.6 * (state.rowOf(i) + 1) / state.height
            }
        }
        value += specials * 12.0 + pairs * 45.0
        value += state.score * 0.02
        value -= state.movesUsed * 2.0
        return value
    }

    private fun isSpecial(code: Int): Boolean =
        Pieces.isRay(code) || Pieces.isJelly(code) || Pieces.isLanternFish(code) || code == Pieces.PEARL

    private fun litCells(state: GameState): Int =
        (0 until state.size).count {
            state.playable[it] &&
                state.floorFlags[it] and pt.tiago.nightcrush.core.model.FloorFlags.GLOW != 0
        }

    private fun ratio(value: Int, target: Int): Double =
        if (target <= 0) 1.0 else (value.toDouble() / target).coerceAtMost(1.0)

    private fun completion(progress: IntArray, targets: IntArray): Double {
        var done = 0
        var total = 0
        for (i in targets.indices) {
            if (targets[i] <= 0) continue
            total++
            if (progress[i] >= targets[i]) done++
        }
        return if (total == 0) 1.0 else done.toDouble() / total
    }

    /** Counts how many orders and objectives are complete, for reporting. */
    fun objectiveProgress(state: GameState): Pair<Int, Int> {
        var done = 0
        var total = 0
        state.objectives.forEachIndexed { index, objective ->
            total++
            val slice = state.progress[index]
            val complete = when (objective) {
                is Objective.CollectColors -> (0 until 6).all { objective.counts[it] <= 0 || slice[it] >= objective.counts[it] }
                is Objective.ClearSilt -> slice[0] >= objective.target
                is Objective.DeliverLanterns -> slice[0] >= objective.target
                is Objective.Orders -> objective.orders.withIndex().all { (slot, order) -> slice[slot] >= order.target }
                is Objective.ClearBlockers -> (0 until 6).all { objective.counts[it] <= 0 || slice[it] >= objective.counts[it] }
                is Objective.SpreadGlow -> litCells(state) >= state.playableCount()
                is Objective.FreeSeahorses -> slice[0] >= objective.target
            }
            if (complete) done++
        }
        return done to total
    }

    /** Cheap label used in reports. */
    fun describeOrders(state: GameState): String = state.objectives.joinToString(";") { objective ->
        when (objective) {
            is Objective.CollectColors -> "collect"
            is Objective.ClearSilt -> "silt"
            is Objective.DeliverLanterns -> "lanterns"
            is Objective.Orders -> "orders:" + objective.orders.joinToString("+") { it.key() }
            is Objective.ClearBlockers -> "blockers"
            is Objective.SpreadGlow -> "glow"
            is Objective.FreeSeahorses -> "seahorses"
        }
    }
}
