package pt.tiago.nightcrush.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.ui.board.BoardAccessibilityGrid

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rPT")
class BoardAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cellsDescribeThemselvesAndSwapThroughCustomActions() {
        val state = GameState(width = 2, height = 1)
        state.pieces[0] = Pieces.plain(0)
        state.pieces[1] = Pieces.rayAlongRow(1)
        var swapped: Pair<Cell, Cell>? = null
        compose.setContent {
            BoardAccessibilityGrid(state = state, selected = null, hint = null, onCellTap = {}, onSwap = { a, b -> swapped = a to b })
        }
        compose.onNodeWithContentDescription("Linha 1, coluna 2: raia horizontal de Estrela-do-mar").assertExists()
        val first = compose.onNodeWithContentDescription("Linha 1, coluna 1: Bolha")
        val actions = first.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == "Trocar com a da direita" }.action() }
        assertEquals(Cell(0, 0) to Cell(0, 1), swapped)
    }
}
