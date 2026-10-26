package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.ComboKind
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Order
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.SpecialKind
import pt.tiago.nightcrush.core.model.SpecialOrderKind

/**
 * One move being resolved: cascades, specials, chains, blockers, glow and score.
 *
 * The class mutates its own copy of the state; [GameEngine.applyMove] hands it a
 * fresh copy so the input state is never touched.
 */
internal class Resolution(val s: GameState, private val events: MutableList<GameEvent>?) {

    /** Cascade number used for the score multiplier (1 is the first). */
    var cascadeIndex = 1
    var oilsDestroyedThisMove = false

    /** Jellies that already did their first explosion and await their second. */
    val armedJelly = BooleanArray(s.size)
    val pendingJellies = HashSet<Int>()

    /** Explosion radius for each pending jelly (1 normal, 2 for the jelly-jelly combo). */
    val jellyRadius = IntArray(s.size)

    private val activatedThisPhase = BooleanArray(s.size)
    /** Packed (cell shl 8) or code: the piece may be gone by the time it activates. */
    private val queue = ArrayDeque<Int>()
    private var pendingScore = 0

    fun emit(event: GameEvent) {
        events?.add(event)
    }

    /** Records the board for the animation; free when events are not recorded. */
    fun snapshot() {
        val list = events ?: return
        list.add(GameEvent.Board(BoardSnapshot.of(s)))
    }

    fun addScore(points: Int) {
        if (points == 0) return
        s.score += points
        pendingScore += points
    }

    fun flushScore() {
        if (pendingScore == 0) return
        // -1: the points are not tied to one cell.
        emit(GameEvent.ScoreDelta(pendingScore, -1))
        pendingScore = 0
    }

    // ---------------------------------------------------------------- swaps

    fun swapCells(a: Int, b: Int) {
        val p = s.pieces[a]; s.pieces[a] = s.pieces[b]; s.pieces[b] = p
        val d = s.pieceData[a]; s.pieceData[a] = s.pieceData[b]; s.pieceData[b] = d
        val o = s.overlays[a]; s.overlays[a] = s.overlays[b]; s.overlays[b] = o
        val ls = s.landStamp[a]; s.landStamp[a] = s.landStamp[b]; s.landStamp[b] = ls
        val aj = armedJelly[a]; armedJelly[a] = armedJelly[b]; armedJelly[b] = aj
        val jr = jellyRadius[a]; jellyRadius[a] = jellyRadius[b]; jellyRadius[b] = jr
        if (pendingJellies.remove(a)) pendingJellies.add(b)
        if (pendingJellies.remove(b)) pendingJellies.add(a)
    }

    fun isSwappable(i: Int): Boolean {
        val code = s.pieces[i]
        if (code == Pieces.EMPTY) return false
        if (!s.playable[i]) return false
        if (s.blockers[i] != Blockers.NONE) return false
        if (s.overlays[i] != Overlays.NONE) return false
        return true
    }

    /**
     * Special use triggered by swapping two special pieces, or a lantern fish
     * with anything. Null when the swap must create a match to be valid.
     */
    fun forcedUse(a: Int, b: Int): ForcedUse? = detectForced(s, a, b)

    fun applyForced(use: ForcedUse) {
        when (use) {
            is ForcedUse.Combo -> applyCombo(use.combo, use.first, use.second)
            is ForcedUse.Fish -> activateFish(use.cell)
        }
    }

    /** Finale rain: every ray fires once, wherever it stands. */
    fun finaleFireAllRays() {
        java.util.Arrays.fill(activatedThisPhase, false)
        for (i in 0 until s.size) if (Pieces.isRay(s.pieces[i])) enqueueActivation(i, s.pieces[i])
        runActivations()
    }

    /** Run queued activations (used after boosters and combos clear specials). */
    fun runPendingActivations() {
        runActivations()
    }

    // ------------------------------------------------------------- cascades

    /**
     * Resolve match groups and pending jelly explosions, settling gravity in
     * between until the board is stable. Counts as cascade steps for scoring.
     */
    fun resolveCascades(
        initialGroups: List<MatchFinder.Group>?,
        initiatorA: Int,
        initiatorB: Int,
        startStep: Int = 1,
    ) {
        var step = startStep
        // Without groups the caller cleared cells directly (boosters, forced swaps,
        // conveyors, the finale): fill the holes before looking for matches.
        if (initialGroups == null) Gravity.settle(this)
        var groups = initialGroups ?: MatchFinder.find(s)
        var a = initiatorA
        var b = initiatorB
        while (true) {
            val hasJellies = pendingJellies.isNotEmpty()
            if (groups.isEmpty() && !hasJellies) break
            cascadeIndex = step
            if (step > s.maxCascade) s.maxCascade = step
            emit(GameEvent.CascadeStep(step))
            java.util.Arrays.fill(activatedThisPhase, false)
            // Armed jellyfish go first: a match never clears them, so a group that
            // contains one would repeat until the cascade guard.
            if (hasJellies) processPendingJellies() else processGroups(groups, a, b)
            a = -1
            b = -1
            runActivations()
            flushScore()
            Gravity.settle(this)
            step++
            if (step > startStep + 400) break
            groups = MatchFinder.find(s)
        }
        cascadeIndex = maxOf(startStep, step - 1)
    }

    private fun processGroups(groups: List<MatchFinder.Group>, initiatorA: Int, initiatorB: Int) {
        for (group in groups) {
            emit(GameEvent.Matched(group.cells.map { Gravity.cell(s, it) }, group.kind, group.color))
            val kindPoints = when (group.kind) {
                MatchKind.THREE -> 60
                MatchKind.FOUR -> 120
                MatchKind.LT -> 200
                MatchKind.SQUARE -> 100
                MatchKind.FIVE -> 200
            }
            addScore(kindPoints * cascadeIndex)

            val spawn = chooseSpawnCell(group, initiatorA, initiatorB)
            val newSpecial = newSpecialFor(group)
            var specialCell = -1
            if (newSpecial != null && spawn >= 0) {
                specialCell = spawn
                s.pieces[spawn] = newSpecial
                s.pieceData[spawn] = 0
                s.landStamp[spawn] = s.landCounter
                val kind = when {
                    Pieces.isRay(newSpecial) -> SpecialKind.RAY
                    Pieces.isJelly(newSpecial) -> SpecialKind.JELLY
                    Pieces.isLanternFish(newSpecial) -> SpecialKind.LANTERN_FISH
                    else -> SpecialKind.ABYSS_PEARL
                }
                emit(GameEvent.SpecialCreated(Gravity.cell(s, spawn), kind, group.color))
                ObjectiveTracker.onSpecialCreated(this, kind)
            }

            if (group.cells.any { s.isGlow(it) }) lightCells(group.cells.toList())

            for (cell in group.cells) {
                if (cell == specialCell) continue
                clearCell(cell, ClearCause.MATCH, points = 0)
            }
            damageAdjacentToGroup(group)
        }
    }

    private fun newSpecialFor(group: MatchFinder.Group): Int? = when (group.kind) {
        MatchKind.THREE -> null
        MatchKind.FOUR -> {
            val run = group.runs.firstOrNull { it.length == 4 }
            if (run != null && run.horizontal) Pieces.rayAlongColumn(group.color)
            else Pieces.rayAlongRow(group.color)
        }
        MatchKind.LT -> Pieces.jelly(group.color)
        MatchKind.SQUARE -> Pieces.lanternFish(group.color)
        MatchKind.FIVE -> Pieces.PEARL
    }

    /** Where the new special is born: where the player moved, else where the last piece fell. */
    private fun chooseSpawnCell(group: MatchFinder.Group, initiatorA: Int, initiatorB: Int): Int {
        val candidates = group.cells.filter { cell ->
            val code = s.pieces[cell]
            (Pieces.isMatchable(code) && Pieces.specialOf(code) == null) && s.overlays[cell] == Overlays.NONE
        }
        if (candidates.isEmpty()) return -1
        if (initiatorB >= 0 && candidates.contains(initiatorB)) return initiatorB
        if (initiatorA >= 0 && candidates.contains(initiatorA)) return initiatorA
        return candidates.maxWithOrNull(
            compareBy({ s.landStamp[it] }, { s.rowOf(it) }, { -s.colOf(it) })
        ) ?: candidates.first()
    }

    /** One orthogonal step of damage from a finished group. */
    private fun damageAdjacentToGroup(group: MatchFinder.Group) {
        val groupCells = group.cells.toHashSet()
        val adjacent = LinkedHashSet<Int>()
        for (cell in group.cells) {
            val r = s.rowOf(cell)
            val c = s.colOf(cell)
            if (r > 0) adjacent.add(s.idx(r - 1, c))
            if (r < s.height - 1) adjacent.add(s.idx(r + 1, c))
            if (c > 0) adjacent.add(s.idx(r, c - 1))
            if (c < s.width - 1) adjacent.add(s.idx(r, c + 1))
        }
        for (cell in adjacent) {
            if (!s.playable[cell]) continue
            // A barnacled piece inside the group already lost its layer while clearing.
            if (Overlays.isBarnacle(s.overlays[cell]) && cell !in groupCells) damageBarnacle(cell)
            val blocker = s.blockers[cell]
            if (blocker == Blockers.NONE) continue
            when {
                blocker in 1..3 -> damageBlocker(cell, 1, ClearCause.MATCH)
                blocker == Blockers.OIL -> destroyOil(cell)
                blocker == Blockers.SEAHORSE_BUBBLE -> popSeahorse(cell)
                Blockers.isOyster(blocker) && Blockers.oysterColor(blocker) == group.color ->
                    damageBlocker(cell, 1, ClearCause.MATCH)
                blocker == Blockers.OCTOPUS -> damageBlocker(cell, 1, ClearCause.MATCH)
                // Sinkholes ignore plain matches.
            }
        }
    }

    // ---------------------------------------------------------- clear/damage

    /**
     * Clear one cell: piece, overlay, silt and chained special activation.
     * Barnacled pieces only lose a layer; jellies survive their first explosion.
     */
    fun clearCell(cell: Int, cause: ClearCause, points: Int, force: Boolean = false) {
        if (!s.playable[cell]) return
        val code = s.pieces[cell]
        val blocker = s.blockers[cell]
        if (blocker != Blockers.NONE) {
            damageBlocker(cell, 1, cause)
            return
        }
        if (code == Pieces.EMPTY) {
            clearSilt(cell)
            return
        }
        if (Overlays.isBarnacle(s.overlays[cell])) {
            damageBarnacle(cell)
            return
        }
        if (Pieces.isJelly(code) && !force) {
            if (!armedJelly[cell]) enqueueActivation(cell, code)
            return
        }
        if (Overlays.isNet(s.overlays[cell])) {
            s.overlays[cell] = Overlays.NONE
            emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.NET, 0))
        }
        s.pieces[cell] = Pieces.EMPTY
        s.pieceData[cell] = 0
        s.landStamp[cell] = 0
        emit(GameEvent.PieceCleared(Gravity.cell(s, cell), Pieces.colorOf(code), code, cause))
        if (points != 0) addScore(points * cascadeIndex)
        ObjectiveTracker.onPieceCleared(this, code)
        if (s.silt[cell] > 0) clearSilt(cell)
        val special = Pieces.specialOf(code)
        if ((special != null || code == Pieces.PEARL) && !force) enqueueActivation(cell, code)
    }

    private fun clearSilt(cell: Int) {
        if (s.silt[cell] <= 0) return
        s.silt[cell]--
        addScore(100 * cascadeIndex)
        emit(GameEvent.SiltCleared(Gravity.cell(s, cell)))
        ObjectiveTracker.onSiltCleared(this)
    }

    private fun damageBarnacle(cell: Int) {
        val layers = Overlays.barnacleLayers(s.overlays[cell])
        if (layers <= 0) return
        val remaining = layers - 1
        s.overlays[cell] = if (remaining == 0) Overlays.NONE else Overlays.barnacle(remaining)
        addScore(20 * cascadeIndex)
        emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.BARNACLE, remaining))
        ObjectiveTracker.onLayerDamaged(this, DamageTarget.BARNACLE)
    }

    private fun destroyOil(cell: Int) {
        s.blockers[cell] = Blockers.NONE
        oilsDestroyedThisMove = true
        addScore(20 * cascadeIndex)
        emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.OIL, 0))
        ObjectiveTracker.onLayerDamaged(this, DamageTarget.OIL)
    }

    private fun popSeahorse(cell: Int) {
        s.blockers[cell] = Blockers.NONE
        s.blockerData[cell] = 0
        s.seahorsesFreed++
        addScore(500 * cascadeIndex)
        emit(GameEvent.SeahorseFreed(Gravity.cell(s, cell)))
        ObjectiveTracker.onSeahorseFreed(this)
    }

    /** Damage any blocker by an area effect (rays, jellies, fish, combos). */
    fun damageBlocker(cell: Int, amount: Int, cause: ClearCause) {
        val blocker = s.blockers[cell]
        if (blocker == Blockers.NONE) return
        when {
            blocker in 1..3 -> {
                val remaining = blocker - amount
                if (remaining <= 0) {
                    s.blockers[cell] = Blockers.NONE
                    emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.CORAL, 0))
                } else {
                    s.blockers[cell] = remaining
                    emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.CORAL, remaining))
                }
                addScore(20 * cascadeIndex)
                ObjectiveTracker.onLayerDamaged(this, DamageTarget.CORAL)
            }
            blocker == Blockers.OIL -> destroyOil(cell)
            blocker == Blockers.SEAHORSE_BUBBLE -> popSeahorse(cell)
            Blockers.isOyster(blocker) -> {
                val anchor = if (s.blockerAnchor[cell] >= 0) s.blockerAnchor[cell] else cell
                s.blockerData[anchor] -= amount
                val remaining = s.blockerData[anchor]
                if (remaining <= 0) {
                    openOyster(anchor)
                } else {
                    emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.GIANT_OYSTER, remaining))
                }
                ObjectiveTracker.onLayerDamaged(this, DamageTarget.GIANT_OYSTER)
            }
            blocker == Blockers.SINKHOLE -> {
                val anchor = if (s.blockerAnchor[cell] >= 0) s.blockerAnchor[cell] else cell
                s.blockerData[anchor] -= amount
                val remaining = s.blockerData[anchor]
                if (remaining <= 0) closeSinkhole(anchor) else
                    emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.SINKHOLE, remaining))
                ObjectiveTracker.onLayerDamaged(this, DamageTarget.SINKHOLE)
            }
            blocker == Blockers.OCTOPUS -> {
                s.blockerData[cell] -= amount
                val remaining = s.blockerData[cell]
                if (remaining <= 0) {
                    s.blockers[cell] = Blockers.NONE
                    emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.OCTOPUS, 0))
                } else {
                    emit(GameEvent.LayerDamaged(Gravity.cell(s, cell), DamageTarget.OCTOPUS, remaining))
                }
                ObjectiveTracker.onLayerDamaged(this, DamageTarget.OCTOPUS)
            }
        }
    }

    private fun openOyster(anchor: Int) {
        forEachOysterCell(anchor) { cell ->
            s.blockers[cell] = Blockers.NONE
            s.blockerData[cell] = 0
            s.blockerAnchor[cell] = -1
        }
        addScore(2000 * cascadeIndex)
        emit(GameEvent.OysterOpened(Gravity.cell(s, anchor)))
    }

    private fun closeSinkhole(anchor: Int) {
        forEachOysterCell(anchor) { cell ->
            s.blockers[cell] = Blockers.NONE
            s.blockerData[cell] = 0
            s.blockerAnchor[cell] = -1
        }
        emit(GameEvent.LayerDamaged(Gravity.cell(s, anchor), DamageTarget.SINKHOLE, 0))
    }

    private fun forEachOysterCell(anchor: Int, action: (Int) -> Unit) {
        val r = s.rowOf(anchor)
        val c = s.colOf(anchor)
        for (rr in r until minOf(r + 2, s.height)) {
            for (cc in c until minOf(c + 2, s.width)) action(s.idx(rr, cc))
        }
    }

    /** A cell hit by an area effect: blocker damage, piece clear, or silt removal. */
    fun hitCell(cell: Int, cause: ClearCause, points: Int) {
        if (!s.playable[cell]) return
        clearCell(cell, cause, points)
    }

    // ---------------------------------------------------------- activations

    private fun enqueueActivation(cell: Int, code: Int) {
        if (activatedThisPhase[cell]) return
        queue.addLast((cell shl 8) or code)
    }

    private fun runActivations() {
        var guard = 0
        while (queue.isNotEmpty()) {
            if (++guard > 2000) break
            val packed = queue.removeFirst()
            val cell = packed ushr 8
            val code = packed and 0xFF
            if (activatedThisPhase[cell]) continue
            activatedThisPhase[cell] = true
            when {
                Pieces.isRay(code) -> activateRay(cell, rayIsHorizontal(code))
                Pieces.isJelly(code) -> activateJelly(cell)
                Pieces.isLanternFish(code) -> activateFish(cell)
                code == Pieces.PEARL -> activatePearl(cell, mostFrequentColor())
                else -> Unit
            }
        }
    }

    private fun rayIsHorizontal(code: Int): Boolean = code in 7..12

    private fun activateRay(cell: Int, horizontal: Boolean) {
        activatedThisPhase[cell] = true
        val area = lineCells(cell, horizontal)
        emit(GameEvent.SpecialActivated(Gravity.cell(s, cell), SpecialKind.RAY, area.map { Gravity.cell(s, it) }))
        lightIfSourceGlow(cell, area)
        for (target in area) hitCell(target, ClearCause.SPECIAL, 60)
    }

    private fun activateJelly(cell: Int) {
        activatedThisPhase[cell] = true
        val area = neighborhood(cell, radius = 1).filter { it != cell }
        emit(GameEvent.SpecialActivated(Gravity.cell(s, cell), SpecialKind.JELLY, area.map { Gravity.cell(s, it) }))
        lightIfSourceGlow(cell, area)
        armedJelly[cell] = true
        jellyRadius[cell] = 1
        pendingJellies.add(cell)
        for (target in area) hitCell(target, ClearCause.SPECIAL, 60)
    }

    private fun processPendingJellies() {
        val armed = pendingJellies.sorted()
        for (cell in armed) {
            pendingJellies.remove(cell)
            if (!Pieces.isJelly(s.pieces[cell])) {
                armedJelly[cell] = false
                jellyRadius[cell] = 0
                continue
            }
            val radius = if (jellyRadius[cell] > 0) jellyRadius[cell] else 1
            val area = neighborhood(cell, radius = radius).filter { it != cell }
            emit(GameEvent.SpecialActivated(Gravity.cell(s, cell), SpecialKind.JELLY, area.map { Gravity.cell(s, it) }))
            lightIfSourceGlow(cell, area)
            for (target in area) hitCell(target, ClearCause.SPECIAL, 60)
            clearCell(cell, ClearCause.SPECIAL, points = 60, force = true)
            armedJelly[cell] = false
            jellyRadius[cell] = 0
        }
    }

    private fun activateFish(cell: Int) {
        activatedThisPhase[cell] = true
        val neighbors = orthogonalNeighbors(cell)
        val target = chooseFishTarget(cell)
        emit(
            GameEvent.SpecialActivated(
                Gravity.cell(s, cell), SpecialKind.LANTERN_FISH,
                (neighbors + listOfNotNull(if (target >= 0) target else null)).map { Gravity.cell(s, it) },
            ),
        )
        lightIfSourceGlow(cell, neighbors)
        for (target2 in neighbors) hitCell(target2, ClearCause.SPECIAL, 60)
        if (target >= 0) hitCell(target, ClearCause.SPECIAL, 60)
        clearCell(cell, ClearCause.SPECIAL, points = 60)
    }

    private fun activatePearl(cell: Int, color: Int) {
        activatedThisPhase[cell] = true
        val area = (0 until s.size).filter { i ->
            s.playable[i] && i != cell && Pieces.colorOf(s.pieces[i]) == color
        }
        emit(GameEvent.SpecialActivated(Gravity.cell(s, cell), SpecialKind.ABYSS_PEARL, area.map { Gravity.cell(s, it) }))
        lightIfSourceGlow(cell, area)
        for (target in area) hitCell(target, ClearCause.SPECIAL, 60)
        clearCell(cell, ClearCause.SPECIAL, points = 60)
    }

    private fun mostFrequentColor(): Int {
        val counts = IntArray(6)
        for (i in 0 until s.size) {
            val color = Pieces.colorOf(s.pieces[i])
            if (color >= 0) counts[color]++
        }
        var best = -1
        var bestCount = 0
        for (color in 0 until 6) {
            if (counts[color] > bestCount) {
                bestCount = counts[color]
                best = color
            }
        }
        return if (best < 0) -1 else best
    }

    // ----------------------------------------------------------------- combos

    fun applyCombo(combo: ComboKind, first: Int, second: Int) {
        s.combosMade[combo.ordinal]++
        ObjectiveTracker.onCombo(this, combo)
        // The two swapped specials are spent by the combo: they must not fire again.
        if (combo != ComboKind.PEARL_RAY && combo != ComboKind.PEARL_JELLY && combo != ComboKind.PEARL_FISH) {
            activatedThisPhase[first] = true
            activatedThisPhase[second] = true
        } else {
            val pearlCell = if (s.pieces[first] == Pieces.PEARL) first else second
            activatedThisPhase[pearlCell] = true
        }
        when (combo) {
            ComboKind.RAY_RAY -> {
                val dest = second
                val area = (lineCells(dest, horizontal = true) + lineCells(dest, horizontal = false)).distinct()
                emit(GameEvent.ComboActivated(combo, area.map { Gravity.cell(s, it) }))
                for (target in area) hitCell(target, ClearCause.COMBO, 60)
                clearCells(first, second)
            }
            ComboKind.RAY_JELLY -> {
                val dest = second
                val area = crossCells(dest, radius = 1)
                emit(GameEvent.ComboActivated(combo, area.map { Gravity.cell(s, it) }))
                for (target in area) hitCell(target, ClearCause.COMBO, 60)
                clearCells(first, second)
            }
            ComboKind.JELLY_JELLY -> {
                val dest = second
                val area = neighborhood(dest, radius = 2)
                emit(GameEvent.ComboActivated(combo, area.map { Gravity.cell(s, it) }))
                for (target in area) {
                    if (target != first && target != second) hitCell(target, ClearCause.COMBO, 60)
                }
                // Both jellyfish survive to explode a second time (5x5) where they land.
                armComboJelly(first)
                armComboJelly(second)
            }
            ComboKind.FISH_RAY, ComboKind.FISH_JELLY -> {
                val fishCell = if (Pieces.isLanternFish(s.pieces[first])) first else second
                val specialCell = if (fishCell == first) second else first
                val target = chooseFishTarget(fishCell)
                val area = if (target >= 0) {
                    if (combo == ComboKind.FISH_RAY) lineCells(target, rayIsHorizontal(s.pieces[specialCell]))
                    else neighborhood(target, radius = 1).filter { it != target }
                } else emptyList()
                emit(GameEvent.ComboActivated(combo, area.map { Gravity.cell(s, it) }))
                for (cell in area) hitCell(cell, ClearCause.COMBO, 60)
                if (target >= 0) hitCell(target, ClearCause.COMBO, 60)
                clearCells(first, second)
            }
            ComboKind.FISH_FISH -> {
                val targets = chooseFishTargets(first, 3)
                emit(GameEvent.ComboActivated(combo, targets.map { Gravity.cell(s, it) }))
                for (target in targets) hitCell(target, ClearCause.COMBO, 60)
                clearCells(first, second)
            }
            ComboKind.PEARL_PLAIN -> {
                val pearlCell = if (s.pieces[first] == Pieces.PEARL) first else second
                val otherCell = if (pearlCell == first) second else first
                val color = Pieces.colorOf(s.pieces[otherCell])
                val area = (0 until s.size).filter { i ->
                    s.playable[i] && i != pearlCell && Pieces.colorOf(s.pieces[i]) == color
                }
                emit(GameEvent.ComboActivated(combo, area.map { Gravity.cell(s, it) }))
                for (target in area) hitCell(target, ClearCause.COMBO, 60)
                clearCell(pearlCell, ClearCause.COMBO, points = 60)
            }
            ComboKind.PEARL_RAY, ComboKind.PEARL_JELLY, ComboKind.PEARL_FISH -> {
                val pearlCell = if (s.pieces[first] == Pieces.PEARL) first else second
                val otherCell = if (pearlCell == first) second else first
                val color = Pieces.colorOf(s.pieces[otherCell])
                val converts = (0 until s.size).filter { i ->
                    s.playable[i] && Pieces.colorOf(s.pieces[i]) == color
                }
                val code = when (combo) {
                    ComboKind.PEARL_RAY -> {
                        val stream = pt.tiago.nightcrush.core.random.SplitMix64(s.rngAux)
                        val horizontal = stream.nextInt(2) == 0
                        s.rngAux = stream.state
                        if (horizontal) 7 + color else 13 + color
                    }
                    ComboKind.PEARL_JELLY -> Pieces.jelly(color)
                    else -> Pieces.lanternFish(color)
                }
                emit(GameEvent.ComboActivated(combo, converts.map { Gravity.cell(s, it) }))
                for (cell in converts) s.pieces[cell] = code
                for (cell in converts) {
                    if (activatedThisPhase[cell]) continue
                    activatedThisPhase[cell] = true
                    when {
                        Pieces.isRay(code) -> activateRay(cell, rayIsHorizontal(code))
                        Pieces.isJelly(code) -> activateJelly(cell)
                        Pieces.isLanternFish(code) -> activateFish(cell)
                    }
                }
                clearCell(pearlCell, ClearCause.COMBO, points = 60)
            }
            ComboKind.PEARL_PEARL -> {
                val area = (0 until s.size).filter { s.playable[it] && s.pieces[it] != Pieces.EMPTY }
                emit(GameEvent.ComboActivated(combo, area.map { Gravity.cell(s, it) }))
                for (target in area) clearCell(target, ClearCause.COMBO, points = 60)
                for (i in 0 until s.size) {
                    if (s.playable[i] && s.blockers[i] != Blockers.NONE) damageBlocker(i, 1, ClearCause.COMBO)
                }
            }
        }
        flushScore()
    }

    private fun armComboJelly(cell: Int) {
        if (Pieces.isJelly(s.pieces[cell])) {
            armedJelly[cell] = true
            jellyRadius[cell] = 2
            pendingJellies.add(cell)
            activatedThisPhase[cell] = true
        }
    }

    private fun clearCells(first: Int, second: Int) {
        // The two swapped specials are consumed by the combo, even jellyfish.
        clearCell(first, ClearCause.COMBO, points = 60, force = true)
        clearCell(second, ClearCause.COMBO, points = 60, force = true)
    }

    // ------------------------------------------------------------- fish aims

    /** Deterministic "most useful target" for the lantern fish. */
    fun chooseFishTarget(origin: Int): Int {
        val candidates = fishTargets(origin, limit = 1)
        return candidates.firstOrNull() ?: -1
    }

    private fun chooseFishTargets(origin: Int, count: Int): List<Int> {
        val chosen = ArrayList<Int>(count)
        val excluded = HashSet<Int>()
        excluded.add(origin)
        repeat(count) {
            val target = fishTargets(origin, limit = 1, excluded).firstOrNull() ?: return chosen
            chosen.add(target)
            excluded.add(target)
        }
        return chosen
    }

    /**
     * Priority 1: needed by an objective; priority 2: any blocker;
     * priority 3: a piece of an objective colour. Ties go to the aux stream.
     */
    private fun fishTargets(origin: Int, limit: Int, excluded: Set<Int> = emptySet()): List<Int> {
        val p1 = ArrayList<Int>()
        val p2 = ArrayList<Int>()
        val p3 = ArrayList<Int>()
        val objectiveColors = HashSet<Int>()
        var wantsSilt = false
        var wantsGlow = false
        var wantsBubbles = false
        var wantsLanternDrop = false
        val wantedBlockerKinds = HashSet<Int>()
        for (objective in s.objectives) {
            when (objective) {
                is Objective.ClearSilt -> wantsSilt = true
                is Objective.SpreadGlow -> wantsGlow = true
                is Objective.FreeSeahorses -> wantsBubbles = true
                is Objective.DeliverLanterns -> wantsLanternDrop = true
                is Objective.CollectColors -> for (color in 0 until 6) if (objective.counts[color] > 0) objectiveColors.add(color)
                is Objective.ClearBlockers -> for (kindIndex in 0 until 6) if (objective.counts[kindIndex] > 0) wantedBlockerKinds.add(kindIndex)
                is Objective.Orders -> for (order in objective.orders) {
                    when (order) {
                        is Order.Collect -> objectiveColors.add(order.color)
                        is Order.BreakLayers -> wantedBlockerKinds.add(Blockers.KIND_ORDER.indexOf(order.kind))
                        else -> Unit
                    }
                }
            }
        }
        for (i in 0 until s.size) {
            if (!s.playable[i] || i == origin || i in excluded) continue
            val blocker = s.blockers[i]
            if (wantsSilt && s.silt[i] > 0) {
                p1.add(i)
                continue
            }
            if (blocker != Blockers.NONE) {
                val kind = Blockers.kindOf(blocker)
                if (kind != null && wantedBlockerKinds.contains(Blockers.KIND_ORDER.indexOf(kind))) p1.add(i)
                if (blocker == Blockers.SEAHORSE_BUBBLE && wantsBubbles) p1.add(i)
                p2.add(i)
                continue
            }
            if (wantsGlow && !s.isGlow(i)) {
                p1.add(i)
                continue
            }
            if (wantsLanternDrop) {
                val above = if (s.rowOf(i) > 0) i - s.width else -1
                if (above >= 0 && s.pieces[above] == Pieces.LANTERN) {
                    p1.add(i)
                    continue
                }
            }
            val color = Pieces.colorOf(s.pieces[i])
            if (color >= 0 && objectiveColors.contains(color)) p3.add(i)
        }
        val pool = when {
            p1.isNotEmpty() -> p1
            p2.isNotEmpty() -> p2
            p3.isNotEmpty() -> p3
            else -> return emptyList()
        }
        val sorted = pool.sorted()
        val stream = pt.tiago.nightcrush.core.random.SplitMix64(s.rngAux)
        val index = stream.nextInt(sorted.size)
        s.rngAux = stream.state
        return listOf(sorted[index])
    }

    // ------------------------------------------------------------------ glow

    private fun lightIfSourceGlow(source: Int, area: List<Int>) {
        if (!s.isGlow(source)) return
        lightCells(area)
    }

    private fun lightCells(cells: List<Int>) {
        val newlyLit = ArrayList<Int>()
        for (cell in cells) {
            if (!s.playable[cell]) continue
            if (!s.isGlow(cell)) {
                s.floorFlags[cell] = s.floorFlags[cell] or pt.tiago.nightcrush.core.model.FloorFlags.GLOW
                newlyLit.add(cell)
            }
        }
        if (newlyLit.isNotEmpty()) {
            emit(GameEvent.GlowSpread(newlyLit.map { Gravity.cell(s, it) }))
            ObjectiveTracker.onGlowSpread(this)
        }
    }

    // ------------------------------------------------------------- geometry

    fun lineCells(cell: Int, horizontal: Boolean): List<Int> {
        val r = s.rowOf(cell)
        val c = s.colOf(cell)
        return if (horizontal) {
            (0 until s.width).mapNotNull { cc -> if (s.playable[s.idx(r, cc)]) s.idx(r, cc) else null }
        } else {
            (0 until s.height).mapNotNull { rr -> if (s.playable[s.idx(rr, c)]) s.idx(rr, c) else null }
        }
    }

    private fun crossCells(cell: Int, radius: Int): List<Int> {
        val r = s.rowOf(cell)
        val c = s.colOf(cell)
        val cells = ArrayList<Int>()
        for (rr in maxOf(0, r - radius)..minOf(s.height - 1, r + radius)) {
            for (cc in 0 until s.width) {
                val i = s.idx(rr, cc)
                if (s.playable[i]) cells.add(i)
            }
        }
        for (cc in maxOf(0, c - radius)..minOf(s.width - 1, c + radius)) {
            for (rr in 0 until s.height) {
                val i = s.idx(rr, cc)
                if (s.playable[i] && !cells.contains(i)) cells.add(i)
            }
        }
        return cells
    }

    fun neighborhood(cell: Int, radius: Int): List<Int> {
        val r = s.rowOf(cell)
        val c = s.colOf(cell)
        val cells = ArrayList<Int>()
        for (rr in maxOf(0, r - radius)..minOf(s.height - 1, r + radius)) {
            for (cc in maxOf(0, c - radius)..minOf(s.width - 1, c + radius)) {
                val i = s.idx(rr, cc)
                if (s.playable[i]) cells.add(i)
            }
        }
        return cells
    }

    fun orthogonalNeighbors(cell: Int): List<Int> {
        val r = s.rowOf(cell)
        val c = s.colOf(cell)
        val cells = ArrayList<Int>(4)
        if (r > 0 && s.playable[s.idx(r - 1, c)]) cells.add(s.idx(r - 1, c))
        if (r < s.height - 1 && s.playable[s.idx(r + 1, c)]) cells.add(s.idx(r + 1, c))
        if (c > 0 && s.playable[s.idx(r, c - 1)]) cells.add(s.idx(r, c - 1))
        if (c < s.width - 1 && s.playable[s.idx(r, c + 1)]) cells.add(s.idx(r, c + 1))
        return cells
    }
}

internal fun specialOrderKind(kind: SpecialKind): SpecialOrderKind = when (kind) {
    SpecialKind.RAY -> SpecialOrderKind.RAY
    SpecialKind.JELLY -> SpecialOrderKind.JELLY
    SpecialKind.LANTERN_FISH -> SpecialOrderKind.LANTERN_FISH
    SpecialKind.ABYSS_PEARL -> SpecialOrderKind.ABYSS_PEARL
}
