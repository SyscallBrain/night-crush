package pt.tiago.nightcrush.game

import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.model.BlockerSpec
import pt.tiago.nightcrush.core.model.CellSpec
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.LimitSpec
import pt.tiago.nightcrush.core.model.PieceSpec
import pt.tiago.nightcrush.core.model.SpawnerSpec
import pt.tiago.nightcrush.core.model.StarsSpec
import pt.tiago.nightcrush.core.model.VariantSpec
import pt.tiago.nightcrush.core.random.SplitMix64
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.rules.MatchFinder

/**
 * Boards for the free modes that are not tied to campaign levels (Zen and the
 * 90 second rush). They are synthesized in memory with the same level format,
 * so both go through the same engine and the same rules.
 */
object FreeBoards {

    fun zen(seed: Long): LevelFile = synthetic(
        id = "zen",
        width = 8,
        height = 8,
        colors = 5,
        limit = LimitSpec("moves", 99_999),
        seed = seed,
        oilSlicks = 0,
    )

    fun timeAttack(seed: Long, difficulty: Difficulty): LevelFile {
        val colors = when (difficulty) {
            Difficulty.EASY -> 5
            Difficulty.MEDIUM -> 6
            Difficulty.HARD -> 6
        }
        val seconds = when (difficulty) {
            Difficulty.EASY -> 105
            else -> 90
        }
        return synthetic(
            id = "time-attack",
            width = 8,
            height = 8,
            colors = colors,
            limit = LimitSpec("time", seconds),
            seed = seed,
            oilSlicks = if (difficulty == Difficulty.HARD) 2 else 0,
        )
    }

    private fun synthetic(
        id: String,
        width: Int,
        height: Int,
        colors: Int,
        limit: LimitSpec,
        seed: Long,
        oilSlicks: Int,
    ): LevelFile {
        val names = listOf("BLUE", "YELLOW", "PURPLE", "TEAL", "GREEN", "CORAL").take(colors)
        val mask = List(height) { "X".repeat(width) }
        val stream = SplitMix64(seed)
        val cells = fillBoard(width, height, names, stream, oilSlicks)
        val spawners = (0 until width).map { SpawnerSpec(it) }
        val variant = VariantSpec(
            seedHex = "%016x".format(stream.nextLong()),
            width = width,
            height = height,
            mask = mask,
            colors = names,
            cells = cells,
            spawners = spawners,
            limit = limit,
            objectives = emptyList(),
            stars = StarsSpec(1000, 2000),
        )
        return LevelFile(
            engineVersion = ENGINE_VERSION,
            id = id,
            ordinal = 0,
            worldId = "free",
            titlePt = id,
            titleEn = id,
            familyId = "free",
            variants = mapOf("easy" to variant, "medium" to variant, "hard" to variant),
        )
    }

    private fun fillBoard(
        width: Int,
        height: Int,
        colors: List<String>,
        stream: SplitMix64,
        oilSlicks: Int,
    ): List<CellSpec> {
        repeat(60) {
            val placed = arrayOfNulls<String>(width * height)
            val blocked = HashSet<Int>()
            repeat(oilSlicks) {
                val index = stream.nextInt(width * height)
                if (index !in blocked) blocked.add(index)
            }
            fun colorAt(r: Int, c: Int): String? =
                if (r in 0 until height && c in 0 until width) placed[r * width + c] else null

            for (r in 0 until height) {
                for (c in 0 until width) {
                    val index = r * width + c
                    if (index in blocked) continue
                    val forbidden = HashSet<String>()
                    if (c >= 2) colorAt(r, c - 1)?.let { if (it == colorAt(r, c - 2)) forbidden.add(it) }
                    if (r >= 2) colorAt(r - 1, c)?.let { if (it == colorAt(r - 2, c)) forbidden.add(it) }
                    if (r >= 1 && c >= 1) {
                        colorAt(r - 1, c - 1)?.let { corner ->
                            if (corner == colorAt(r - 1, c) && corner == colorAt(r, c - 1)) forbidden.add(corner)
                        }
                    }
                    val available = colors.filter { it !in forbidden }
                    placed[index] = if (available.isEmpty()) colors[stream.nextInt(colors.size)]
                    else available[stream.nextInt(available.size)]
                }
            }
            val cells = ArrayList<CellSpec>(width * height)
            for (r in 0 until height) {
                for (c in 0 until width) {
                    val index = r * width + c
                    if (index in blocked) {
                        cells.add(CellSpec(r, c, blocker = BlockerSpec("oil")))
                    } else {
                        cells.add(CellSpec(r, c, piece = PieceSpec("normal", placed[index])))
                    }
                }
            }
            val candidate = VariantSpec(
                seedHex = "%016x".format(stream.nextLong()),
                width = width,
                height = height,
                mask = List(height) { "X".repeat(width) },
                colors = colors,
                cells = cells,
                spawners = (0 until width).map { SpawnerSpec(it) },
                limit = LimitSpec("moves", 100),
                objectives = emptyList(),
                stars = StarsSpec(1000, 2000),
            )
            val wrapper = LevelFile(
                engineVersion = ENGINE_VERSION,
                id = "free",
                ordinal = 0,
                worldId = "free",
                titlePt = "free",
                variants = mapOf("easy" to candidate),
            )
            val state = LevelBuilder.start(wrapper, Difficulty.EASY)
            if (MatchFinder.find(state).isEmpty() && GameEngine.hasValidMove(state)) return cells
        }
        return emptyList()
    }
}
