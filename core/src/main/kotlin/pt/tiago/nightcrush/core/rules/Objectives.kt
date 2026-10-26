package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.ComboKind
import pt.tiago.nightcrush.core.model.FloorFlags
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Order
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.SpecialKind

/** Counts everything the objectives and orders ask for. */
internal object ObjectiveTracker {

    fun onPieceCleared(r: Resolution, code: Int) {
        val color = Pieces.colorOf(code)
        forEachObjective(r.s) { index, objective ->
            when (objective) {
                is Objective.CollectColors -> {
                    if (color >= 0 && color < 6 && objective.counts[color] > 0) {
                        bump(r, index, color)
                    }
                }
                is Objective.Orders -> {
                    objective.orders.forEachIndexed { slot, order ->
                        if (order is Order.Collect && order.color == color) bump(r, index, slot)
                    }
                }
                else -> Unit
            }
        }
    }

    fun onSpecialCreated(r: Resolution, kind: SpecialKind) {
        val created = specialOrderKind(kind)
        forEachObjective(r.s) { index, objective ->
            if (objective is Objective.Orders) {
                objective.orders.forEachIndexed { slot, order ->
                    if (order is Order.MakeSpecial && order.kind == created) bump(r, index, slot)
                }
            }
        }
    }

    fun onCombo(r: Resolution, combo: ComboKind) {
        forEachObjective(r.s) { index, objective ->
            if (objective is Objective.Orders) {
                objective.orders.forEachIndexed { slot, order ->
                    if (order is Order.MakeCombo && order.combo == combo) bump(r, index, slot)
                }
            }
        }
    }

    fun onLayerDamaged(r: Resolution, target: DamageTarget) {
        val blockerKind = when (target) {
            DamageTarget.CORAL -> Blockers.KIND_ORDER[0]
            DamageTarget.OIL -> Blockers.KIND_ORDER[1]
            DamageTarget.SEAHORSE_BUBBLE -> Blockers.KIND_ORDER[2]
            DamageTarget.GIANT_OYSTER -> Blockers.KIND_ORDER[3]
            DamageTarget.SINKHOLE -> Blockers.KIND_ORDER[4]
            DamageTarget.OCTOPUS -> Blockers.KIND_ORDER[5]
            DamageTarget.BARNACLE, DamageTarget.NET -> return
        }
        val kindIndex = Blockers.KIND_ORDER.indexOf(blockerKind)
        forEachObjective(r.s) { index, objective ->
            when (objective) {
                is Objective.ClearBlockers -> if (objective.counts[kindIndex] > 0) bump(r, index, kindIndex)
                is Objective.Orders -> objective.orders.forEachIndexed { slot, order ->
                    if (order is Order.BreakLayers && order.kind == blockerKind) bump(r, index, slot)
                }
                else -> Unit
            }
        }
    }

    fun onLanternDelivered(r: Resolution) {
        forEachObjective(r.s) { index, objective ->
            if (objective is Objective.DeliverLanterns) bump(r, index, 0)
        }
    }

    fun onSeahorseFreed(r: Resolution) {
        forEachObjective(r.s) { index, objective ->
            if (objective is Objective.FreeSeahorses) bump(r, index, 0)
        }
    }

    fun onSiltCleared(r: Resolution) {
        forEachObjective(r.s) { index, objective ->
            if (objective is Objective.ClearSilt) bump(r, index, 0)
        }
    }

    fun onGlowSpread(r: Resolution) {
        val lit = (0 until r.s.size).count { r.s.playable[it] && r.s.floorFlags[it] and FloorFlags.GLOW != 0 }
        forEachObjective(r.s) { index, objective ->
            if (objective is Objective.SpreadGlow) {
                r.s.progress[index][0] = lit
                r.emit(GameEvent.ObjectiveProgress(index, 0, lit))
            }
        }
    }

    private inline fun forEachObjective(s: GameState, action: (Int, Objective) -> Unit) {
        s.objectives.forEachIndexed(action)
    }

    private fun bump(r: Resolution, objectiveIndex: Int, slot: Int) {
        val slice = r.s.progress[objectiveIndex]
        if (slot < 0 || slot >= slice.size) return
        slice[slot]++
        r.emit(GameEvent.ObjectiveProgress(objectiveIndex, slot, slice[slot]))
    }

    fun litCells(s: GameState): Int =
        (0 until s.size).count { s.playable[it] && s.floorFlags[it] and FloorFlags.GLOW != 0 }

    fun allComplete(s: GameState): Boolean {
        // A level always carries objectives; a board without any is never complete.
        if (s.objectives.isEmpty()) return false
        s.objectives.forEachIndexed { index, objective ->
            val slice = s.progress[index]
            val done = when (objective) {
                is Objective.CollectColors -> (0 until 6).all { color ->
                    objective.counts[color] <= 0 || slice[color] >= objective.counts[color]
                }
                is Objective.ClearSilt -> slice[0] >= objective.target
                is Objective.DeliverLanterns -> slice[0] >= objective.target
                is Objective.Orders -> objective.orders.withIndex().all { (slot, order) -> slice[slot] >= order.target }
                is Objective.ClearBlockers -> (0 until 6).all { kind ->
                    objective.counts[kind] <= 0 || slice[kind] >= objective.counts[kind]
                }
                is Objective.SpreadGlow -> litCells(s) >= s.playableCount()
                is Objective.FreeSeahorses -> slice[0] >= objective.target
            }
            if (!done) return false
        }
        return true
    }
}
