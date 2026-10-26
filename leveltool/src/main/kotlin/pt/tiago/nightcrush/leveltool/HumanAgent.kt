package pt.tiago.nightcrush.leveltool

import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.LimitType
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.PreLevelBooster
import pt.tiago.nightcrush.core.random.SplitMix64
import pt.tiago.nightcrush.core.rules.GameEngine
import kotlin.math.exp
import kotlin.math.floor

/**
 * A simulated "average human" for calibrating levels: greedy play with noise,
 * prefers special combinations most of the time, and never sees the future
 * beyond the immediate result of a swap.
 */
object HumanAgent {

    data class Result(val won: Boolean, val moves: Int, val score: Int, val boardedMoves: Int)

    fun playGame(
        level: LevelFile,
        difficulty: Difficulty,
        policySeed: Long,
        boosters: List<PreLevelBooster> = emptyList(),
    ): Result {
        var state = LevelBuilder.start(level, difficulty, boosters)
        val rng = SplitMix64(policySeed)
        val bestProbability = if (difficulty == Difficulty.HARD) 0.65 else 0.55
        val maxMoves = if (state.limitType == LimitType.MOVES) state.movesLeft else state.timeLeftSec / 4
        // One pathological board must not stall a whole certification run.
        val deadline = System.nanoTime() + 1_000_000_000L
        var used = 0
        while (state.outcome == Outcome.Playing && used < maxMoves) {
            if (System.nanoTime() > deadline) break
            val swaps = GameEngine.validSwaps(state)
            if (swaps.isEmpty()) break
            val now = Solver.evaluate(state)
            // A player sees that a lantern needs the cells under it cleared and sets
            // that up a move ahead; one-move greed never does, so look two deep.
            val lookAhead = state.lanternsOnBoard > 0
            val scored = ArrayList<Scored>(swaps.size)
            for (swap in swaps) {
                val result = GameEngine.applyMove(state, swap, recordEvents = false)
                var gain = when (result.state.outcome) {
                    is Outcome.Won -> 1e6
                    is Outcome.Lost -> -1e6
                    else -> Solver.evaluate(result.state) - now
                }
                if (lookAhead && result.state.outcome == Outcome.Playing &&
                    result.state.lanternsDelivered == state.lanternsDelivered
                ) {
                    gain += 0.5 * bestDelivery(result.state)
                }
                scored.add(Scored(swap, gain, isComboSwap(state, swap)))
            }
            val chosen = choose(scored, rng, bestProbability)
            state = GameEngine.applyMove(state, chosen, recordEvents = false).state
            used++
        }
        return Result(state.outcome is Outcome.Won, used, state.score, maxMoves)
    }

    /** How much the best next move gains by delivering a lantern, or 0. */
    private fun bestDelivery(state: GameState): Double {
        val now = Solver.evaluate(state)
        var best = 0.0
        for (swap in GameEngine.validSwaps(state)) {
            val next = GameEngine.applyMove(state, swap, recordEvents = false).state
            if (next.lanternsDelivered > state.lanternsDelivered) {
                best = maxOf(best, if (next.outcome is Outcome.Won) 1000.0 else Solver.evaluate(next) - now)
            }
        }
        return best
    }

    private class Scored(val move: Move.Swap, val gain: Double, val combo: Boolean)

    private fun isComboSwap(state: GameState, swap: Move.Swap): Boolean {
        val a = state.idx(swap.a.r, swap.a.c)
        val b = state.idx(swap.b.r, swap.b.c)
        val pa = state.pieces[a]
        val pb = state.pieces[b]
        fun special(code: Int) = pt.tiago.nightcrush.core.model.Pieces.isRay(code) ||
            pt.tiago.nightcrush.core.model.Pieces.isJelly(code) ||
            pt.tiago.nightcrush.core.model.Pieces.isLanternFish(code) ||
            code == pt.tiago.nightcrush.core.model.Pieces.PEARL
        return special(pa) && special(pb)
    }

    private fun choose(scored: List<Scored>, rng: SplitMix64, bestProbability: Double): Move.Swap {
        val best = scored.maxByOrNull { it.gain } ?: error("no candidates")
        // Special combinations are the player's favourite trick: 80% of the time.
        val combos = scored.filter { it.combo }
        if (combos.isNotEmpty() && rng.nextInt(100) < 80) {
            return combos.maxByOrNull { it.gain }!!.move
        }
        if (rng.nextInt(100) < (bestProbability * 100).toInt()) return best.move
        // Softmax over gains, temperature scaled to the spread of the options.
        val maxGain = scored.maxOf { it.gain }
        val minGain = scored.minOf { it.gain }
        val spread = (maxGain - minGain).coerceAtLeast(1.0)
        val temperature = spread / 4.0
        val weights = scored.map { exp((it.gain - maxGain) / temperature) }
        val total = weights.sum()
        var roll = (rng.nextLong().ushr(1).toDouble() / Long.MAX_VALUE.toDouble()) * total
        for (i in scored.indices) {
            roll -= weights[i]
            if (roll <= 0) return scored[i].move
        }
        return scored.last().move
    }

    fun winRate(
        level: LevelFile,
        difficulty: Difficulty,
        games: Int,
        seedBase: Long,
        boosters: List<PreLevelBooster> = emptyList(),
    ): WinRate {
        var wins = 0
        var moveSum = 0
        repeat(games) { game ->
            val result = playGame(level, difficulty, seedBase + game * 7919L, boosters)
            if (result.won) wins++
            moveSum += result.moves
        }
        return WinRate(wins.toDouble() / games, moveSum.toDouble() / games)
    }

    data class WinRate(val rate: Double, val averageMoves: Double)

    /** Target bands from the plan (section 5.3). */
    fun band(level: LevelFile, difficulty: Difficulty): ClosedFloatingPointRange<Double> {
        val intro = level.learningTags.contains("intro")
        return when {
            intro -> when (difficulty) {
                Difficulty.EASY -> 0.95..1.0
                Difficulty.MEDIUM -> 0.85..1.0
                Difficulty.HARD -> 0.70..1.0
            }
            level.isFullMoon -> when (difficulty) {
                Difficulty.EASY -> 0.65..0.75
                Difficulty.MEDIUM -> 0.30..0.40
                Difficulty.HARD -> 0.15..0.25
            }
            else -> when (difficulty) {
                Difficulty.EASY -> 0.85..0.95
                Difficulty.MEDIUM -> 0.60..0.75
                Difficulty.HARD -> 0.35..0.50
            }
        }
    }

    /**
     * Steps the limit until the rate lands in the band (at most [maxSteps]).
     * Too hard adds moves freely; too easy removes them one step at a time, never
     * below 80% of the designed limit, and stops as soon as a cut overshoots.
     */
    fun calibrateLimit(
        level: LevelFile,
        difficulty: Difficulty,
        games: Int,
        seedBase: Long,
        step: Int,
        maxSteps: Int = 12,
    ): Pair<LevelFile, WinRate> {
        val key = difficulty.name.lowercase()
        val designed = level.variants[key]?.limit?.value ?: return level to WinRate(0.0, 0.0)
        val floorValue = maxOf((designed * 0.8).toInt(), if (step == 1) 8 else 60)
        var current = level
        var last = WinRate(0.0, 0.0)
        var easier: Pair<LevelFile, WinRate>? = null
        repeat(maxSteps) {
            val variant = current.variants[key] ?: return current to last
            last = winRate(current, difficulty, games, seedBase)
            val target = band(current, difficulty)
            if (last.rate in target) return current to last
            val newValue = if (last.rate > target.endInclusive) {
                if (variant.limit.value - step < floorValue) return current to last
                easier = current to last
                variant.limit.value - step
            } else {
                // A cut went one step too far: keep the last limit that was too easy.
                easier?.let { return it }
                val adjustment = 1 + ((target.start - last.rate) * 15).toInt()
                variant.limit.value + adjustment * step
            }
            val updatedVariant = variant.copy(limit = variant.limit.copy(value = newValue))
            current = current.copy(variants = current.variants + (key to updatedVariant))
        }
        return current to last
    }

    /**
     * Easy never offers fewer moves (or seconds) than medium, nor medium fewer
     * than hard, whatever the agent measured: players expect that order.
     */
    fun orderLimits(level: LevelFile): LevelFile {
        val hard = level.variants["hard"]?.limit
        val medium = level.variants["medium"]?.limit
        val easy = level.variants["easy"]?.limit
        var out = level
        fun raise(name: String, floor: Int) {
            val variant = out.variants[name] ?: return
            if (variant.limit.value >= floor) return
            out = out.copy(variants = out.variants + (name to variant.copy(limit = variant.limit.copy(value = floor))))
        }
        if (medium != null && hard != null && medium.type == hard.type) raise("medium", hard.value)
        val mediumNow = out.variants["medium"]?.limit
        if (easy != null && mediumNow != null && easy.type == mediumNow.type) raise("easy", mediumNow.value)
        return out
    }

    /** Time levels are certified against floor(seconds / 4) decisions. */
    fun affordableMoves(state: GameState): Int =
        if (state.limitType == LimitType.MOVES) state.movesLeft else floor(state.timeLeftSec / 4.0).toInt()
}
