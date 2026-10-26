package pt.tiago.nightcrush.leveltool

import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.model.CellSpec
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.FloorSpec
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.LimitSpec
import pt.tiago.nightcrush.core.model.MoveSpec
import pt.tiago.nightcrush.core.model.ObjectiveSpec
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.PieceColor
import pt.tiago.nightcrush.core.model.PieceSpec
import pt.tiago.nightcrush.core.model.PuzzleEntry
import pt.tiago.nightcrush.core.model.PuzzleFile
import pt.tiago.nightcrush.core.model.StarsSpec
import pt.tiago.nightcrush.core.model.VariantSpec
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.rules.GameEvent
import pt.tiago.nightcrush.core.rules.PuzzleSolver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * The 100 puzzles (design 9): fixed boards with no refills where the marked
 * cells (one silt layer each) must be cleared in exactly N moves.
 *
 * A candidate is a random board without matches. A random playout of N moves
 * decides the marks: every cell cleared by the last move, plus one cleared by
 * the first. The solver then certifies it: some N-move sequence clears every
 * mark, and no sequence of N-1 moves does. Later packs also cap the number
 * of winning sequences, and each pack is ordered from the most to the fewest.
 */
object Puzzles {

    /** [maxSolutions] keeps later packs tight: fewer winning lines means more thinking. */
    class Pack(val number: Int, val size: Int, val colors: Int, val moves: Int, val maxSolutions: Int)

    val packs = listOf(
        Pack(1, 6, 4, 2, 24),
        Pack(2, 6, 4, 3, 10),
        Pack(3, 7, 5, 3, 5),
        Pack(4, 7, 5, 4, 3),
    )

    const val PER_PACK = 25
    val path: Path = Path.of("content/puzzles/puzzles.json")

    fun generate(args: Commands.Args): Int {
        val seed = args.optional("seed")?.toLong() ?: 20261025L
        val onlyPack = args.int("pack", 0)
        val existing = if (path.exists()) levelJson.decodeFromString(PuzzleFile.serializer(), Files.readString(path)).puzzles else emptyList()
        val puzzles = existing.filter { onlyPack != 0 && it.pack != onlyPack }.toMutableList()
        for (pack in packs) {
            if (onlyPack != 0 && pack.number != onlyPack) continue
            val random = java.util.Random(seed * 31 + pack.number)
            val made = ArrayList<Pair<Int, PuzzleEntry>>()
            var tried = 0
            val started = System.currentTimeMillis()
            while (made.size < PER_PACK) {
                tried++
                val puzzle = candidate(pack, 0, random) ?: continue
                made.add(solutions(puzzle, pack.maxSolutions + 1) to puzzle)
            }
            // Easiest first: the more winning lines, the earlier in the pack.
            made.sortedByDescending { it.first }.forEachIndexed { index, (_, puzzle) ->
                val number = (pack.number - 1) * PER_PACK + index + 1
                puzzles.add(renumber(puzzle, number))
            }
            val counts = made.map { it.first }
            println("puzzles: pack ${pack.number}: ${made.size} puzzles from $tried boards in ${(System.currentTimeMillis() - started) / 1000} s, solutions ${counts.min()}..${counts.max()}")
        }
        val file = PuzzleFile(engineVersion = ENGINE_VERSION, puzzles = puzzles.sortedBy { it.number })
        writeText(path, levelJsonCompact.encodeToString(PuzzleFile.serializer(), file))
        println("puzzles: ${file.puzzles.size} puzzles -> $path")
        return 0
    }

    /** Replays every stored solution; the build runs this with the certificates. */
    fun verify(): Int {
        if (!path.exists()) return 0
        val file = levelJson.decodeFromString(PuzzleFile.serializer(), Files.readString(path))
        if (file.engineVersion != ENGINE_VERSION) {
            println("puzzles: engine ${file.engineVersion} != $ENGINE_VERSION")
            return 1
        }
        var failed = 0
        for (puzzle in file.puzzles) {
            var state = LevelBuilder.start(puzzle.level, Difficulty.EASY)
            for (move in puzzle.solution) state = GameEngine.applyMove(state, move.toMove(), recordEvents = false).state
            if (state.outcome !is Outcome.Won || puzzle.solution.size != puzzle.moves) {
                println("puzzles: puzzle ${puzzle.number} does not replay to a win")
                failed++
            }
        }
        println("puzzles: ${file.puzzles.size - failed} verified, $failed failed")
        return if (failed == 0) 0 else 1
    }

    private fun candidate(pack: Pack, number: Int, random: java.util.Random): PuzzleEntry? {
        val colors = PieceColor.entries.take(pack.colors)
        val grid = randomBoard(pack.size, colors.size, random)
        val open = level(number, pack, colors.map { it.name }, grid, marks = emptySet(), moves = 99)
        var state = LevelBuilder.start(open, Difficulty.EASY)
        if (GameEngine.validSwaps(state).size < 4) return null

        // A random playout of exactly N moves, recording where each move cleared pieces.
        val clearedByMove = ArrayList<Set<Int>>()
        repeat(pack.moves) {
            val swaps = GameEngine.validSwaps(state)
            if (swaps.isEmpty()) return null
            val result = GameEngine.applyMove(state, swaps[random.nextInt(swaps.size)])
            clearedByMove.add(result.events.filterIsInstance<GameEvent.PieceCleared>().map { it.cell.r * pack.size + it.cell.c }.toSet())
            state = result.state
            if (state.outcome != Outcome.Playing) return null
        }
        val first = clearedByMove.first().filter { cell -> clearedByMove.drop(1).none { cell in it } }
        if (first.isEmpty()) return null
        val marks = clearedByMove.last() + first[random.nextInt(first.size)]
        if (marks.size !in 4..9) return null

        val puzzle = level(number, pack, colors.map { it.name }, grid, marks, pack.moves)
        val solution = PuzzleSolver.solve(LevelBuilder.start(puzzle, Difficulty.EASY), pack.moves) ?: return null
        if (solution.size != pack.moves) return null
        // Exactly N: with one move fewer there must be no way to clear every mark.
        val shorter = level(number, pack, colors.map { it.name }, grid, marks, pack.moves - 1)
        if (PuzzleSolver.solve(LevelBuilder.start(shorter, Difficulty.EASY), pack.moves - 1) != null) return null
        val entry = PuzzleEntry(number = number, pack = pack.number, moves = pack.moves, level = puzzle, solution = solution.map { MoveSpec.of(it) })
        return if (solutions(entry, pack.maxSolutions + 1) <= pack.maxSolutions) entry else null
    }

    /** Winning move sequences of the puzzle, counted up to [cap]. */
    private fun solutions(puzzle: PuzzleEntry, cap: Int): Int {
        fun count(state: pt.tiago.nightcrush.core.model.GameState, depth: Int, found: Int): Int {
            var total = found
            for (swap in GameEngine.validSwaps(state)) {
                if (total >= cap) return total
                val next = GameEngine.applyMove(state, swap, recordEvents = false).state
                when {
                    next.outcome is Outcome.Won -> total++
                    next.outcome == Outcome.Playing && depth > 1 -> total = count(next, depth - 1, total)
                }
            }
            return total
        }
        return count(LevelBuilder.start(puzzle.level, Difficulty.EASY), puzzle.moves, 0)
    }

    /** Renames only: the seed stays, because lanternfish targets depend on it and the solution was certified with it. */
    private fun renumber(puzzle: PuzzleEntry, number: Int): PuzzleEntry {
        return puzzle.copy(
            number = number,
            level = puzzle.level.copy(
                id = "puzzle-%03d".format(number),
                ordinal = number,
                titlePt = "Quebra-cabeças %d".format(number),
                titleEn = "Puzzle %d".format(number),
            ),
        )
    }

    /** Fills the board left to right, top to bottom, never making three in a row. */
    private fun randomBoard(size: Int, colors: Int, random: java.util.Random): IntArray {
        val grid = IntArray(size * size)
        for (i in grid.indices) {
            val r = i / size
            val c = i % size
            while (true) {
                val color = random.nextInt(colors)
                val row = c >= 2 && grid[i - 1] == color && grid[i - 2] == color
                val column = r >= 2 && grid[i - size] == color && grid[i - 2 * size] == color
                if (!row && !column) {
                    grid[i] = color
                    break
                }
            }
        }
        return grid
    }

    private fun level(number: Int, pack: Pack, colors: List<String>, grid: IntArray, marks: Set<Int>, moves: Int): LevelFile {
        val cells = grid.indices.map { i ->
            CellSpec(
                r = i / pack.size,
                c = i % pack.size,
                piece = PieceSpec(kind = "normal", color = colors[grid[i]]),
                floor = if (i in marks) FloorSpec(silt = 1) else null,
            )
        }
        val variant = VariantSpec(
            seedHex = "%016x".format(number.toLong()),
            width = pack.size,
            height = pack.size,
            mask = List(pack.size) { "X".repeat(pack.size) },
            colors = colors,
            cells = cells,
            spawners = emptyList(),
            limit = LimitSpec("moves", moves),
            // Without marks the goal can never complete, which is what the playout needs.
            objectives = listOf(ObjectiveSpec(type = "silt", count = if (marks.isEmpty()) 999 else marks.size)),
            stars = StarsSpec(two = 1, three = 1),
            puzzle = true,
        )
        return LevelFile(
            engineVersion = ENGINE_VERSION,
            id = "puzzle-%03d".format(number),
            ordinal = number,
            worldId = "puzzles",
            titlePt = "Quebra-cabeças %d".format(number),
            titleEn = "Puzzle %d".format(number),
            familyId = "puzzle-pack-${pack.number}",
            variants = mapOf("easy" to variant),
        )
    }
}
