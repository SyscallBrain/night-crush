package pt.tiago.nightcrush.leveltool

import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.model.BlockerSpec
import pt.tiago.nightcrush.core.model.CellSpec
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.FloorSpec
import pt.tiago.nightcrush.core.model.LanternRulesSpec
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.LimitSpec
import pt.tiago.nightcrush.core.model.ObjectiveSpec
import pt.tiago.nightcrush.core.model.OrderSpec
import pt.tiago.nightcrush.core.model.OverlaySpec
import pt.tiago.nightcrush.core.model.PieceSpec
import pt.tiago.nightcrush.core.model.PortalSpec
import pt.tiago.nightcrush.core.model.PosSpec
import pt.tiago.nightcrush.core.model.SpawnerSpec
import pt.tiago.nightcrush.core.model.StarsSpec
import pt.tiago.nightcrush.core.model.TutorialSpec
import pt.tiago.nightcrush.core.model.TutorialStepSpec
import pt.tiago.nightcrush.core.model.VariantSpec
import pt.tiago.nightcrush.core.random.SplitMix64
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.rules.MatchFinder
import kotlin.math.roundToInt

/**
 * Deterministic level generation.
 *
 * Each world first gets a plan: one goal per position, drawn from the world's
 * goal mix (plan section 5.2), and one structural family per position that can
 * host that goal. A family is a board shape plus a feature theme (silt, coral,
 * lanterns, ...). Within a world no family repeats in a row or more than four
 * times. The variants are then built from the slot.
 */
object Generation {

    private const val PER_WORLD = 50

    /**
     * [seed] drives the boards; [planSeed] drives the world plan (goals and
     * families), so a rejected level can be rebuilt without disturbing its world.
     */
    fun level(ordinal: Int, seed: Long, planSeed: Long = seed): LevelFile {
        require(ordinal in 1..600) { "ordinal out of range" }
        val world = (ordinal - 1) / PER_WORLD + 1
        val position = (ordinal - 1) % PER_WORLD + 1
        val fullMoon = position % 10 == 0
        val slot = worldPlan(world, planSeed)[position - 1]
        val intro = isIntro(world, position)
        val rush = SplitMix64(seed * 31 + ordinal * 7919)
        val variants = LinkedHashMap<String, VariantSpec>()
        for (difficulty in listOf("easy", "medium", "hard")) {
            variants[difficulty] = buildVariant(
                ordinal, world, position, difficulty, fullMoon, intro, slot,
                SplitMix64(rush.nextLong()),
            )
        }
        return LevelFile(
            engineVersion = ENGINE_VERSION,
            id = "level-%04d".format(ordinal),
            ordinal = ordinal,
            worldId = "world-%02d".format(world),
            titlePt = title(ordinal, portuguese = true),
            titleEn = title(ordinal, portuguese = false),
            familyId = slot.family.id,
            learningTags = buildList {
                if (intro) add("intro")
                if (fullMoon) add("full-moon")
                add(slot.goal.tag)
            },
            revision = 1,
            isFullMoon = fullMoon,
            tutorial = tutorialFor(world, position),
            variants = variants,
        )
    }

    /** Positions that teach something: short, generous and with a tutorial card. */
    private fun isIntro(world: Int, position: Int): Boolean =
        position <= 3 || (world == 1 && position in WORLD_ONE_LESSONS)

    private val WORLD_ONE_LESSONS = setOf(2, 4, 7, 10, 12, 15, 20, 25)

    // --------------------------------------------------------------------- goals

    private enum class Goal(val tag: String) {
        COLLECT("collect"),
        COLLECT_TWO("collect"),
        MAKE_SPECIAL("orders"),
        BREAK_LAYERS("orders"),
        SILT("silt"),
        LANTERNS("lanterns"),
        CORAL("coral"),
        SEAHORSES("seahorses"),
        GLOW("glow"),
    }

    /** Goal mix per world, 50 entries each. The world's new goal takes about 35%. */
    private fun goalMix(world: Int): Map<Goal, Int> = when (world) {
        1 -> mapOf(Goal.COLLECT to 22, Goal.COLLECT_TWO to 8, Goal.MAKE_SPECIAL to 20)
        2 -> mapOf(Goal.SILT to 20, Goal.COLLECT to 15, Goal.COLLECT_TWO to 7, Goal.MAKE_SPECIAL to 8)
        3 -> mapOf(Goal.LANTERNS to 17, Goal.SILT to 12, Goal.COLLECT to 8, Goal.COLLECT_TWO to 4, Goal.MAKE_SPECIAL to 9)
        4 -> mapOf(
            Goal.CORAL to 9, Goal.BREAK_LAYERS to 9, Goal.LANTERNS to 8, Goal.SILT to 8,
            Goal.COLLECT to 8, Goal.MAKE_SPECIAL to 8,
        )
        7 -> mapOf(
            Goal.SEAHORSES to 17, Goal.COLLECT to 7, Goal.SILT to 7, Goal.LANTERNS to 7,
            Goal.CORAL to 6, Goal.MAKE_SPECIAL to 6,
        )
        9 -> mapOf(
            Goal.GLOW to 17, Goal.COLLECT to 7, Goal.SILT to 7, Goal.LANTERNS to 7,
            Goal.CORAL to 6, Goal.MAKE_SPECIAL to 6,
        )
        else -> mapOf(
            Goal.COLLECT to 10, Goal.COLLECT_TWO to 4, Goal.SILT to 9, Goal.LANTERNS to 9,
            Goal.CORAL to 7, Goal.MAKE_SPECIAL to 6, Goal.BREAK_LAYERS to 5,
        )
    }

    /** The goal of the first level of a world, which introduces its novelty. */
    private fun introGoal(world: Int): Goal = when (world) {
        2 -> Goal.SILT
        3 -> Goal.LANTERNS
        4 -> Goal.CORAL
        7 -> Goal.SEAHORSES
        9 -> Goal.GLOW
        else -> Goal.COLLECT
    }

    /** Positions with a clock: four per world from world 5 (plan section 5.2). */
    private fun isTimeLevel(world: Int, position: Int): Boolean = world >= 5 && position in TIME_POSITIONS

    private val TIME_POSITIONS = setOf(5, 18, 31, 44)

    // ------------------------------------------------------------------ families

    private class Shape(val id: String, val mask: List<String>)

    private enum class Theme(val id: String, val world: Int) {
        PLAIN("", 1),
        BARNACLE("barnacle", 1),
        SILT("silt", 2),
        LANTERN("lantern", 3),
        CORAL("coral", 4),
        NET("net", 5),
        OIL("oil", 6),
        BUBBLES("seahorse", 7),
        PORTALS("portal", 7),
        CONVEYOR("conveyor", 8),
        PUFFER("puffer", 9),
        GLOW("glow", 9),
        OYSTER("oyster", 10),
        SINKHOLE("sinkhole", 11),
        OCTOPUS("octopus", 12),
    }

    private class Family(val shape: Shape, val theme: Theme) {
        val id: String = if (theme == Theme.PLAIN) shape.id else "${theme.id}-${shape.id}"
        val mask: List<String> get() = shape.mask
    }

    private class Slot(val goal: Goal, val family: Family)

    private fun mask(vararg rows: String) = rows.toList()

    private val shapes: List<Shape> = listOf(
        Shape("open-field", List(9) { "XXXXXXXXX" }),
        Shape(
            "heart",
            mask(
                "..XXXXX..", ".XXXXXXX.", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX",
                "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...",
            ),
        ),
        Shape(
            "diamond",
            mask(
                "...XXX...", "..XXXXX..", ".XXXXXXX.", "XXXXXXXXX", "XXXXXXXXX",
                "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...",
            ),
        ),
        Shape(
            "hourglass",
            mask(
                "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...",
                "..XXXXX..", ".XXXXXXX.", "XXXXXXXXX", "XXXXXXXXX",
            ),
        ),
        Shape(
            "ring",
            mask(
                "XXXXXXXXX", "XXXXXXXXX", "XXX...XXX", "XX.....XX", "XX.....XX",
                "XX.....XX", "XXX...XXX", "XXXXXXXXX", "XXXXXXXXX",
            ),
        ),
        Shape(
            "cross",
            mask(
                "..XXXXX..", "..XXXXX..", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX",
                "XXXXXXXXX", "XXXXXXXXX", "..XXXXX..", "..XXXXX..",
            ),
        ),
        Shape(
            "twin-towers",
            mask(
                "XXXX.XXXX", "XXXX.XXXX", "XXXX.XXXX", "XXXXXXXXX", "XXXXXXXXX",
                "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX",
            ),
        ),
        Shape(
            "arrow",
            mask(
                "...XXX...", "..XXXXX..", ".XXXXXXX.", "XXXXXXXXX", "XXXXXXXXX",
                "XXXXXXXXX", "..XXXXX..", "..XXXXX..", "..XXXXX..",
            ),
        ),
        Shape(
            "harbour",
            mask(
                "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX",
                "XXXXXXXXX", "XXX...XXX", "XXX...XXX", "XXX...XXX",
            ),
        ),
        Shape(
            "wide-bay",
            mask(
                ".XXXXXXX.", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX",
                "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.",
            ),
        ),
        Shape("tall-channel", List(9) { "XXXXXXX" }),
    )

    /** Column of the lantern exit: the middle one. */
    private fun exitColumn(mask: List<String>): Int = mask[0].length / 2

    /** Whether a shape can host a theme (open cells where the theme needs them). */
    private fun fits(shape: Shape, theme: Theme): Boolean {
        val m = shape.mask
        val h = m.size
        val w = m[0].length
        fun open(r: Int, c: Int) = r in 0 until h && c in 0 until w && m[r][c] == 'X'
        val cr = h / 2 - 1
        val cc = w / 2 - 1
        return when (theme) {
            Theme.LANTERN -> (0 until h).all { open(it, exitColumn(m)) }
            Theme.PORTALS -> open(1, 1) && open(1, w - 2) && open(h - 2, w - 2) && open(h - 2, 1)
            Theme.CONVEYOR -> (1 until w - 1).all { open(h - 2, it) }
            Theme.OYSTER, Theme.SINKHOLE -> open(cr, cc) && open(cr, cc + 1) && open(cr + 1, cc) && open(cr + 1, cc + 1)
            Theme.OCTOPUS -> open(cr, cc)
            else -> true
        }
    }

    private val families: List<Family> =
        Theme.entries.flatMap { theme -> shapes.filter { fits(it, theme) }.map { Family(it, theme) } }

    /** The themes a goal can be played on. */
    private fun themesFor(goal: Goal): Set<Theme> = when (goal) {
        Goal.SILT -> setOf(Theme.SILT, Theme.NET)
        Goal.LANTERNS -> setOf(Theme.LANTERN)
        Goal.CORAL, Goal.BREAK_LAYERS -> setOf(Theme.CORAL)
        Goal.SEAHORSES -> setOf(Theme.BUBBLES)
        Goal.GLOW -> setOf(Theme.GLOW)
        else -> Theme.entries.toSet() - setOf(Theme.LANTERN, Theme.SILT, Theme.CORAL, Theme.BUBBLES, Theme.GLOW)
    }

    /** The theme a world introduces, preferred for its generic goals. */
    private fun newTheme(world: Int): Theme? = Theme.entries.firstOrNull { it.world == world && it != Theme.PLAIN }
        ?.let { if (world == 7) Theme.PORTALS else it }

    private val plans = HashMap<Pair<Int, Long>, List<Slot>>()

    private fun worldPlan(world: Int, seed: Long): List<Slot> = synchronized(plans) {
        plans.getOrPut(world to seed) { buildPlan(world, seed) }
    }

    private fun buildPlan(world: Int, seed: Long): List<Slot> {
        val rng = SplitMix64(seed * 1_000_003L + world * 7919L)
        val goals = ArrayList<Goal>(PER_WORLD)
        for ((goal, count) in goalMix(world)) repeat(count) { goals.add(goal) }
        check(goals.size == PER_WORLD) { "goal mix of world $world has ${goals.size} entries" }
        // Shuffle, then pin the introductory goals and fix the timed positions.
        for (k in goals.size - 1 downTo 1) {
            val j = rng.nextInt(k + 1)
            val tmp = goals[k]; goals[k] = goals[j]; goals[j] = tmp
        }
        fun moveTo(position: Int, wanted: (Goal) -> Boolean) {
            val index = position - 1
            if (wanted(goals[index])) return
            val from = (position until goals.size).firstOrNull { wanted(goals[it]) }
                ?: (0 until index).firstOrNull { wanted(goals[it]) } ?: return
            val tmp = goals[index]; goals[index] = goals[from]; goals[from] = tmp
        }
        moveTo(1, { it == introGoal(world) })
        if (world == 1) {
            for (position in listOf(2, 4, 7, 10)) moveTo(position, { it == Goal.MAKE_SPECIAL })
        }
        for (position in TIME_POSITIONS) {
            if (isTimeLevel(world, position)) moveTo(position, { it != Goal.LANTERNS })
        }
        // No goal three times in a row.
        for (i in 2 until goals.size) {
            if (goals[i] == goals[i - 1] && goals[i] == goals[i - 2]) {
                val swap = (i + 1 until goals.size).firstOrNull { goals[it] != goals[i] && it - 1 !in TIME_POSITIONS }
                if (swap != null) {
                    val tmp = goals[i]; goals[i] = goals[swap]; goals[swap] = tmp
                }
            }
        }

        val used = HashMap<String, Int>()
        val slots = ArrayList<Slot>(PER_WORLD)
        val fresh = newTheme(world)
        for (index in goals.indices) {
            val position = index + 1
            val goal = goals[index]
            val allowed = themesFor(goal)
            var pool = families.filter { family ->
                family.theme in allowed &&
                    family.theme.world <= world &&
                    // Barnacles arrive at level 25 of world 1.
                    (family.theme != Theme.BARNACLE || world > 1 || position >= 25)
            }
            if (position == 1 && world > 1) {
                // The first level shows off the world's novelty.
                val showcase = pool.filter { it.theme.world == world }
                if (showcase.isNotEmpty()) pool = showcase
            } else if (fresh != null && goal in GENERIC_GOALS && rng.nextInt(100) < 45) {
                val themed = pool.filter { it.theme == fresh }
                if (themed.isNotEmpty()) pool = themed
            }
            val previous = slots.lastOrNull()?.family?.id
            val candidates = pool.filter { it.id != previous && (used[it.id] ?: 0) < 4 }
                .ifEmpty { pool.filter { it.id != previous } }
                .ifEmpty { pool }
            // Least used first, ties broken by the plan's random stream.
            val least = candidates.minOf { used[it.id] ?: 0 }
            val tied = candidates.filter { (used[it.id] ?: 0) == least }
            val family = tied[rng.nextInt(tied.size)]
            used[family.id] = (used[family.id] ?: 0) + 1
            slots.add(Slot(goal, family))
        }
        return slots
    }

    private val GENERIC_GOALS = setOf(Goal.COLLECT, Goal.COLLECT_TWO, Goal.MAKE_SPECIAL)

    // ------------------------------------------------------------------- variant

    private fun buildVariant(
        ordinal: Int,
        world: Int,
        position: Int,
        difficulty: String,
        fullMoon: Boolean,
        intro: Boolean,
        slot: Slot,
        rng: SplitMix64,
    ): VariantSpec {
        val goal = slot.goal
        val family = slot.family
        val theme = family.theme
        val mask = family.mask
        val colorCount = when (difficulty) {
            "easy" -> if (world <= 4 || intro) 4 else 5
            "medium" -> if (fullMoon) 6 else 5
            else -> 6
        }
        val colors = COLORS.take(colorCount)
        val width = mask[0].length
        val height = mask.size
        val openCells = ArrayList<PosSpec>()
        for (r in 0 until height) {
            for (c in 0 until width) if (mask[r][c] == 'X') openCells.add(PosSpec(r, c))
        }
        val exitCol = exitColumn(mask)

        val cells = ArrayList<CellSpec>()
        val occupied = HashSet<Int>()

        /**
         * Free open cells in random order. Blockers stay off the top row (they
         * would seal a spawner) and off the lantern column.
         */
        fun takeOpen(count: Int, forBlocker: Boolean): List<PosSpec> {
            if (count <= 0) return emptyList()
            val list = openCells.toMutableList()
            for (k in list.size - 1 downTo 1) {
                val j = rng.nextInt(k + 1)
                val tmp = list[k]; list[k] = list[j]; list[j] = tmp
            }
            val out = ArrayList<PosSpec>()
            for (pos in list) {
                if (out.size >= count) break
                if (forBlocker && (pos.r == 0 || (theme == Theme.LANTERN && pos.c == exitCol))) continue
                if (occupied.add(pos.r * width + pos.c)) out.add(pos)
            }
            return out
        }

        val hard = difficulty == "hard"
        val easy = difficulty == "easy"
        when (theme) {
            Theme.SILT, Theme.NET, Theme.LANTERN -> {
                val share = when (theme) {
                    Theme.SILT -> 0.25
                    Theme.NET -> 0.18
                    else -> 0.10
                }
                for (pos in takeOpen((openCells.size * share).roundToInt(), forBlocker = false)) {
                    val layers = if (easy) 1 else if (hard) 2 else rng.nextInt(2) + 1
                    cells.add(CellSpec(pos.r, pos.c, floor = FloorSpec(silt = layers)))
                }
                if (theme == Theme.NET) {
                    for (pos in takeOpen(if (hard) 4 else 3, forBlocker = true)) {
                        cells.add(CellSpec(pos.r, pos.c, overlay = OverlaySpec("net")))
                    }
                }
            }
            Theme.GLOW -> for (pos in takeOpen((openCells.size * 0.08).roundToInt().coerceAtLeast(3), forBlocker = false)) {
                cells.add(CellSpec(pos.r, pos.c, floor = FloorSpec(glow = true)))
            }
            Theme.BARNACLE -> for (pos in takeOpen(if (easy) 3 else 4, forBlocker = true)) {
                cells.add(CellSpec(pos.r, pos.c, overlay = OverlaySpec("barnacle", layers = if (hard) 2 else 1)))
            }
            Theme.CORAL -> for (pos in takeOpen(if (easy) 5 else if (hard) 7 else 6, forBlocker = true)) {
                val layers = if (easy) 1 else 2
                cells.add(CellSpec(pos.r, pos.c, blocker = BlockerSpec("coral", layers = layers)))
            }
            Theme.OIL -> for (pos in takeOpen(if (hard) 4 else 3, forBlocker = true)) {
                cells.add(CellSpec(pos.r, pos.c, blocker = BlockerSpec("oil")))
            }
            Theme.BUBBLES -> for (pos in takeOpen(if (easy) 3 else 4, forBlocker = true)) {
                cells.add(CellSpec(pos.r, pos.c, blocker = BlockerSpec("seahorseBubble")))
            }
            Theme.PUFFER -> for (pos in takeOpen(if (easy) 2 else 3, forBlocker = true)) {
                cells.add(
                    CellSpec(
                        pos.r, pos.c,
                        piece = PieceSpec("puffer", colors[(pos.r + pos.c) % colors.size], counter = if (hard) 10 else 14),
                    ),
                )
            }
            Theme.OYSTER -> {
                val r = height / 2 - 1
                val c = width / 2 - 1
                cells.add(
                    CellSpec(
                        r, c,
                        blocker = BlockerSpec(
                            "giantOyster",
                            color = colors[(ordinal + difficulty.length) % colors.size],
                            counter = if (hard) 3 else 2,
                        ),
                    ),
                )
                for (dr in 0..1) for (dc in 0..1) occupied.add((r + dr) * width + c + dc)
            }
            Theme.SINKHOLE -> {
                val r = height / 2 - 1
                val c = width / 2 - 1
                cells.add(CellSpec(r, c, blocker = BlockerSpec("sinkhole", layers = if (easy) 2 else 3)))
                for (dr in 0..1) for (dc in 0..1) occupied.add((r + dr) * width + c + dc)
            }
            Theme.OCTOPUS -> {
                val r = height / 2 - 1
                val c = width / 2 - 1
                if (easy) {
                    // Easy variants never face the octopus (plan section 5.4): coral instead.
                    for (pos in takeOpen(4, forBlocker = true)) {
                        cells.add(CellSpec(pos.r, pos.c, blocker = BlockerSpec("coral", layers = 1)))
                    }
                } else {
                    cells.add(CellSpec(r, c, blocker = BlockerSpec("octopus", hp = if (hard) 6 else 5)))
                    occupied.add(r * width + c)
                }
            }
            Theme.PLAIN, Theme.PORTALS, Theme.CONVEYOR -> Unit
        }

        val portals = if (theme == Theme.PORTALS) {
            listOf(
                PortalSpec(PosSpec(1, 1), PosSpec(1, width - 2)),
                PortalSpec(PosSpec(height - 2, width - 2), PosSpec(height - 2, 1)),
            )
        } else {
            emptyList()
        }
        val conveyor = if (theme == Theme.CONVEYOR) (1 until width - 1).map { PosSpec(height - 2, it) } else emptyList()

        val spawners = ArrayList<SpawnerSpec>()
        for (c in 0 until width) if (mask[0][c] == 'X') spawners.add(SpawnerSpec(c, lanterns = goal == Goal.LANTERNS && c == exitCol))
        var lanternRules: LanternRulesSpec? = null
        val exits = ArrayList<PosSpec>()
        if (goal == Goal.LANTERNS) {
            val total = if (easy) 2 else if (hard) 4 else 3
            lanternRules = LanternRulesSpec(total, maxOnBoard = 2, everyMoves = 3)
            exits.add(PosSpec(height - 1, exitCol))
        }

        val timeLevel = isTimeLevel(world, position)
        val objectives = objectivesFor(goal, ordinal, world, position, difficulty, colors, cells, lanternRules)

        // Designed budget; the calibration then nudges it into the target band.
        val designed = designedMoves(goal, world, position, difficulty, intro)
        val limit = if (timeLevel) {
            val seconds = when (difficulty) {
                "easy" -> 110
                "medium" -> 95
                else -> 80
            } - (world - 5).coerceAtLeast(0) * 2
            LimitSpec("time", seconds.coerceIn(60, 120))
        } else {
            LimitSpec("moves", designed)
        }

        var variant = VariantSpec(
            seedHex = "%016x".format(rng.nextLong()),
            width = width,
            height = height,
            mask = mask,
            colors = colors,
            cells = fillPieces(mask, width, height, colors, cells, rng),
            spawners = spawners,
            exits = exits,
            portals = portals,
            conveyor = conveyor,
            limit = limit,
            objectives = objectives,
            lanternRules = lanternRules,
            stars = StarsSpec(1000, 2000),
        )
        repeat(40) {
            if (isPlayable(variant, difficulty, ordinal)) return variant
            variant = variant.copy(
                seedHex = "%016x".format(rng.nextLong()),
                cells = fillPieces(mask, width, height, colors, cells, rng),
            )
        }
        return variant
    }

    /**
     * Moves for a variant before calibration: the goal sets the base, the
     * sawtooth of each block of ten bends it, and the difficulty scales it.
     */
    private fun designedMoves(goal: Goal, world: Int, position: Int, difficulty: String, intro: Boolean): Int {
        val base = when (goal) {
            Goal.COLLECT -> 18
            Goal.COLLECT_TWO -> 20
            Goal.MAKE_SPECIAL -> 18
            Goal.BREAK_LAYERS -> 20
            Goal.SILT -> 22
            Goal.LANTERNS -> 24
            Goal.CORAL -> 22
            Goal.SEAHORSES -> 22
            Goal.GLOW -> 24
        } + world / 3
        val inBlock = (position - 1) % 10 + 1
        val saw = when (inBlock) {
            in 1..3 -> 3
            in 4..7 -> 0
            in 8..9 -> -2
            else -> -3
        }
        val medium = base + saw + if (intro) 4 else 0
        val scaled = when (difficulty) {
            "easy" -> medium * 1.3
            "hard" -> medium * 0.8
            else -> medium.toDouble()
        }
        return scaled.roundToInt().coerceAtLeast(10)
    }

    private fun objectivesFor(
        goal: Goal,
        ordinal: Int,
        world: Int,
        position: Int,
        difficulty: String,
        colors: List<String>,
        cells: List<CellSpec>,
        lanternRules: LanternRulesSpec?,
    ): List<ObjectiveSpec> {
        val step = when (difficulty) {
            "easy" -> 0
            "medium" -> 1
            else -> 2
        }
        val collect = 14 + step * 6 + world
        val color = colors[ordinal % colors.size]
        val otherColor = colors[(ordinal + 2) % colors.size]
        return when (goal) {
            Goal.COLLECT -> listOf(ObjectiveSpec("collect", colors = mapOf(color to collect)))
            Goal.COLLECT_TWO -> listOf(
                ObjectiveSpec("collect", colors = mapOf(color to collect * 2 / 3, otherColor to collect * 2 / 3)),
            )
            Goal.MAKE_SPECIAL -> {
                val (kind, count) = specialOrder(world, position, ordinal, step)
                listOf(ObjectiveSpec("orders", orders = listOf(OrderSpec("makeSpecial", value = kind, count = count))))
            }
            Goal.BREAK_LAYERS -> {
                val layers = cells.sumOf { if (it.blocker?.kind == "coral") it.blocker?.layers ?: 0 else 0 }
                listOf(
                    ObjectiveSpec(
                        "orders",
                        orders = listOf(
                            OrderSpec("breakLayers", value = "CORAL", count = (layers * 2 / 3).coerceAtLeast(3)),
                            OrderSpec("collect", colorName = color, count = collect / 2),
                        ),
                    ),
                )
            }
            Goal.SILT -> listOf(ObjectiveSpec("silt", count = cells.sumOf { it.floor?.silt ?: 0 }))
            Goal.LANTERNS -> listOf(ObjectiveSpec("deliverLanterns", count = lanternRules?.total ?: 0))
            Goal.CORAL -> {
                val layers = cells.sumOf { if (it.blocker?.kind == "coral") it.blocker?.layers ?: 0 else 0 }
                listOf(ObjectiveSpec("clearBlockers", blockers = mapOf("CORAL" to layers)))
            }
            Goal.SEAHORSES -> listOf(
                ObjectiveSpec("freeSeahorses", count = cells.count { it.blocker?.kind == "seahorseBubble" }),
            )
            Goal.GLOW -> listOf(ObjectiveSpec("spreadGlow"))
        }
    }

    /** Which special an order asks for: only the ones world 1 has already taught. */
    private fun specialOrder(world: Int, position: Int, ordinal: Int, step: Int): Pair<String, Int> {
        val known = buildList {
            add("RAY")
            if (world > 1 || position >= 4) add("JELLY")
            if (world > 1 || position >= 7) add("LANTERN_FISH")
            if (world > 1 || position >= 10) add("ABYSS_PEARL")
        }
        // A lesson level asks for exactly the special it teaches.
        val kind = if (world == 1) {
            when (position) {
                2 -> "RAY"
                4 -> "JELLY"
                7 -> "LANTERN_FISH"
                10 -> "ABYSS_PEARL"
                else -> known[ordinal % known.size]
            }
        } else {
            known[ordinal % known.size]
        }
        val count = when (kind) {
            "RAY" -> 3 + step
            "JELLY" -> 2 + step
            "LANTERN_FISH" -> 2 + step / 2
            else -> 1 + step / 2
        }
        return kind to count
    }

    /**
     * Fills every open cell that is not already a blocker or a piece, avoiding
     * immediate three-runs and 2x2 squares while placing.
     */
    private fun fillPieces(
        mask: List<String>,
        width: Int,
        height: Int,
        colors: List<String>,
        decorations: List<CellSpec>,
        rng: SplitMix64,
    ): List<CellSpec> {
        val blocked = HashSet<Int>()
        val byIndex = HashMap<Int, CellSpec>()
        for (spec in decorations) {
            val index = spec.r * width + spec.c
            byIndex[index] = spec
            if (spec.blocker != null || spec.piece != null) blocked.add(index)
        }
        val pieces = arrayOfNulls<String>(width * height)
        for ((index, spec) in byIndex) {
            spec.piece?.color?.let { pieces[index] = it.uppercase() }
        }
        fun colorAt(r: Int, c: Int): String? =
            if (r in 0 until height && c in 0 until width) pieces[r * width + c] else null

        for (r in 0 until height) {
            for (c in 0 until width) {
                if (mask[r][c] != 'X') continue
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
                val pick = if (available.isEmpty()) colors[rng.nextInt(colors.size)]
                else available[rng.nextInt(available.size)]
                pieces[index] = pick
            }
        }
        val filled = ArrayList<CellSpec>(decorations.size + width * height)
        for (r in 0 until height) {
            for (c in 0 until width) {
                if (mask[r][c] != 'X') continue
                val index = r * width + c
                val existing = byIndex[index]
                // Blockers keep their cell exactly as decorated: no piece on top.
                if (existing != null && existing.blocker != null) {
                    filled.add(existing)
                    continue
                }
                val color = pieces[index] ?: continue
                filled.add(
                    if (existing == null) {
                        CellSpec(r, c, piece = PieceSpec("normal", color))
                    } else if (existing.piece == null) {
                        existing.copy(piece = PieceSpec("normal", color))
                    } else {
                        existing
                    },
                )
            }
        }
        return filled
    }

    private fun isPlayable(variant: VariantSpec, difficulty: String, ordinal: Int): Boolean {
        val wrapper = LevelFile(
            engineVersion = ENGINE_VERSION,
            id = "level-%04d".format(ordinal),
            ordinal = ordinal,
            worldId = "world-%02d".format((ordinal - 1) / PER_WORLD + 1),
            titlePt = "candidate",
            variants = mapOf(difficulty to variant),
        )
        return runCatching {
            val state = LevelBuilder.start(wrapper, Difficulty.valueOf(difficulty.uppercase()))
            MatchFinder.find(state).isEmpty() &&
                GameEngine.hasValidMove(state) &&
                GameEngine.refillsEverywhere(state)
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ tutorials

    private fun tutorialFor(world: Int, position: Int): TutorialSpec? {
        val key = if (world == 1) {
            when (position) {
                1 -> "tut_swap"
                2 -> "tut_ray"
                4 -> "tut_jelly"
                7 -> "tut_fish"
                10 -> "tut_pearl"
                12 -> "tut_combo"
                25 -> "tut_barnacle"
                else -> return null
            }
        } else if (position == 1) {
            when (world) {
                2 -> "tut_silt"
                3 -> "tut_lanterns"
                4 -> "tut_coral"
                5 -> "tut_net"
                6 -> "tut_oil"
                7 -> "tut_seahorse"
                8 -> "tut_conveyor"
                9 -> "tut_glow"
                10 -> "tut_oyster"
                11 -> "tut_sinkhole"
                else -> "tut_octopus"
            }
        } else {
            return null
        }
        return TutorialSpec(listOf(TutorialStepSpec(textKey = key)))
    }

    // -------------------------------------------------------------------- titles

    /** Twenty themes; each world uses ten of them, shifted by world. */
    private val themes = listOf(
        "Lanternas" to "Lanterns", "Segredos" to "Secrets", "Marés" to "Tides", "Sombras" to "Shadows",
        "Ecos" to "Echoes", "Jardins" to "Gardens", "Ventos" to "Winds", "Reflexos" to "Reflections",
        "Cantos" to "Songs", "Vestígios" to "Relics", "Caminhos" to "Paths", "Faróis" to "Beacons",
        "Areias" to "Sands", "Correntes" to "Currents", "Cristais" to "Crystals", "Naufrágios" to "Wrecks",
        "Luzes" to "Glimmers", "Conchas" to "Shells", "Brumas" to "Mists", "Estrelas" to "Stars",
    )

    /** Five real places on the coast of each world (Portuguese, English). */
    private val places = listOf(
        listOf("da Ria" to "of the Ria", "de Faro" to "of Faro", "da Culatra" to "of Culatra", "de Tavira" to "of Tavira", "da Armona" to "of Armona"),
        listOf("de Sagres" to "of Sagres", "do Cabo de São Vicente" to "of Cape St. Vincent", "do Beliche" to "of Beliche", "da Baleeira" to "of Baleeira", "do Martinhal" to "of Martinhal"),
        listOf("da Arrábida" to "of Arrábida", "de Sesimbra" to "of Sesimbra", "do Portinho" to "of Portinho", "de Galapos" to "of Galapos", "do Espichel" to "of Espichel"),
        listOf("das Berlengas" to "of the Berlengas", "de Peniche" to "of Peniche", "das Estelas" to "of the Estelas", "dos Farilhões" to "of the Farilhões", "do Forte" to "of the Fort"),
        listOf("da Nazaré" to "of Nazaré", "do Sítio" to "of the Sítio", "da Praia do Norte" to "of Praia do Norte", "do Canhão" to "of the Canyon", "de São Martinho" to "of São Martinho"),
        listOf("da Costa Vicentina" to "of the Vicentine Coast", "de Odeceixe" to "of Odeceixe", "da Arrifana" to "of Arrifana", "do Amado" to "of Amado", "de Aljezur" to "of Aljezur"),
        listOf("das Fajãs" to "of the Fajãs", "de São Jorge" to "of São Jorge", "da Caldeira" to "of the Caldera", "das Flores" to "of Flores", "do Pico" to "of Pico"),
        listOf("do Porto Moniz" to "of Porto Moniz", "das Piscinas" to "of the Rock Pools", "do Seixal" to "of Seixal", "da Ribeira da Janela" to "of Ribeira da Janela", "do Ilhéu Mole" to "of Ilhéu Mole"),
        listOf("de Benagil" to "of Benagil", "da Gruta" to "of the Cave", "da Marinha" to "of Marinha", "de Carvoeiro" to "of Carvoeiro", "dos Algares" to "of the Sea Caves"),
        listOf("das Selvagens" to "of the Savage Islands", "da Selvagem Grande" to "of Selvagem Grande", "do Ilhéu de Fora" to "of the Outer Islet", "da Selvagem Pequena" to "of Selvagem Pequena", "dos Cagarros" to "of the Shearwaters"),
        listOf("da Fossa" to "of the Trench", "do Abismo" to "of the Abyss", "das Fontes Termais" to "of the Vents", "da Escuridão" to "of the Dark", "do Fundo" to "of the Seabed"),
        listOf("do Atlântico" to "of the Atlantic", "do Alto Mar" to "of the High Sea", "da Dorsal" to "of the Ridge", "do Mar dos Sargaços" to "of the Sargasso Sea", "do Golfo" to "of the Gulf"),
    )

    /**
     * Fifty different titles per world: ten themes times five places, so all
     * 600 are unique. Levels 1–10 of a world share the first place, and so on.
     */
    fun title(ordinal: Int, portuguese: Boolean): String {
        val world = ((ordinal - 1) / PER_WORLD).coerceIn(0, 11)
        val index = (ordinal - 1) % PER_WORLD
        val theme = themes[(world * 3 + index % 10) % themes.size]
        val place = places[world][index / 10]
        return if (portuguese) "${theme.first} ${place.first}" else "${theme.second} ${place.second}"
    }

    private val COLORS = listOf("BLUE", "YELLOW", "PURPLE", "TEAL", "GREEN", "CORAL")
}
