package pt.tiago.nightcrush.leveltool

import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.FloorFlags
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.LimitType
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.StarsSpec
import pt.tiago.nightcrush.core.model.MetricsSpec
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.rules.MatchFinder
import pt.tiago.nightcrush.core.serialization.StateHasher
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.math.roundToInt

/** All leveltool commands. Paths are relative to the repository root. */
object Commands {

    class Args(private val values: Map<String, String>) {
        fun value(key: String, default: String? = null): String =
            values[key] ?: default ?: error("Missing --$key")

        fun optional(key: String): String? = values[key]

        fun int(key: String, default: Int): Int = values[key]?.toInt() ?: default

        fun flag(key: String): Boolean = values[key] == "true"
    }

    private fun levelPath(ordinal: Int): Path {
        val world = (ordinal - 1) / 50 + 1
        return Path.of("content", "levels", "world-%02d".format(world), "level-%04d.json".format(ordinal))
    }

    private fun candidatePath(ordinal: Int): Path {
        val world = (ordinal - 1) / 50 + 1
        return Path.of("content", "candidates", "world-%02d".format(world), "level-%04d.json".format(ordinal))
    }

    private fun certificatePath(ordinal: Int, difficulty: Difficulty): Path =
        Path.of("content", "certificates", "level-%04d".format(ordinal), difficulty.jsonName + ".json")

    private val Difficulty.jsonName: String get() = name.lowercase()

    fun worldOf(ordinal: Int): Int = (ordinal - 1) / 50 + 1

    // ------------------------------------------------------------------ validate

    private val blockerIntroWorld = mapOf(
        Blockers.KIND_ORDER[0] to 4, // CORAL
        Blockers.KIND_ORDER[1] to 6, // OIL
        Blockers.KIND_ORDER[2] to 7, // SEAHORSE_BUBBLE
        Blockers.KIND_ORDER[3] to 10, // GIANT_OYSTER
        Blockers.KIND_ORDER[4] to 11, // SINKHOLE
        Blockers.KIND_ORDER[5] to 12, // OCTOPUS
    )

    fun validate(args: Args): Int {
        val strict = args.flag("strict")
        val catalogPath = Path.of(args.value("catalog"))
        if (!catalogPath.exists()) {
            println("validate: catalog not found: $catalogPath")
            return 2
        }
        val catalog = levelJson.decodeFromString(CatalogFile.serializer(), Files.readString(catalogPath))
        var issues = 0
        if (catalog.engineVersion != ENGINE_VERSION) {
            println("catalog engineVersion ${catalog.engineVersion} != $ENGINE_VERSION")
            issues++
        }
        for (world in catalog.worlds) {
            if (world.to - world.from + 1 != 50) {
                println("world ${world.id}: expected 50 levels, has ${world.to - world.from + 1}")
                issues++
            }
        }
        if (strict && catalog.worlds.size != 12) {
            println("expected 12 worlds, found ${catalog.worlds.size}")
            issues++
        }
        val ordinals = catalog.levels.map { it.ordinal }.sorted()
        if (strict && ordinals != (1..600).toList()) {
            println("expected ordinals 1..600 contiguous, found ${ordinals.size} entries")
            issues++
        }
        for (entry in catalog.levels.sortedBy { it.ordinal }) {
            val path = Path.of(entry.path)
            if (!path.exists()) {
                println("level ${entry.id}: missing file ${entry.path}")
                issues++
                continue
            }
            val actualHash = sha256File(path)
            if (actualHash != entry.sha256) {
                println("level ${entry.id}: catalog sha256 mismatch")
                issues++
            }
            val level = readLevel(path)
            if (level.id != entry.id || level.ordinal != entry.ordinal || level.worldId != entry.worldId) {
                println("level ${entry.id}: catalog entry does not match the file identity")
                issues++
            }
            if (level.revision != entry.revision) {
                println("level ${entry.id}: revision ${level.revision} != catalog ${entry.revision}")
                issues++
            }
            issues += LevelValidator.validate(level, strict).onEach { println("level ${level.id}: $it") }.size
            if (strict) {
                for (difficulty in Difficulty.entries) {
                    val certPath = certificatePath(entry.ordinal, difficulty)
                    if (!certPath.exists()) {
                        println("level ${entry.id}: missing ${difficulty.jsonName} certificate")
                        issues++
                        continue
                    }
                    val certificate = levelJson.decodeFromString(
                        CertificateFile.serializer(),
                        Files.readString(certPath),
                    )
                    val problem = CertificateVerifier.verify(level, difficulty, certificate)
                    if (problem != null) {
                        println("level ${entry.id} ${difficulty.jsonName}: $problem")
                        issues++
                    }
                }
            }
        }
        println(if (issues == 0) "validate: OK (${catalog.levels.size} levels)" else "validate: $issues issue(s)")
        return if (issues == 0) 0 else 1
    }

    // -------------------------------------------------------- verify certificates

    fun verifyCertificates(args: Args): Int {
        val catalogPath = Path.of(args.value("catalog"))
        val catalog = levelJson.decodeFromString(CatalogFile.serializer(), Files.readString(catalogPath))
        var verified = 0
        var failed = 0
        for (entry in catalog.levels.sortedBy { it.ordinal }) {
            val level = readLevel(Path.of(entry.path))
            for (difficulty in Difficulty.entries) {
                val certPath = certificatePath(entry.ordinal, difficulty)
                if (!certPath.exists()) {
                    println("level ${entry.id} ${difficulty.jsonName}: certificate missing")
                    failed++
                    continue
                }
                val certificate = levelJson.decodeFromString(CertificateFile.serializer(), Files.readString(certPath))
                val problem = CertificateVerifier.verify(level, difficulty, certificate)
                if (problem != null) {
                    println("level ${entry.id} ${difficulty.jsonName}: $problem")
                    failed++
                } else {
                    verified++
                }
            }
        }
        println("verify-certificates: $verified verified, $failed failed")
        // The puzzles ride along so the build checks every piece of shipped content.
        if (Puzzles.verify() != 0) return 1
        return if (failed == 0) 0 else 1
    }

    // ------------------------------------------------------------------- solving

    fun solve(args: Args): Int {
        val id = args.value("level")
        val difficultyArg = args.optional("difficulty")
        val ordinal = id.removePrefix("level-").toInt()
        val path = levelPath(ordinal)
        // One revision bump up front: the certificates' hashes include it.
        var level = readLevel(path).copy(revision = readLevel(path).revision + 1)
        val difficulties = difficultyArg?.let { listOf(Difficulty.valueOf(it.uppercase())) } ?: Difficulty.entries
        var failures = 0
        val certificates = ArrayList<Pair<Difficulty, CertificateFile>>()
        for (difficulty in difficulties) {
            val solution = Solver.solve(level, difficulty)
            if (solution == null) {
                println("level ${level.id} ${difficulty.jsonName}: no solution found")
                failures++
                continue
            }
            level = applyStars(level, difficulty, solution.score)
            val variant = level.variants[difficulty.jsonName]!!
            certificates.add(
                difficulty to CertificateFile(
                    engineVersion = ENGINE_VERSION,
                    levelId = level.id,
                    difficulty = difficulty.jsonName,
                    revision = level.revision,
                    seedHex = variant.seedHex,
                    moves = solution.moves.map { it.toSpec() },
                    afterStateHash = solution.hashes,
                    finalScore = solution.score,
                    movesUsed = solution.movesUsed,
                    maxCascade = solution.maxCascade,
                    twoStars = variant.stars.two,
                    threeStars = variant.stars.three,
                ),
            )
            println(
                "level ${level.id} ${difficulty.jsonName}: solved in ${solution.movesUsed} moves, " +
                    "score ${solution.score}, stars ${variant.stars.two}/${variant.stars.three}",
            )
        }
        writeText(path, levelJson.encodeToString(LevelFile.serializer(), level))
        for ((difficulty, certificate) in certificates) {
            writeText(certificatePath(ordinal, difficulty), levelJson.encodeToString(CertificateFile.serializer(), certificate))
        }
        return if (failures == 0) 0 else 1
    }

    private fun applyStars(level: LevelFile, difficulty: Difficulty, score: Int): LevelFile {
        val three = round100((score * 0.85).roundToInt())
        val two = round100((three * 0.6).roundToInt())
        val variant = level.variants[difficulty.jsonName]!!.copy(
            stars = StarsSpec(two = two, three = three),
            expectedMetrics = (level.variants[difficulty.jsonName]!!.expectedMetrics ?: MetricsSpec())
                .copy(solverMoves = level.variants[difficulty.jsonName]!!.expectedMetrics?.solverMoves ?: 0),
        )
        return level.copy(variants = level.variants + (difficulty.jsonName to variant))
    }

    // ---------------------------------------------------------------- calibrate

    fun calibrate(args: Args): Int {
        val id = args.value("level")
        val ordinal = id.removePrefix("level-").toInt()
        val games = args.int("games", 120)
        val path = levelPath(ordinal)
        var level = readLevel(path)
        for (difficulty in Difficulty.entries) {
            val step = if (limitType(level, difficulty) == LimitType.MOVES) 1 else 5
            val (updated, rate) = HumanAgent.calibrateLimit(level, difficulty, games, 0x5EED + ordinal * 31L, step)
            level = updated
            val band = HumanAgent.band(level, difficulty)
            val variant = level.variants[difficulty.jsonName]!!
            val metrics = (variant.expectedMetrics ?: MetricsSpec()).copy(
                humanWinRate = rate.rate,
                solverMoves = variant.expectedMetrics?.solverMoves ?: 0,
            )
            level = level.copy(variants = level.variants + (difficulty.jsonName to variant.copy(expectedMetrics = metrics)))
            println(
                "level ${level.id} ${difficulty.jsonName}: limit ${effectiveLimit(level, difficulty)}, " +
                    "win rate %.3f (band %.2f..%.2f)".format(rate.rate, band.start, band.endInclusive),
            )
        }
        writeText(path, levelJson.encodeToString(LevelFile.serializer(), level))
        return 0
    }

    private fun limitType(level: LevelFile, difficulty: Difficulty): LimitType =
        if (level.variants[difficulty.jsonName]!!.limit.type == "time") LimitType.TIME else LimitType.MOVES

    private fun effectiveLimit(level: LevelFile, difficulty: Difficulty): Int =
        level.variants[difficulty.jsonName]!!.limit.value

    // -------------------------------------------------------------------- report

    fun report(args: Args): Int {
        val output = Path.of(args.value("output", "report.md"))
        val catalogPath = Path.of(args.value("catalog", "content/catalog.json"))
        if (!catalogPath.exists()) {
            println("report: no catalog yet")
            return 1
        }
        val catalog = levelJson.decodeFromString(CatalogFile.serializer(), Files.readString(catalogPath))
        val rows = ArrayList<Row>()
        for (entry in catalog.levels.sortedBy { it.ordinal }) {
            val level = readLevel(Path.of(entry.path))
            for (difficulty in Difficulty.entries) {
                val variant = level.variants[difficulty.jsonName] ?: continue
                rows.add(
                    Row(
                        ordinal = entry.ordinal,
                        world = worldOf(entry.ordinal),
                        family = level.familyId,
                        difficulty = difficulty,
                        objectives = variant.objectives.joinToString("+") { it.type },
                        moves = variant.limit.value,
                        winRate = variant.expectedMetrics?.humanWinRate ?: -1.0,
                        solverMoves = variant.expectedMetrics?.solverMoves ?: 0,
                        inBand = variant.expectedMetrics?.let {
                            val band = HumanAgent.band(level, difficulty)
                            it.humanWinRate in band
                        } ?: false,
                    ),
                )
            }
        }
        val text = buildString {
            appendLine("# NightCrush content report")
            appendLine()
            appendLine("Levels: ${catalog.levels.size}, variants measured: ${rows.size}")
            appendLine()
            appendLine("## Win rates by difficulty")
            appendLine()
            appendLine("| Difficulty | Variants | In band | Average win rate | Average moves |")
            appendLine("|---|---|---|---|---|")
            for (difficulty in Difficulty.entries) {
                val subset = rows.filter { it.difficulty == difficulty && it.winRate >= 0 }
                if (subset.isEmpty()) continue
                appendLine(
                    "| ${difficulty.name} | ${subset.size} | ${subset.count { it.inBand }} | " +
                        "%.3f | %.1f |".format(subset.map { it.winRate }.average(), subset.map { it.moves }.average()),
                )
            }
            appendLine()
            appendLine("## Families")
            appendLine()
            rows.groupBy { it.family }.forEach { (family, group) ->
                appendLine("- $family: ${group.size} variants")
            }
            appendLine()
            appendLine("## Outliers (outside the target band)")
            appendLine()
            val outliers = rows.filter { !it.inBand && it.winRate >= 0 }
            if (outliers.isEmpty()) {
                appendLine("None.")
            } else {
                for (row in outliers.sortedBy { it.winRate }) {
                    appendLine(
                        "- level ${"%04d".format(row.ordinal)} ${row.difficulty.name}: " +
                            "win rate %.3f, moves ${row.moves}, ${row.objectives}".format(row.winRate),
                    )
                }
            }
        }
        writeText(output, text)
        println("report: ${rows.size} variants, ${rows.count { !it.inBand && it.winRate >= 0 }} outliers -> $output")
        return 0
    }

    private class Row(
        val ordinal: Int,
        val world: Int,
        val family: String,
        val difficulty: Difficulty,
        val objectives: String,
        val moves: Int,
        val winRate: Double,
        val solverMoves: Int,
        val inBand: Boolean,
    )

    // -------------------------------------------------------------- export assets

    fun exportAssets(args: Args): Int {
        val catalogPath = Path.of(args.value("catalog"))
        val output = Path.of(args.value("output"))
        if (!catalogPath.exists()) {
            println("export-assets: catalog not found: $catalogPath")
            return 2
        }
        val catalog = levelJson.decodeFromString(CatalogFile.serializer(), Files.readString(catalogPath))
        if (catalog.engineVersion != ENGINE_VERSION) {
            println("export-assets: catalog engine ${catalog.engineVersion} != $ENGINE_VERSION")
            return 2
        }
        // Verify every level hash before exporting anything.
        for (entry in catalog.levels) {
            val path = Path.of(entry.path)
            if (!path.exists() || sha256File(path) != entry.sha256) {
                println("export-assets: level ${entry.id} failed its integrity check")
                return 2
            }
        }
        val files = ArrayList<AssetFile>()
        Files.createDirectories(output)
        for (entry in catalog.levels) {
            val source = Path.of(entry.path)
            val relative = "levels/world-%02d/level-%04d.json".format(worldOf(entry.ordinal), entry.ordinal)
            val target = output.resolve(relative)
            writeText(target, Files.readString(source))
            files.add(AssetFile(relative, sha256File(target)))
        }
        writeText(output.resolve("catalog.json"), Files.readString(catalogPath))
        files.add(AssetFile("catalog.json", sha256File(output.resolve("catalog.json"))))
        if (Puzzles.path.exists()) {
            writeText(output.resolve("puzzles.json"), Files.readString(Puzzles.path))
            files.add(AssetFile("puzzles.json", sha256File(output.resolve("puzzles.json"))))
        }
        val manifest = AssetManifest(
            engineVersion = ENGINE_VERSION,
            catalogSha256 = sha256File(catalogPath),
            files = files.sortedBy { it.path },
        )
        writeText(output.resolve("manifest.json"), levelJson.encodeToString(AssetManifest.serializer(), manifest))
        println("export-assets: ${files.size} files -> $output")
        return 0
    }

    // ------------------------------------------------------------------- retitle

    /** Rewrites every level title from [Generation.title] and rebuilds the catalog; boards are untouched. */
    fun retitle(args: Args): Int {
        var changed = 0
        for (ordinal in 1..600) {
            val path = levelPath(ordinal)
            if (!path.exists()) continue
            val level = readLevel(path)
            val pt = Generation.title(ordinal, portuguese = true)
            val en = Generation.title(ordinal, portuguese = false)
            if (level.titlePt == pt && level.titleEn == en) continue
            writeText(path, levelJson.encodeToString(LevelFile.serializer(), level.copy(titlePt = pt, titleEn = en)))
            changed++
        }
        CatalogBuilder.rebuild(Path.of("content"))
        println("retitle: $changed levels renamed")
        return 0
    }

    // ---------------------------------------------------------------- daily pool

    fun dailyPool(args: Args): Int {
        val pool = DailyPool.eligible()
        val target = Path.of(args.value("output", "content/daily-pool.json"))
        writeText(target, levelJson.encodeToString(DailyPool.PoolFile.serializer(), DailyPool.PoolFile(pool)))
        println("daily-pool: ${pool.size} eligible levels -> $target")
        return 0
    }

    // ------------------------------------------------------------------ generate

    fun generate(args: Args): Int {
        val world = args.int("world", 0)
        require(world in 1..12) { "--world must be 1..12" }
        val from = args.int("from", (world - 1) * 50 + 1)
        val to = args.int("to", world * 50)
        val seed = args.optional("seed")?.toLong() ?: 20261013L
        val planSeed = args.optional("plan-seed")?.toLong() ?: 20261013L
        var written = 0
        for (ordinal in from..to) {
            val level = Generation.level(ordinal, seed, planSeed)
            writeText(candidatePath(ordinal), levelJson.encodeToString(LevelFile.serializer(), level))
            written++
        }
        println("generate: $written candidate levels in content/candidates/world-%02d".format(world))
        return 0
    }

    // ------------------------------------------------------------------- certify

    fun certify(args: Args): Int {
        val world = args.int("world", 0)
        require(world in 1..12) { "--world must be 1..12" }
        val games = args.int("games", 120)
        val seed = args.optional("seed")?.toLong() ?: 20261013L
        val promote = !args.flag("no-promote")
        val deep = args.flag("deep")
        val options = if (deep) Solver.Options.DEEP else Solver.Options()
        val maxAttempts = if (deep) 3 else 2
        val from = (world - 1) * 50 + 1
        val to = world * 50
        val tasks = (from..to).mapNotNull { ordinal ->
            val candidate = candidatePath(ordinal)
            val published = levelPath(ordinal)
            when {
                candidate.exists() -> ordinal to readLevel(candidate)
                published.exists() -> ordinal to readLevel(published)
                else -> null
            }
        }
        println("certify: world $world, ${tasks.size} levels, $games games per calibration step")
        val pool = Executors.newFixedThreadPool(maxOf(1, Runtime.getRuntime().availableProcessors() - 1))
        var certified = 0
        var rejected = 0
        try {
            val results = pool.invokeAll(
                tasks.map { (ordinal, level) ->
                    java.util.concurrent.Callable {
                        val outcome = certifyLevel(ordinal, level, games, seed, options, maxAttempts)
                        outcome
                    }
                },
            )
            for (future in results) {
                val outcome = future.get()
                if (outcome.promoted) certified++ else rejected++
                println(outcome.message)
            }
            if (promote && certified > 0) {
                CatalogBuilder.rebuild(Path.of("content"))
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.HOURS)
        }
        println("certify: $certified promoted, $rejected rejected")
        return if (certified > 0) 0 else 1
    }

    private class CertifyOutcome(val promoted: Boolean, val message: String)

    private fun certifyLevel(
        ordinal: Int,
        startLevel: LevelFile,
        games: Int,
        seed: Long,
        options: Solver.Options = Solver.Options(),
        maxAttempts: Int = 2,
    ): CertifyOutcome {
        // One revision bump per certify run, before anything is written, so the
        // level file and its certificates always agree.
        var level = startLevel.copy(revision = startLevel.revision + 1)
        val world = worldOf(ordinal)
        val certificates = ArrayList<Pair<String, CertificateFile>>(3)
        val rates = HashMap<Difficulty, HumanAgent.WinRate>()
        for (difficulty in Difficulty.entries) {
            if (level.variants[difficulty.jsonName] == null) continue
            val step = if (limitType(level, difficulty) == LimitType.MOVES) 1 else 5
            val (calibrated, rate) = HumanAgent.calibrateLimit(level, difficulty, games, seed + ordinal * 31L, step)
            level = calibrated
            rates[difficulty] = rate
        }
        level = HumanAgent.orderLimits(level)
        for (difficulty in Difficulty.entries) {
            if (level.variants[difficulty.jsonName] == null) continue
            val rate = rates.getValue(difficulty)
            println(
                "level %04d %s: limit %d, win rate %.3f".format(
                    ordinal, difficulty.jsonName, effectiveLimit(level, difficulty), rate.rate,
                ),
            )
            var solved = Solver.solve(level, difficulty, options = options)
            var attempts = 0
            while (solved == null && attempts < maxAttempts) {
                // Give the solver a little more room, then try again.
                val variant = level.variants[difficulty.jsonName]!!
                val bump = if (limitType(level, difficulty) == LimitType.MOVES) 4 else 20
                level = level.copy(
                    variants = level.variants + (
                        difficulty.jsonName to variant.copy(limit = variant.limit.copy(value = variant.limit.value + bump))
                        ),
                )
                attempts++
                solved = Solver.solve(level, difficulty, options = options)
            }
            if (solved == null) {
                // Nothing is written: the level keeps its previous state and the gap
                // filler regenerates this ordinal with a different seed.
                if (candidatePath(ordinal).exists()) Files.deleteIfExists(candidatePath(ordinal))
                return CertifyOutcome(false, "level %04d %s: unsolved, rejected".format(ordinal, difficulty.jsonName))
            }
            val variantBefore = level.variants[difficulty.jsonName]!!
            val three = round100((solved.score * 0.85).roundToInt())
            val two = round100((three * 0.6).roundToInt())
            val variant = variantBefore.copy(
                stars = StarsSpec(two = two, three = three),
                expectedMetrics = (variantBefore.expectedMetrics ?: MetricsSpec()).copy(
                    humanWinRate = rate.rate,
                    solverMoves = solved.movesUsed,
                ),
            )
            level = level.copy(variants = level.variants + (difficulty.jsonName to variant))
            certificates.add(
                difficulty.jsonName to CertificateFile(
                    engineVersion = ENGINE_VERSION,
                    levelId = level.id,
                    difficulty = difficulty.jsonName,
                    revision = level.revision,
                    seedHex = variant.seedHex,
                    moves = solved.moves.map { it.toSpec() },
                    afterStateHash = solved.hashes,
                    finalScore = solved.score,
                    movesUsed = solved.movesUsed,
                    maxCascade = solved.maxCascade,
                    twoStars = two,
                    threeStars = three,
                ),
            )
        }
        val target = levelPath(ordinal)
        writeText(target, levelJson.encodeToString(LevelFile.serializer(), level))
        for ((difficultyName, certificate) in certificates) {
            val difficulty = Difficulty.entries.first { it.jsonName == difficultyName }
            writeText(certificatePath(ordinal, difficulty), levelJson.encodeToString(CertificateFile.serializer(), certificate))
        }
        if (candidatePath(ordinal).exists()) Files.deleteIfExists(candidatePath(ordinal))
        return CertifyOutcome(true, "level %04d world %02d: certified".format(ordinal, world))
    }

    // ---------------------------------------------------------------------- sync

    /**
     * Aligns every published level's revision with its certificates, so a
     * certificate written by an older certify run stays verifiable without
     * re-solving anything (the revision is part of the state hash).
     */
    fun syncRevisions(args: Args): Int {
        var synced = 0
        var skipped = 0
        for (world in 1..12) {
            for (ordinal in (world * 50 - 49)..(world * 50)) {
                val path = levelPath(ordinal)
                if (!path.exists()) continue
                val level = readLevel(path)
                val revisions = ArrayList<Int>()
                for (difficulty in Difficulty.entries) {
                    val certPath = certificatePath(ordinal, difficulty)
                    if (!certPath.exists()) continue
                    val certificate = levelJson.decodeFromString(CertificateFile.serializer(), Files.readString(certPath))
                    revisions.add(certificate.revision)
                }
                if (revisions.isEmpty()) {
                    skipped++
                    continue
                }
                val target = revisions.first()
                if (revisions.any { it != target } || level.revision == target) {
                    if (level.revision == target) continue
                    skipped++
                    continue
                }
                writeText(path, levelJson.encodeToString(LevelFile.serializer(), level.copy(revision = target)))
                synced++
            }
        }
        CatalogBuilder.rebuild(Path.of("content"))
        println("sync-revisions: $synced synced, $skipped skipped")
        return 0
    }

    // ---------------------------------------------------------------------- misc

    fun stats(args: Args): Int {
        val catalogPath = Path.of(args.value("catalog", "content/catalog.json"))
        if (!catalogPath.exists()) {
            println("stats: no catalog")
            return 1
        }
        val catalog = levelJson.decodeFromString(CatalogFile.serializer(), Files.readString(catalogPath))
        println("levels: ${catalog.levels.size}, worlds: ${catalog.worlds.size}")
        return 0
    }
}
