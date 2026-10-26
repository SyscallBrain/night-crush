package pt.tiago.nightcrush.core.rules

import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.FloorFlags
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.random.SplitMix64

/** Deterministic shuffle of the movable matchable pieces until a move exists. */
internal object Shuffle {

    fun run(r: Resolution, force: Boolean = false): Boolean {
        val s = r.s
        val cells = (0 until s.size).filter { i ->
            s.playable[i] && Pieces.isMatchable(s.pieces[i]) &&
                s.overlays[i] == Overlays.NONE && s.blockers[i] == Blockers.NONE
        }
        if (cells.size < 2) return false
        val codes = IntArray(cells.size) { s.pieces[cells[it]] }
        val stream = SplitMix64(s.rngAux)
        for (attempt in 0 until 200) {
            if (attempt < 100) {
                for (k in codes.size - 1 downTo 1) {
                    val j = stream.nextInt(k + 1)
                    val tmp = codes[k]
                    codes[k] = codes[j]
                    codes[j] = tmp
                }
                for (k in cells.indices) s.pieces[cells[k]] = codes[k]
            } else {
                // Last resort: fresh colours for the same cells, still from the aux stream.
                for (cell in cells) {
                    val color = s.colors[stream.nextInt(s.colors.size)]
                    val code = s.pieces[cell]
                    s.pieces[cell] = when {
                        code in 7..12 -> Pieces.rayAlongRow(color)
                        code in 13..18 -> Pieces.rayAlongColumn(color)
                        Pieces.isJelly(code) -> Pieces.jelly(color)
                        Pieces.isLanternFish(code) -> Pieces.lanternFish(color)
                        Pieces.isPuffer(code) -> Pieces.puffer(color)
                        else -> Pieces.plain(color)
                    }
                }
            }
            if (MatchFinder.find(s).isEmpty() && GameEngine.hasValidMove(s)) break
        }
        s.rngAux = stream.state
        r.emit(GameEvent.Shuffled)
        r.snapshot()
        return true
    }
}
