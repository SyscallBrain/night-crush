package pt.tiago.nightcrush.core.model

import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.random.SplitMix64

/** Boosters bought before a level starts, placed by the auxiliary stream. */
/** Specials placed before the first move: bought ones, plus the lanternfish of a 7-win streak. */
enum class PreLevelBooster { RAY, JELLY, ABYSS_PEARL, LANTERN_FISH }

/**
 * Builds a live [GameState] from a level file variant. The initial board is
 * complete (no random fills at runtime): pieces, blockers and floors all come
 * from the file, so certificates replay exactly.
 */
object LevelBuilder {

    fun start(level: LevelFile, difficulty: Difficulty, boosters: List<PreLevelBooster> = emptyList()): GameState {
        if (level.schemaVersion != LevelFile.SCHEMA_VERSION) {
            throw ModelException("Unsupported schemaVersion ${level.schemaVersion}")
        }
        if (level.engineVersion != ENGINE_VERSION) {
            throw ModelException("Level ${level.id} needs engine ${level.engineVersion}, engine is $ENGINE_VERSION")
        }
        val variant = level.variants[difficulty.name.lowercase()]
            ?: throw ModelException("Level ${level.id} has no ${difficulty.name.lowercase()} variant")
        val width = variant.width
        val height = variant.height
        if (width !in 1..9 || height !in 1..9) throw ModelException("Board must be at most 9x9")
        if (variant.mask.size != height) throw ModelException("Mask must have $height rows")

        val playable = BooleanArray(width * height)
        for (r in 0 until height) {
            val row = variant.mask[r]
            if (row.length != width) throw ModelException("Mask row $r must be $width wide")
            for (c in 0 until width) {
                playable[r * width + c] = row[c] == 'X'
            }
        }

        val colors = variant.colors.map { PieceColor.fromName(it).ordinal }
        if (colors.isEmpty() || colors.size > 6) throw ModelException("Level needs 1..6 colours")

        val state = GameState(
            width = width,
            height = height,
            level = LevelRef(
                id = level.id,
                worldId = level.worldId,
                ordinal = level.ordinal,
                revision = level.revision,
                difficulty = difficulty,
                engineVersion = level.engineVersion,
                seedHex = variant.seedHex,
            ),
            playable = playable,
            colors = colors.toIntArray(),
            spawner = BooleanArray(width),
            lanternSpawner = BooleanArray(width),
            stuckLoses = variant.puzzle,
        )

        for (spec in variant.cells) {
            require(spec.r in 0 until height && spec.c in 0 until width) {
                "Cell (${spec.r},${spec.c}) outside the board"
            }
            applyCell(state, spec)
        }
        for (exit in variant.exits) {
            val i = state.idx(exit.r, exit.c)
            state.floorFlags[i] = state.floorFlags[i] or FloorFlags.EXIT
        }
        for (portal in variant.portals) {
            val from = state.idx(portal.from.r, portal.from.c)
            val to = state.idx(portal.to.r, portal.to.c)
            state.floorFlags[from] = state.floorFlags[from] or FloorFlags.PORTAL_IN
            state.floorFlags[to] = state.floorFlags[to] or FloorFlags.PORTAL_OUT
            state.portalTarget[from] = to
        }
        if (variant.conveyor.isNotEmpty()) {
            val cells = variant.conveyor.map { state.idx(it.r, it.c) }
            for (k in cells.indices) {
                state.conveyorNext[cells[k]] = cells[(k + 1) % cells.size]
            }
        }

        val spawners = variant.spawners
        if (spawners == null) {
            for (c in 0 until width) {
                if (playable[c]) state.spawner[c] = true
            }
        } else {
            for (spawner in spawners) {
                state.spawner[spawner.c] = true
                state.lanternSpawner[spawner.c] = spawner.lanterns
            }
        }

        when (variant.limit.type.lowercase()) {
            "moves" -> {
                state.limitType = LimitType.MOVES
                state.limitValue = variant.limit.value
                state.movesLeft = variant.limit.value
            }
            "time" -> {
                state.limitType = LimitType.TIME
                state.limitValue = variant.limit.value
                state.timeLeftSec = variant.limit.value
            }
            else -> throw ModelException("Unknown limit type ${variant.limit.type}")
        }

        state.objectives = variant.objectives.map { toObjective(it) }
        state.progress = Array(state.objectives.size) { IntArray(state.objectives[it].slots) }

        variant.lanternRules?.let {
            state.lanternTotal = it.total
            state.lanternMaxOnBoard = it.maxOnBoard
            state.lanternEvery = it.everyMoves
        }

        val base = SplitMix64(if (variant.seedHex.isEmpty()) 0L else SplitMix64.seedFromHex(variant.seedHex))
        state.rngColumns = LongArray(width) { base.nextLong() }
        state.rngAux = base.nextLong()

        if (boosters.isNotEmpty()) placeBoosters(state, boosters)
        return state
    }

    private fun applyCell(state: GameState, spec: CellSpec) {
        val i = state.idx(spec.r, spec.c)
        spec.floor?.let {
            state.silt[i] = it.silt.coerceIn(0, 2)
            if (it.glow) state.floorFlags[i] = state.floorFlags[i] or FloorFlags.GLOW
            if (it.exit) state.floorFlags[i] = state.floorFlags[i] or FloorFlags.EXIT
        }
        spec.piece?.let { piece ->
            when (piece.kind.lowercase()) {
                "normal" -> {
                    val color = colorIndex(piece.color)
                    state.pieces[i] = Pieces.plain(color)
                }
                "lantern" -> state.pieces[i] = Pieces.LANTERN
                "puffer" -> {
                    val color = colorIndex(piece.color)
                    state.pieces[i] = Pieces.puffer(color)
                    state.pieceData[i] = piece.counter ?: throw ModelException("Puffer needs a counter")
                }
                else -> throw ModelException("Unknown piece kind ${piece.kind}")
            }
        }
        spec.overlay?.let { overlay ->
            state.overlays[i] = when (overlay.kind.lowercase()) {
                "barnacle" -> Overlays.barnacle(overlay.layers.coerceIn(1, 3))
                "net" -> Overlays.NET
                else -> throw ModelException("Unknown overlay ${overlay.kind}")
            }
        }
        spec.blocker?.let { blocker ->
            when (blocker.kind.lowercase()) {
                "coral" -> state.blockers[i] = Blockers.coral(blocker.layers.coerceIn(1, 3))
                "oil" -> state.blockers[i] = Blockers.OIL
                "seahorsebubble" -> state.blockers[i] = Blockers.SEAHORSE_BUBBLE
                "giantoyster" -> fill2x2(state, spec.r, spec.c) { cell, anchor ->
                    state.blockers[cell] = Blockers.oyster(colorIndex(blocker.color))
                    state.blockerData[cell] = blocker.counter
                    state.blockerAnchor[cell] = anchor
                }
                "sinkhole" -> fill2x2(state, spec.r, spec.c) { cell, anchor ->
                    state.blockers[cell] = Blockers.SINKHOLE
                    state.blockerData[cell] = if (blocker.layers > 1) blocker.layers else blocker.counter
                    state.blockerAnchor[cell] = anchor
                }
                "octopus" -> {
                    state.blockers[i] = Blockers.OCTOPUS
                    state.blockerData[i] = blocker.hp
                }
                else -> throw ModelException("Unknown blocker ${blocker.kind}")
            }
            state.pieces[i] = Pieces.EMPTY
        }
    }

    private fun fill2x2(state: GameState, r: Int, c: Int, action: (cell: Int, anchor: Int) -> Unit) {
        val anchor = state.idx(r, c)
        for (rr in r until minOf(r + 2, state.height)) {
            for (cc in c until minOf(c + 2, state.width)) {
                action(state.idx(rr, cc), anchor)
            }
        }
    }

    private fun colorIndex(name: String?): Int {
        if (name == null) throw ModelException("Colour is required")
        return PieceColor.fromName(name).ordinal
    }

    private fun toObjective(spec: ObjectiveSpec): Objective = when (spec.type) {
        "collect" -> {
            val counts = IntArray(6)
            for ((name, value) in spec.colors) counts[PieceColor.fromName(name).ordinal] = value
            Objective.CollectColors(counts)
        }
        "silt" -> Objective.ClearSilt(spec.count)
        "deliverLanterns" -> Objective.DeliverLanterns(spec.count)
        "orders" -> Objective.Orders(spec.orders.map { toOrder(it) })
        "clearBlockers" -> {
            val counts = IntArray(6)
            for ((name, value) in spec.blockers) {
                val kind = BlockerKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: throw ModelException("Unknown blocker kind in objective: $name")
                counts[Blockers.KIND_ORDER.indexOf(kind)] = value
            }
            Objective.ClearBlockers(counts)
        }
        "spreadGlow" -> Objective.SpreadGlow
        "freeSeahorses" -> Objective.FreeSeahorses(spec.count)
        else -> throw ModelException("Unknown objective type ${spec.type}")
    }

    private fun toOrder(spec: OrderSpec): Order = when (spec.kind) {
        "makeSpecial" -> {
            val kind = SpecialOrderKind.entries.firstOrNull { it.name.equals(spec.value, ignoreCase = true) }
                ?: throw ModelException("Unknown special ${spec.value}")
            Order.MakeSpecial(kind, spec.count)
        }
        "makeCombo" -> {
            val combo = ComboKind.entries.firstOrNull { it.name.equals(spec.value, ignoreCase = true) }
                ?: throw ModelException("Unknown combo ${spec.value}")
            Order.MakeCombo(combo, spec.count)
        }
        "collect" -> Order.Collect(colorIndex(spec.colorName), spec.count)
        "breakLayers" -> {
            val kind = BlockerKind.entries.firstOrNull { it.name.equals(spec.value, ignoreCase = true) }
                ?: throw ModelException("Unknown blocker ${spec.value}")
            Order.BreakLayers(kind, spec.count)
        }
        else -> throw ModelException("Unknown order kind ${spec.kind}")
    }

    /** Pre-level boosters land on random plain cells, chosen by the auxiliary stream. */
    private fun placeBoosters(state: GameState, boosters: List<PreLevelBooster>) {
        for (booster in boosters) {
            val candidates = (0 until state.size).filter { i ->
                state.pieces[i] in 1..6 && state.overlays[i] == Overlays.NONE && state.blockers[i] == Blockers.NONE
            }
            if (candidates.isEmpty()) return
            val stream = SplitMix64(state.rngAux)
            val cell = candidates[stream.nextInt(candidates.size)]
            val code = when (booster) {
                PreLevelBooster.RAY -> {
                    val color = Pieces.colorOf(state.pieces[cell])
                    if (stream.nextInt(2) == 0) Pieces.rayAlongRow(color) else Pieces.rayAlongColumn(color)
                }
                PreLevelBooster.JELLY -> Pieces.jelly(Pieces.colorOf(state.pieces[cell]))
                PreLevelBooster.ABYSS_PEARL -> Pieces.PEARL
                PreLevelBooster.LANTERN_FISH -> Pieces.lanternFish(Pieces.colorOf(state.pieces[cell]))
            }
            state.rngAux = stream.state
            state.pieces[cell] = code
        }
    }
}
