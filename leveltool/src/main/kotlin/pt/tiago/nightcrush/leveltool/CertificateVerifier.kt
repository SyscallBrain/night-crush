package pt.tiago.nightcrush.leveltool

import pt.tiago.nightcrush.core.ENGINE_VERSION
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.serialization.StateHasher

/**
 * Replays a certificate against the engine and checks every claim it makes.
 *
 * A certificate is only worth something if replaying its moves from the level
 * file reproduces the same states, the same score and a win, on this engine
 * version. The state hash after each move is compared so a divergence is
 * reported at the exact move where the two runs stopped agreeing.
 */
object CertificateVerifier {

    /** null when the certificate replays exactly; a problem description otherwise. */
    fun verify(level: LevelFile, difficulty: Difficulty, certificate: CertificateFile): String? {
        val variant = level.variants[difficulty.name.lowercase()]
            ?: return "level ${level.id} has no ${difficulty.name.lowercase()} variant"
        if (certificate.moves.size != certificate.afterStateHash.size) {
            return "certificate has ${certificate.moves.size} move(s) but " +
                "${certificate.afterStateHash.size} state hash(es)"
        }
        if (certificate.engineVersion != ENGINE_VERSION) {
            return "certificate engine ${certificate.engineVersion} != $ENGINE_VERSION"
        }
        if (certificate.levelId != level.id) return "certificate level ${certificate.levelId} != ${level.id}"
        if (certificate.difficulty != difficulty.name.lowercase()) {
            return "certificate difficulty ${certificate.difficulty} != ${difficulty.name.lowercase()}"
        }
        if (certificate.revision != level.revision) {
            return "certificate revision ${certificate.revision} != level revision ${level.revision}"
        }
        if (certificate.seedHex != variant.seedHex) {
            return "certificate seed ${certificate.seedHex} != variant seed ${variant.seedHex}"
        }
        var state: GameState
        try {
            state = LevelBuilder.start(level, difficulty)
        } catch (error: Exception) {
            return "board cannot be built: ${error.message}"
        }
        for ((index, spec) in certificate.moves.withIndex()) {
            val before = StateHasher.hash(state)
            val result = runCatching { GameEngine.applyMove(state, spec.toMove(), recordEvents = false) }
                .getOrElse { error -> return "move $index could not be applied: ${error.message}" }
            state = result.state
            val expected = certificate.afterStateHash[index]
            val actual = StateHasher.hash(state)
            if (actual != expected) {
                return "state diverged after move $index: expected $expected, got $actual " +
                    "(before move: $before)"
            }
        }
        if (state.outcome !is Outcome.Won) {
            return "replay did not win (outcome ${state.outcome}, ${state.movesUsed} move(s))"
        }
        if (state.score != certificate.finalScore) {
            return "final score ${state.score} != certificate ${certificate.finalScore}"
        }
        if (state.movesUsed != certificate.movesUsed) {
            return "moves used ${state.movesUsed} != certificate ${certificate.movesUsed}"
        }
        if (state.score < certificate.threeStars) {
            return "final score ${state.score} does not reach three stars (${certificate.threeStars})"
        }
        return null
    }
}
