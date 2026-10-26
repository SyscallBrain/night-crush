package pt.tiago.nightcrush.ui.board

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.min
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.FloorFlags
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Overlays
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.ui.components.pieceNameKey

/** True while TalkBack (or any touch-exploration service) is on; follows changes live. */
@Composable
fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var enabled by remember { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/**
 * An invisible grid over the board for TalkBack (design 10): each cell says
 * its piece, special, blocker and layers, double-tap selects it, and custom
 * actions swap it with any neighbour. Laid out with the same geometry as the
 * canvas so focus rectangles match the cells.
 */
@Composable
fun BoardAccessibilityGrid(
    state: GameState,
    selected: Cell?,
    hint: Pair<Cell, Cell>?,
    onCellTap: (Cell) -> Unit,
    onSwap: (Cell, Cell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val up = stringResource(R.string.a11y_swap_up)
    val down = stringResource(R.string.a11y_swap_down)
    val left = stringResource(R.string.a11y_swap_left)
    val right = stringResource(R.string.a11y_swap_right)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cell = min(maxWidth / state.width, maxHeight / state.height)
        val originX = (maxWidth - cell * state.width) / 2
        val originY = (maxHeight - cell * state.height) / 2
        for (r in 0 until state.height) {
            for (c in 0 until state.width) {
                val i = state.idx(r, c)
                if (!state.playable[i]) continue
                val here = Cell(r, c)
                val description = stringResource(R.string.a11y_cell, r + 1, c + 1, describeCell(state, i, here == selected, hint?.let { here == it.first || here == it.second } == true))
                val actions = buildList {
                    for ((label, dr, dc) in listOf(Triple(up, -1, 0), Triple(down, 1, 0), Triple(left, 0, -1), Triple(right, 0, 1))) {
                        val nr = r + dr
                        val nc = c + dc
                        if (state.inBounds(nr, nc) && state.playable[state.idx(nr, nc)]) {
                            add(CustomAccessibilityAction(label) { onSwap(here, Cell(nr, nc)); true })
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .offset(x = originX + cell * c, y = originY + cell * r)
                        .size(cell)
                        .semantics {
                            contentDescription = description
                            customActions = actions
                            onClick { onCellTap(here); true }
                        },
                )
            }
        }
    }
}

@Composable
private fun describeCell(state: GameState, i: Int, selected: Boolean, hint: Boolean): String {
    val parts = ArrayList<String>()
    val code = state.pieces[i]
    val color = Pieces.colorOf(code)
    val colorName = if (color >= 0) stringResource(pieceNameKey(color)) else ""
    when {
        code == Pieces.EMPTY -> if (state.blockers[i] == Blockers.NONE) parts.add(stringResource(R.string.a11y_empty))
        code == Pieces.LANTERN -> parts.add(stringResource(R.string.a11y_lantern))
        code == Pieces.PEARL -> parts.add(stringResource(R.string.a11y_pearl))
        code in 7..12 -> parts.add(stringResource(R.string.a11y_ray_h, colorName))
        code in 13..18 -> parts.add(stringResource(R.string.a11y_ray_v, colorName))
        Pieces.isJelly(code) -> parts.add(stringResource(R.string.a11y_jelly, colorName))
        Pieces.isLanternFish(code) -> parts.add(stringResource(R.string.a11y_fish, colorName))
        Pieces.isPuffer(code) -> parts.add(stringResource(R.string.a11y_puffer, colorName, state.pieceData[i]))
        else -> parts.add(colorName)
    }
    val overlay = state.overlays[i]
    if (overlay != Overlays.NONE) {
        parts.add(
            if (Overlays.isBarnacle(overlay)) stringResource(R.string.a11y_barnacles, Overlays.barnacleLayers(overlay))
            else stringResource(R.string.a11y_net),
        )
    }
    val blocker = state.blockers[i]
    when {
        blocker in 1..3 -> parts.add(stringResource(R.string.a11y_coral, Blockers.coralLayers(blocker)))
        blocker == Blockers.OIL -> parts.add(stringResource(R.string.a11y_oil))
        blocker == Blockers.SEAHORSE_BUBBLE -> parts.add(stringResource(R.string.a11y_bubble))
        Blockers.isOyster(blocker) -> parts.add(stringResource(R.string.a11y_oyster))
        blocker == Blockers.SINKHOLE -> parts.add(stringResource(R.string.a11y_sinkhole))
        blocker == Blockers.OCTOPUS -> parts.add(stringResource(R.string.a11y_octopus))
    }
    if (state.silt[i] > 0) parts.add(stringResource(R.string.a11y_silt, state.silt[i]))
    if (state.floorFlags[i] and FloorFlags.EXIT != 0) parts.add(stringResource(R.string.a11y_exit))
    if (state.floorFlags[i] and FloorFlags.GLOW != 0) parts.add(stringResource(R.string.a11y_glow))
    if (selected) parts.add(stringResource(R.string.a11y_selected))
    if (hint) parts.add(stringResource(R.string.a11y_hint))
    return parts.joinToString(", ")
}
