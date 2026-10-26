package pt.tiago.nightcrush.core.serialization

import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Order
import pt.tiago.nightcrush.core.model.Outcome
import java.security.MessageDigest

/**
 * Versioned canonical encoding of the logical state, used for certificates
 * and deterministic replay. Includes objective progress and both RNG states:
 * two states with the same hash behave identically for every future move.
 */
object StateHasher {
    const val HASH_VERSION = 1

    fun hash(state: GameState): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(canonical(state).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun canonical(state: GameState): String = buildString {
        append("v=").append(HASH_VERSION).append('\n')
        append("engine=").append(state.level.engineVersion).append('\n')
        append("level=").append(state.level.id).append('|').append(state.level.worldId).append('|')
            .append(state.level.ordinal).append('|').append(state.level.revision).append('|')
            .append(state.level.difficulty.name).append('|').append(state.level.seedHex.lowercase()).append('\n')
        append("size=").append(state.width).append(',').append(state.height).append('\n')
        append("playable=").append(bits(state.playable)).append('\n')
        append("cells:\n")
        for (i in 0 until state.size) {
            append(state.pieces[i]).append(',')
            append(state.pieceData[i]).append(',')
            append(state.overlays[i]).append(',')
            append(state.blockers[i]).append(',')
            append(state.blockerData[i]).append(',')
            append(state.blockerAnchor[i]).append(',')
            append(state.blockerTimer[i]).append(',')
            append(state.silt[i]).append(',')
            append(state.floorFlags[i]).append(',')
            append(state.portalTarget[i]).append(',')
            append(state.conveyorNext[i]).append(',')
            append(state.landStamp[i]).append('\n')
        }
        append("colors=").append(state.colors.joinToString(",")).append('\n')
        append("spawner=").append(bits(state.spawner)).append('\n')
        append("lanternSpawner=").append(bits(state.lanternSpawner)).append('\n')
        append("limit=").append(state.limitType.name).append('|').append(state.limitValue).append('|')
            .append(state.movesLeft).append('|').append(state.movesUsed).append('|').append(state.timeLeftSec).append('\n')
        append("objectives=").append(state.objectives.size).append('\n')
        state.objectives.forEachIndexed { index, objective ->
            append(objectiveCanonical(objective)).append('\n')
            append("progress=").append(state.progress[index].joinToString(",")).append('\n')
        }
        append("lanterns=").append(state.lanternTotal).append('|').append(state.lanternMaxOnBoard).append('|')
            .append(state.lanternEvery).append('|').append(state.lanternsSpawned).append('|')
            .append(state.lanternsOnBoard).append('|').append(state.lanternsDelivered).append('|')
            .append(bits(state.lanternSpawnedThisMove)).append('\n')
        append("rng=").append(state.rngColumns.joinToString(",") { java.lang.Long.toUnsignedString(it, 16) }).append('\n')
        append("aux=").append(java.lang.Long.toUnsignedString(state.rngAux, 16)).append('\n')
        append("score=").append(state.score).append('\n')
        append("outcome=").append(
            when (val outcome = state.outcome) {
                is Outcome.Playing -> "playing"
                is Outcome.Won -> "won"
                is Outcome.Lost -> "lost:${outcome.reasonKey()}"
            },
        ).append('\n')
        append("maxCascade=").append(state.maxCascade).append('\n')
        append("specials=").append(state.specialsCreated.joinToString(",")).append('\n')
        append("combos=").append(state.combosMade.joinToString(",")).append('\n')
        append("seahorses=").append(state.seahorsesFreed).append('\n')
        append("land=").append(state.landCounter).append('\n')
    }

    private fun objectiveCanonical(objective: Objective): String = when (objective) {
        is Objective.CollectColors -> "collect|" + objective.counts.joinToString(",")
        is Objective.ClearSilt -> "silt|" + objective.target
        is Objective.DeliverLanterns -> "lanterns|" + objective.target
        is Objective.Orders -> "orders|" + objective.orders.joinToString(",") { order ->
            when (order) {
                is Order.MakeSpecial -> "ms:${order.kind.name}:${order.target}"
                is Order.MakeCombo -> "mc:${order.combo.name}:${order.target}"
                is Order.Collect -> "c:${order.color}:${order.target}"
                is Order.BreakLayers -> "b:${order.kind.name}:${order.target}"
            }
        }
        is Objective.ClearBlockers -> "blockers|" + objective.counts.joinToString(",")
        is Objective.SpreadGlow -> "glow|"
        is Objective.FreeSeahorses -> "seahorses|" + objective.target
    }

    private fun bits(values: BooleanArray): String = buildString {
        for (value in values) append(if (value) '1' else '0')
    }

    private fun pt.tiago.nightcrush.core.model.Outcome.Lost.reasonKey(): String = when (this) {
        Outcome.Lost.OutOfMoves -> "moves"
        Outcome.Lost.OutOfTime -> "time"
        Outcome.Lost.PufferExploded -> "puffer"
    }
}
