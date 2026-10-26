package pt.tiago.nightcrush.leveltool

import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.model.BlockerKind
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.ObjectiveSpec
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.VariantSpec
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.rules.MatchFinder

/**
 * Content rules for a single level file.
 *
 * [validate] always checks what makes a level playable at all (identity, build,
 * initial board, mask connectivity, objective coherence). The content rules
 * (introduction order, colour counts, stars, tutorial steps) only run in strict
 * mode, which is what certification uses before promoting a candidate.
 */
object LevelValidator {

    private const val LEVELS_PER_WORLD = 50
    private val DIFFICULTY_NAMES = listOf("easy", "medium", "hard")

    /** Human-readable problems; empty when the level is fine. strict adds content-rule checks. */
    fun validate(level: LevelFile, strict: Boolean): List<String> {
        val issues = ArrayList<String>()
        checkIdentity(level, issues)
        checkVariantsPresent(level, strict, issues)
        for (name in DIFFICULTY_NAMES) {
            val variant = level.variants[name] ?: continue
            validateVariant(level, name, variant, strict, issues)
        }
        if (strict) checkTutorial(level, issues)
        return issues
    }

    // ------------------------------------------------------------------ identity

    private fun checkIdentity(level: LevelFile, issues: MutableList<String>) {
        if (level.schemaVersion != LevelFile.SCHEMA_VERSION) {
            issues += "schemaVersion ${level.schemaVersion} != ${LevelFile.SCHEMA_VERSION}"
        }
        if (level.engineVersion != ENGINE_VERSION) {
            issues += "engineVersion ${level.engineVersion} != $ENGINE_VERSION"
        }
        if (level.ordinal !in 1..600) {
            issues += "ordinal ${level.ordinal} outside 1..600"
        }
        val expectedId = "level-%04d".format(level.ordinal)
        if (level.id != expectedId) issues += "id ${level.id} != $expectedId"
        val expectedWorld = "world-%02d".format(worldOf(level.ordinal))
        if (level.worldId != expectedWorld) issues += "worldId ${level.worldId} != $expectedWorld"
    }

    private fun checkVariantsPresent(level: LevelFile, strict: Boolean, issues: MutableList<String>) {
        if (strict) {
            for (name in DIFFICULTY_NAMES) {
                if (!level.variants.containsKey(name)) issues += "missing $name variant"
            }
            return
        }
        if (level.variants.isEmpty()) issues += "no variants"
    }

    // ------------------------------------------------------------------- variant

    private fun validateVariant(
        level: LevelFile,
        name: String,
        variant: VariantSpec,
        strict: Boolean,
        issues: MutableList<String>,
    ) {
        val state = try {
            LevelBuilder.start(level, Difficulty.valueOf(name.uppercase()))
        } catch (error: Exception) {
            issues += "$name: board cannot be built: ${error.message}"
            return
        }
        checkBoard(state, name, issues)
        checkObjectives(state, variant, name, strict, issues)
        checkSilt(state, variant, name, strict, issues)
        checkLanterns(state, variant, name, strict, issues)
        checkSeahorses(state, variant, name, strict, issues)
        checkTimeLimit(state, variant, name, strict, issues)
        if (strict) {
            checkIntroductions(state, level, name, issues)
            checkColours(level, variant, name, issues)
            checkStars(variant, name, issues)
        }
    }

    private fun checkBoard(state: GameState, name: String, issues: MutableList<String>) {
        val groups = MatchFinder.find(state)
        if (groups.isNotEmpty()) {
            issues += "$name: board starts with ${groups.size} match group(s)"
        }
        if (!GameEngine.hasValidMove(state)) issues += "$name: no valid swap available"
        val reached = reachableFromSpawners(state)
        val unreachable = (0 until state.size).count { state.playable[it] && !reached[it] }
        if (unreachable > 0) {
            issues += "$name: $unreachable playable cell(s) are not reachable from a spawner"
        }
        for (i in 0 until state.size) {
            if (!state.isExit(i)) continue
            if (!state.playable[i]) {
                issues += "$name: exit at ${cellText(state, i)} is not a playable cell"
                continue
            }
            if (!reached[i]) issues += "$name: exit at ${cellText(state, i)} is not reachable"
        }
    }

    /** 4-neighbour flood fill seeded from the topmost playable cell of every spawning column. */
    private fun reachableFromSpawners(state: GameState): BooleanArray {
        val reached = BooleanArray(state.size)
        val queue = ArrayDeque<Int>()
        for (c in 0 until state.width) {
            if (!state.spawner[c]) continue
            for (r in 0 until state.height) {
                val cell = state.idx(r, c)
                if (!state.playable[cell]) continue
                reached[cell] = true
                queue.addLast(cell)
                break
            }
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val r = state.rowOf(cell)
            val c = state.colOf(cell)
            for (neighbor in neighbors(state, r, c)) {
                if (!state.playable[neighbor] || reached[neighbor]) continue
                reached[neighbor] = true
                queue.addLast(neighbor)
            }
        }
        return reached
    }

    private fun neighbors(state: GameState, r: Int, c: Int): List<Int> {
        val out = ArrayList<Int>(4)
        if (r > 0) out += state.idx(r - 1, c)
        if (r < state.height - 1) out += state.idx(r + 1, c)
        if (c > 0) out += state.idx(r, c - 1)
        if (c < state.width - 1) out += state.idx(r, c + 1)
        return out
    }

    private fun cellText(state: GameState, cell: Int): String = "(${state.rowOf(cell)},${state.colOf(cell)})"

    // ---------------------------------------------------------------- objectives

    private fun checkObjectives(
        state: GameState,
        variant: VariantSpec,
        name: String,
        strict: Boolean,
        issues: MutableList<String>,
    ) {
        val objectives = variant.objectives
        if (objectives.isEmpty()) {
            issues += "$name: no objectives"
            if (strict) return
        }
        if (strict && objectives.size !in 1..3) {
            issues += "$name: ${objectives.size} objectives, expected 1..3"
        }
        val available = variant.colors.map { it.uppercase() }.toSet()
        for (objective in objectives) {
            if (objective.type != "collect") continue
            for ((color, target) in objective.colors) {
                if (target > 0 && color.uppercase() !in available) {
                    issues += "$name: collect objective asks for $color which is not a board colour"
                }
            }
        }
        if (!strict) return
        // On easy every colour the level asks for has to be on the board, orders included.
        for (objective in objectives) {
            if (objective.type != "orders") continue
            for (order in objective.orders) {
                val color = order.colorName ?: continue
                if (color.uppercase() !in available) {
                    issues += "$name: order asks for $color which is not a board colour"
                }
            }
        }
        checkClearBlockers(state, objectives, name, issues)
    }

    private fun checkSilt(
        state: GameState,
        variant: VariantSpec,
        name: String,
        strict: Boolean,
        issues: MutableList<String>,
    ) {
        val target = variant.objectives.filter { it.type == "silt" }.sumOf { it.count }
        if (target <= 0) return
        val layers = (0 until state.size).sumOf { state.silt[it] }
        if (target > layers) {
            issues += "$name: silt objective asks for $target layers, the board only has $layers"
        }
    }

    private fun checkClearBlockers(
        state: GameState,
        objectives: List<ObjectiveSpec>,
        name: String,
        issues: MutableList<String>,
    ) {
        for (objective in objectives) {
            if (objective.type != "clearBlockers") continue
            for ((kindName, target) in objective.blockers) {
                if (target <= 0) continue
                val kind = BlockerKind.entries.firstOrNull { it.name.equals(kindName, ignoreCase = true) }
                if (kind == null) {
                    issues += "$name: clearBlockers names unknown blocker $kindName"
                    continue
                }
                val present = blockerLayers(state, kind)
                if (target > present) {
                    issues += "$name: clearBlockers asks for $target $kindName layers, " +
                        "the board only has $present"
                }
            }
        }
    }

    /** Coral counts layers; every other blocker counts the cells it occupies. */
    private fun blockerLayers(state: GameState, kind: BlockerKind): Int {
        var total = 0
        for (i in 0 until state.size) {
            when (kind) {
                BlockerKind.CORAL -> total += Blockers.coralLayers(state.blockers[i])
                else -> if (Blockers.kindOf(state.blockers[i]) == kind) total++
            }
        }
        return total
    }

    // ------------------------------------------------------------------ lanterns

    private fun checkLanterns(
        state: GameState,
        variant: VariantSpec,
        name: String,
        strict: Boolean,
        issues: MutableList<String>,
    ) {
        val target = variant.objectives.filter { it.type == "deliverLanterns" }.sumOf { it.count }
        val rules = variant.lanternRules
        if (target <= 0) {
            if (rules != null) {
                issues += "$name: lanternRules present without a deliverLanterns objective"
            }
            return
        }
        if (rules == null) {
            issues += "$name: deliverLanterns $target without lanternRules"
            return
        }
        if (rules.total != target) {
            issues += "$name: lanternRules.total ${rules.total} != deliverLanterns $target"
        }
        if (state.lanternSpawner.none { it }) issues += "$name: no spawner marked with lanterns"
        if ((0 until state.size).none { state.isExit(it) }) issues += "$name: no exit to deliver lanterns to"
        if (strict && rules.total <= 0) issues += "$name: lanternRules.total must be positive"
    }

    private fun checkSeahorses(
        state: GameState,
        variant: VariantSpec,
        name: String,
        strict: Boolean,
        issues: MutableList<String>,
    ) {
        if (!strict) return
        val target = variant.objectives.filter { it.type == "freeSeahorses" }.sumOf { it.count }
        if (target <= 0) return
        val bubbles = (0 until state.size).count { state.blockers[it] == Blockers.SEAHORSE_BUBBLE }
        if (bubbles != target) {
            issues += "$name: freeSeahorses target $target but the board has $bubbles seahorse bubble(s)"
        }
    }

    private fun checkTimeLimit(
        state: GameState,
        variant: VariantSpec,
        name: String,
        strict: Boolean,
        issues: MutableList<String>,
    ) {
        if (variant.limit.type.lowercase() != "time") return
        if (strict && variant.objectives.any { it.type == "deliverLanterns" }) {
            issues += "$name: time level cannot ask for lanterns"
        }
        if (strict && variant.limit.value !in 60..120) {
            issues += "$name: time limit ${variant.limit.value}s outside 60..120"
        }
    }

    // --------------------------------------------------------------- introduction

    private val blockerIntroWorld = mapOf(
        BlockerKind.CORAL to 4,
        BlockerKind.OIL to 6,
        BlockerKind.SEAHORSE_BUBBLE to 7,
        BlockerKind.GIANT_OYSTER to 10,
        BlockerKind.SINKHOLE to 11,
        BlockerKind.OCTOPUS to 12,
    )

    private fun checkIntroductions(
        state: GameState,
        level: LevelFile,
        name: String,
        issues: MutableList<String>,
    ) {
        val world = worldOf(level.ordinal)
        for (kind in BlockerKind.entries) {
            if (blockerLayers(state, kind) <= 0) continue
            val intro = blockerIntroWorld.getValue(kind)
            if (world < intro) issues += "$name: $kind is only introduced in world $intro (level is world $world)"
        }
        if ((0 until state.size).any { Overlays.isNet(state.overlays[it]) } && world < 5) {
            issues += "$name: nets are only introduced in world 5 (level is world $world)"
        }
        if ((0 until state.size).any { state.conveyorNext[it] >= 0 || state.conveyorNext[it] == -2 } && world < 8) {
            issues += "$name: conveyor belts are only introduced in world 8 (level is world $world)"
        }
        if ((0 until state.size).any { state.isPortalIn(it) || state.isPortalOut(it) } && world < 7) {
            issues += "$name: portals are only introduced in world 7 (level is world $world)"
        }
        if ((0 until state.size).any { Pieces.isPuffer(state.pieces[it]) } && world < 9) {
            issues += "$name: pufferfish are only introduced in world 9 (level is world $world)"
        }
        if ((0 until state.size).any { state.isGlow(it) } && world < 9) {
            issues += "$name: glow is only introduced in world 9 (level is world $world)"
        }
        if ((0 until state.size).any { state.silt[it] > 0 } && world < 2) {
            issues += "$name: silt is only introduced in world 2 (level is world $world)"
        }
    }

    // -------------------------------------------------------------------- colours

    private fun checkColours(
        level: LevelFile,
        variant: VariantSpec,
        name: String,
        issues: MutableList<String>,
    ) {
        val count = variant.colors.size
        when (name) {
            "easy" -> {
                if (count !in 4..5) issues += "$name: $count colours, expected 4..5"
            }
            "medium" -> {
                if (count != 5 && count != 6) issues += "$name: $count colours, expected 5..6"
                if (count == 6 && !level.isFullMoon) {
                    issues += "$name: 6 colours only on full moon levels"
                }
            }
            "hard" -> {
                if (count != 6) issues += "$name: $count colours, expected exactly 6"
            }
        }
    }

    private fun checkStars(variant: VariantSpec, name: String, issues: MutableList<String>) {
        val stars = variant.stars
        if (stars.two <= 0) issues += "$name: two stars must be positive"
        if (stars.three <= stars.two) issues += "$name: three stars ${stars.three} must beat two ${stars.two}"
    }

    // ------------------------------------------------------------------- tutorial

    private fun checkTutorial(level: LevelFile, issues: MutableList<String>) {
        val tutorial = level.tutorial ?: return
        val bounds = level.variants.values.firstOrNull() ?: return
        val width = bounds.width
        val height = bounds.height
        for ((index, step) in tutorial.steps.withIndex()) {
            val forced = step.forcedSwap ?: continue
            if (forced.size % 2 != 0) {
                issues += "tutorial step $index: forcedSwap has an odd number of coordinates"
                continue
            }
            for (pair in forced.chunked(2)) {
                val a = pair[0]
                val b = pair[1]
                if (a.size != 2 || b.size != 2) {
                    issues += "tutorial step $index: forcedSwap coordinate is not a [row, column] pair"
                    continue
                }
                if (!inBounds(a, width, height) || !inBounds(b, width, height)) {
                    issues += "tutorial step $index: forcedSwap $a-$b leaves the board"
                    continue
                }
                if (kotlin.math.abs(a[0] - b[0]) + kotlin.math.abs(a[1] - b[1]) != 1) {
                    issues += "tutorial step $index: forcedSwap $a-$b is not an orthogonal swap"
                }
            }
        }
    }

    private fun inBounds(cell: List<Int>, width: Int, height: Int): Boolean =
        cell[0] in 0 until height && cell[1] in 0 until width

    private fun worldOf(ordinal: Int): Int = (ordinal - 1) / LEVELS_PER_WORLD + 1
}
