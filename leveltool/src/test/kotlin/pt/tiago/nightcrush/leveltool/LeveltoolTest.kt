package pt.tiago.nightcrush.leveltool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.rules.MatchFinder

class GenerationTest {

    @Test
    fun `every generated level opens without a match and with a move`() {
        for (ordinal in intArrayOf(1, 7, 25, 50, 51, 123, 300, 451, 599)) {
            val level = Generation.level(ordinal, 20261013L)
            assertEquals("level-%04d".format(ordinal), level.id)
            for (difficulty in Difficulty.entries) {
                val state = LevelBuilder.start(level, difficulty)
                assertTrue("initial match at $ordinal/${difficulty.name}", MatchFinder.find(state).isEmpty())
                assertTrue("no move at $ordinal/${difficulty.name}", GameEngine.hasValidMove(state))
            }
        }
    }

    @Test
    fun `full moon levels are every tenth and intro levels carry tutorials`() {
        for (position in 1..50) {
            val level = Generation.level(position, 1L)
            assertEquals(position % 10 == 0, level.isFullMoon)
        }
        assertTrue(Generation.level(2, 1L).tutorial != null)
        assertNull(Generation.level(3, 1L).tutorial)
    }

    @Test
    fun `a family never repeats in a row and never more than four times per world`() {
        for (world in 1..12) {
            val families = (1..50).map { Generation.level((world - 1) * 50 + it, 5L).familyId }
            for (index in 1 until families.size) {
                assertTrue("consecutive repeat in world $world: ${families[index]}", families[index] != families[index - 1])
            }
            val counts = families.groupingBy { it }.eachCount()
            assertTrue("family overused in world $world: $counts", counts.values.all { it <= 4 })
        }
    }

    @Test
    fun `world one stays short and gentle`() {
        val easy = Generation.level(1, 9L).variants["easy"]!!
        assertTrue(easy.colors.size in 4..5)
        assertEquals(9, easy.width)
        assertEquals(9, easy.height)
    }

    @Test
    fun `hard variants use six colours and fewer moves than easy`() {
        val level = Generation.level(200, 3L)
        val easy = level.variants["easy"]!!
        val hard = level.variants["hard"]!!
        assertEquals(6, hard.colors.size)
        assertTrue(hard.limit.value < easy.limit.value)
    }

    @Test
    fun `generated levels pass the strict validator`() {
        for (ordinal in intArrayOf(1, 51, 101, 151, 251, 351, 451, 551)) {
            val level = Generation.level(ordinal, 42L)
            val issues = LevelValidator.validate(level, strict = true)
                .filterNot { it.contains("certificate") }
            assertTrue("validator issues at $ordinal: $issues", issues.isEmpty())
        }
    }

    @Test
    fun `every generated level passes strict validation`() {
        val problems = ArrayList<String>()
        for (ordinal in 1..600) {
            val level = Generation.level(ordinal, 20261013L)
            for (issue in LevelValidator.validate(level, strict = true)) {
                problems.add("level %04d: %s".format(ordinal, issue))
            }
        }
        assertTrue("${problems.size} issues, first: ${problems.take(6)}", problems.isEmpty())
    }
}

class SolverTest {

    private fun level(edit: (pt.tiago.nightcrush.core.model.VariantSpec) -> pt.tiago.nightcrush.core.model.VariantSpec): LevelFile {
        val base = Generation.level(300, 11L).variants["easy"]!!
        val variant = edit(base)
        return LevelFile(
            engineVersion = ENGINE_VERSION,
            id = "level-0300",
            ordinal = 300,
            worldId = "world-06",
            titlePt = "test",
            variants = mapOf("easy" to variant),
        )
    }

    @Test
    fun `the solver wins and its certificate replays exactly`() {
        val level = level { it.copy(limit = it.limit.copy(value = 30)) }
        val solution = Solver.solve(level, Difficulty.EASY)
        assertTrue("solver failed", solution != null)
        val certificate = CertificateFile(
            engineVersion = ENGINE_VERSION,
            levelId = level.id,
            difficulty = "easy",
            revision = level.revision,
            seedHex = level.variants["easy"]!!.seedHex,
            moves = solution!!.moves.map { it.toSpec() },
            afterStateHash = solution.hashes,
            finalScore = solution.score,
            movesUsed = solution.movesUsed,
            maxCascade = solution.maxCascade,
            twoStars = 1000,
            threeStars = 2000,
        )
        assertNull(CertificateVerifier.verify(level, Difficulty.EASY, certificate))
    }

    @Test
    fun `a tampered certificate is rejected`() {
        val level = level { it.copy(limit = it.limit.copy(value = 30)) }
        val solution = Solver.solve(level, Difficulty.EASY)!!
        val certificate = CertificateFile(
            engineVersion = ENGINE_VERSION,
            levelId = level.id,
            difficulty = "easy",
            revision = level.revision,
            seedHex = level.variants["easy"]!!.seedHex,
            moves = solution.moves.map { it.toSpec() },
            afterStateHash = solution.hashes.mapIndexed { index, hash ->
                if (index == 0) hash.reversed() else hash
            },
            finalScore = solution.score,
            movesUsed = solution.movesUsed,
            maxCascade = solution.maxCascade,
            twoStars = 1000,
            threeStars = 2000,
        )
        assertTrue(CertificateVerifier.verify(level, Difficulty.EASY, certificate) != null)
    }

    @Test
    fun `solve is deterministic for the same level`() {
        val level = level { it }
        val first = Solver.solve(level, Difficulty.EASY)
        val second = Solver.solve(level, Difficulty.EASY)
        assertEquals(first?.moves?.map { it.toSpec() }, second?.moves?.map { it.toSpec() })
    }
}

class HumanAgentTest {

    @Test
    fun `the win rate is deterministic for one seed`() {
        val level = Generation.level(400, 7L)
        val first = HumanAgent.winRate(level, Difficulty.MEDIUM, games = 12, seedBase = 99L)
        val second = HumanAgent.winRate(level, Difficulty.MEDIUM, games = 12, seedBase = 99L)
        assertEquals(first.rate, second.rate, 0.0)
    }

    @Test
    fun `a generous move budget never wins less than a tight one`() {
        val base = Generation.level(210, 5L)
        val generous = base.copy(
            variants = mapOf(
                "easy" to base.variants["easy"]!!.copy(
                    limit = base.variants["easy"]!!.limit.copy(value = 80),
                ),
            ),
        )
        val tight = base.copy(
            variants = mapOf(
                "easy" to base.variants["easy"]!!.copy(
                    limit = base.variants["easy"]!!.limit.copy(value = 2),
                ),
            ),
        )
        val generousRate = HumanAgent.winRate(generous, Difficulty.EASY, games = 10, seedBase = 3L).rate
        val tightRate = HumanAgent.winRate(tight, Difficulty.EASY, games = 10, seedBase = 3L).rate
        assertTrue("generous=$generousRate tight=$tightRate", generousRate >= tightRate)
    }

    @Test
    fun `calibration never cuts more than a fifth of the designed move budget`() {
        val base = Generation.level(210, 5L)
        val before = base.variants["easy"]!!.limit.value
        val (adjusted, _) = HumanAgent.calibrateLimit(base, Difficulty.EASY, games = 6, seedBase = 1L, step = 1)
        assertTrue(adjusted.variants["easy"]!!.limit.value >= (before * 0.8).toInt())
    }

    @Test
    fun `easier variants never get fewer moves than harder ones`() {
        val base = Generation.level(212, 5L)
        fun withLimit(name: String, value: Int) = name to base.variants[name]!!.let { it.copy(limit = it.limit.copy(value = value)) }
        val skewed = base.copy(variants = mapOf(withLimit("easy", 6), withLimit("medium", 9), withLimit("hard", 20)))
        val ordered = HumanAgent.orderLimits(skewed)
        assertEquals(20, ordered.variants["easy"]!!.limit.value)
        assertEquals(20, ordered.variants["medium"]!!.limit.value)
        assertEquals(20, ordered.variants["hard"]!!.limit.value)
    }
}

class ValidatorTest {

    @Test
    fun `a level with a broken mask is rejected`() {
        val base = Generation.level(60, 2L).variants["easy"]!!
        val broken = base.copy(mask = base.mask.mapIndexed { index, row -> if (index == 0) "." + row.drop(1) else row })
        val level = Generation.level(60, 2L).copy(variants = mapOf("easy" to broken))
        assertTrue(LevelValidator.validate(level, strict = true).isNotEmpty())
    }

    @Test
    fun `an engine mismatch is rejected`() {
        val level = Generation.level(61, 2L).copy(engineVersion = ENGINE_VERSION + 3)
        assertTrue(LevelValidator.validate(level, strict = true).isNotEmpty())
    }

    @Test
    fun `a move plus objective works end to end through the builder`() {
        val level = Generation.level(62, 2L)
        val state = LevelBuilder.start(level, Difficulty.EASY)
        val swaps = GameEngine.validSwaps(state)
        assertTrue(swaps.isNotEmpty())
        val result = GameEngine.applyMove(state, swaps.first())
        assertTrue(result.state.outcome != Outcome.Won || result.state.movesUsed == 1)
    }
}

class LanternAgentTest {

    @Test
    fun `the agent brings lanterns down to their exit within the designed limit`() {
        val level = (101..150).asSequence()
            .map { Generation.level(it, seed = it.toLong()) }
            .first { "lanterns" in it.learningTags && !it.isFullMoon }
        val rate = HumanAgent.winRate(level, Difficulty.MEDIUM, games = 20, seedBase = 7L)
        assertTrue("win rate ${rate.rate} on ${level.id}", rate.rate >= 0.3)
    }
}
