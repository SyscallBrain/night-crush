package pt.tiago.nightcrush.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.core.model.FloorFlags
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Current
import pt.tiago.nightcrush.ui.theme.Danger
import pt.tiago.nightcrush.ui.theme.Deep
import pt.tiago.nightcrush.ui.theme.Eyebrow
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.OnGlow
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = Reef,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Deep)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun EyebrowLabel(text: String, modifier: Modifier = Modifier, color: Color = Mist) {
    Text(text = text.uppercase(), style = Eyebrow, color = color, modifier = modifier)
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(Glow, Color(0xFF2BD7B0)))
                } else {
                    Brush.horizontalGradient(listOf(Reef, Reef))
                },
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) OnGlow else Mist,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Foam,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Deep)
            .border(1.dp, Reef, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = textColor, fontSize = 14.sp)
    }
}

@Composable
fun StarRow(stars: Int, size: Int = 18) {
    Row(horizontalArrangement = Arrangement.spacedBy((size / 8).dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..3) {
            NightIcon(NightIcons.Star, tint = if (i <= stars) Plankton else Reef, size = size.dp)
        }
    }
}

/** Five hearts, full or empty. Drawn as vectors so the system never swaps in a red emoji. */
@Composable
fun HeartsRow(lives: Int, max: Int, size: Int = 22) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..max) {
            NightIcon(NightIcons.Heart, tint = if (i <= lives) Danger else Reef, size = size.dp)
        }
    }
}

/**
 * The score bar of a level: the track runs up to the three-star score and a star
 * marker sits at each threshold, lit once it is reached.
 */
@Composable
fun ScoreStarsBar(score: Int, two: Int, three: Int, modifier: Modifier = Modifier) {
    val top = three.coerceAtLeast(1).toFloat()
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(18.dp)) {
        val track = maxWidth - 16.dp
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 16.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Reef),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((score / top).coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(Current, Glow))),
            )
        }
        for (threshold in listOf(two, three)) {
            val x = track * (threshold / top).coerceIn(0f, 1f)
            NightIcon(
                NightIcons.Star,
                tint = if (score >= threshold) Plankton else Mist.copy(alpha = 0.6f),
                size = 18.dp,
                modifier = Modifier.offset(x = x - 1.dp),
            )
        }
    }
}

@Composable
fun ProgressBar(value: Float, modifier: Modifier = Modifier, color: Color = Glow) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Reef),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

@Composable
fun DifficultyChips(
    selected: pt.tiago.nightcrush.core.model.Difficulty,
    labelOf: @Composable (pt.tiago.nightcrush.core.model.Difficulty) -> String,
    onSelect: (pt.tiago.nightcrush.core.model.Difficulty) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Deep)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (difficulty in pt.tiago.nightcrush.core.model.Difficulty.entries) {
            val active = difficulty == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Glow else Color.Transparent)
                    .clickable { onSelect(difficulty) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(difficulty),
                    color = if (active) OnGlow else Mist,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** One objective line with its live counter. */
@Composable
fun ObjectiveRow(
    state: GameState,
    index: Int,
    label: String,
    value: Int,
    target: Int,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text = label, color = Foam, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(text = "$value/$target", color = if (value >= target) Glow else Mist, fontSize = 14.sp)
    }
    Spacer(Modifier.height(2.dp))
    ProgressBar(value = if (target <= 0) 1f else value.toFloat() / target, color = if (value >= target) Glow else pt.tiago.nightcrush.ui.theme.Current)
}

fun objectiveProgress(state: GameState, index: Int): Pair<Int, Int> {
    val objective = state.objectives[index]
    val slice = state.progress[index]
    return when (objective) {
        is Objective.CollectColors -> {
            val color = (0 until 6).firstOrNull { objective.counts[it] > 0 } ?: 0
            slice[color] to objective.counts[color]
        }
        is Objective.ClearSilt -> slice[0] to objective.target
        is Objective.DeliverLanterns -> slice[0] to objective.target
        is Objective.Orders -> {
            val slot = objective.orders.indices.firstOrNull { slice[it] < objective.orders[it].target }
                ?: (objective.orders.size - 1).coerceAtLeast(0)
            if (objective.orders.isEmpty()) 0 to 0
            else slice[slot] to objective.orders[slot].target
        }
        is Objective.ClearBlockers -> {
            val kind = (0 until 6).firstOrNull { objective.counts[it] > 0 } ?: 0
            slice[kind] to objective.counts[kind]
        }
        is Objective.SpreadGlow -> {
            val lit = (0 until state.size).count {
                state.playable[it] && state.floorFlags[it] and FloorFlags.GLOW != 0
            }
            lit to state.playableCount()
        }
        is Objective.FreeSeahorses -> slice[0] to objective.target
    }
}

fun pieceNameKey(color: Int): Int = when (color) {
    0 -> pt.tiago.nightcrush.R.string.piece_blue
    1 -> pt.tiago.nightcrush.R.string.piece_yellow
    2 -> pt.tiago.nightcrush.R.string.piece_purple
    3 -> pt.tiago.nightcrush.R.string.piece_teal
    4 -> pt.tiago.nightcrush.R.string.piece_green
    else -> pt.tiago.nightcrush.R.string.piece_coral
}

fun objectiveLabelKey(objective: Objective): Int = when (objective) {
    is Objective.CollectColors -> pt.tiago.nightcrush.R.string.objective_collect
    is Objective.ClearSilt -> pt.tiago.nightcrush.R.string.objective_silt
    is Objective.DeliverLanterns -> pt.tiago.nightcrush.R.string.objective_lanterns
    is Objective.Orders -> pt.tiago.nightcrush.R.string.objective_orders
    is Objective.ClearBlockers -> pt.tiago.nightcrush.R.string.objective_blockers
    is Objective.SpreadGlow -> pt.tiago.nightcrush.R.string.objective_glow
    is Objective.FreeSeahorses -> pt.tiago.nightcrush.R.string.objective_seahorses
}

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Picks the Portuguese or English content string by the device language. */
@Composable
fun localized(pt: String, en: String): String {
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    return if (language == "pt") pt.ifBlank { en } else en.ifBlank { pt }
}

@Composable
fun difficultyLabel(difficulty: pt.tiago.nightcrush.core.model.Difficulty): String = androidx.compose.ui.res.stringResource(
    when (difficulty) {
        pt.tiago.nightcrush.core.model.Difficulty.EASY -> pt.tiago.nightcrush.R.string.difficulty_easy
        pt.tiago.nightcrush.core.model.Difficulty.MEDIUM -> pt.tiago.nightcrush.R.string.difficulty_medium
        pt.tiago.nightcrush.core.model.Difficulty.HARD -> pt.tiago.nightcrush.R.string.difficulty_hard
    },
)

/** The piece name for a colour id as written in level files (BLUE, YELLOW, ...). */
@Composable
fun pieceName(colorName: String): String {
    val index = pt.tiago.nightcrush.core.model.PieceColor.entries.indexOfFirst { it.name == colorName }
    return androidx.compose.ui.res.stringResource(pieceNameKey(index.coerceAtLeast(0)))
}

/** One line per goal for the level intro, e.g. "Collect 12 Starfish". */
@Composable
fun describeObjective(objective: pt.tiago.nightcrush.core.model.ObjectiveSpec): List<String> {
    return when (objective.type) {
        "collect" -> objective.colors.entries.map { (color, count) ->
            androidx.compose.ui.res.stringResource(pt.tiago.nightcrush.R.string.goal_collect, count, pieceName(color))
        }
        "silt" -> listOf(androidx.compose.ui.res.stringResource(pt.tiago.nightcrush.R.string.goal_silt, objective.count))
        "deliverLanterns" -> listOf(androidx.compose.ui.res.stringResource(pt.tiago.nightcrush.R.string.goal_lanterns, objective.count))
        "orders" -> objective.orders.map { order -> describeOrderSpec(order) }
        "clearBlockers" -> listOf(
            androidx.compose.ui.res.stringResource(pt.tiago.nightcrush.R.string.goal_blockers, objective.blockers.values.sum()),
        )
        "spreadGlow" -> listOf(androidx.compose.ui.res.stringResource(pt.tiago.nightcrush.R.string.goal_glow))
        "freeSeahorses" -> listOf(androidx.compose.ui.res.stringResource(pt.tiago.nightcrush.R.string.goal_seahorses, objective.count))
        else -> emptyList()
    }
}

/** "Make 3 × Ray", "Break 12 × Coral", "Collect 20 × Bubble". */
@Composable
fun describeOrderSpec(order: pt.tiago.nightcrush.core.model.OrderSpec): String {
    return when (order.kind) {
        "makeSpecial" -> androidx.compose.ui.res.stringResource(
            pt.tiago.nightcrush.R.string.goal_order_special, order.count, specialName(order.value.orEmpty()),
        )
        "makeCombo" -> androidx.compose.ui.res.stringResource(pt.tiago.nightcrush.R.string.goal_order_combo, order.count)
        "breakLayers" -> androidx.compose.ui.res.stringResource(
            pt.tiago.nightcrush.R.string.goal_order_break, order.count, blockerName(order.value.orEmpty()),
        )
        else -> androidx.compose.ui.res.stringResource(
            pt.tiago.nightcrush.R.string.goal_collect, order.count, pieceName(order.colorName.orEmpty()),
        )
    }
}

/** Short label of a live order, for the goal rows in the game. */
@Composable
fun orderLabel(order: pt.tiago.nightcrush.core.model.Order): String = when (order) {
    is pt.tiago.nightcrush.core.model.Order.MakeSpecial -> specialName(order.kind.name)
    is pt.tiago.nightcrush.core.model.Order.MakeCombo -> androidx.compose.ui.res.stringResource(pt.tiago.nightcrush.R.string.special_combo)
    is pt.tiago.nightcrush.core.model.Order.Collect -> androidx.compose.ui.res.stringResource(pieceNameKey(order.color))
    is pt.tiago.nightcrush.core.model.Order.BreakLayers -> blockerName(order.kind.name)
}

@Composable
fun specialName(id: String): String = androidx.compose.ui.res.stringResource(
    when (id.uppercase()) {
        "RAY" -> pt.tiago.nightcrush.R.string.special_ray
        "JELLY" -> pt.tiago.nightcrush.R.string.special_jelly
        "LANTERN_FISH" -> pt.tiago.nightcrush.R.string.special_fish
        else -> pt.tiago.nightcrush.R.string.special_pearl
    },
)

@Composable
fun blockerName(id: String): String = androidx.compose.ui.res.stringResource(
    when (id.uppercase()) {
        "OIL" -> pt.tiago.nightcrush.R.string.blocker_oil
        "SEAHORSE_BUBBLE" -> pt.tiago.nightcrush.R.string.blocker_bubble
        "GIANT_OYSTER" -> pt.tiago.nightcrush.R.string.blocker_oyster
        "SINKHOLE" -> pt.tiago.nightcrush.R.string.blocker_sinkhole
        "OCTOPUS" -> pt.tiago.nightcrush.R.string.blocker_octopus
        else -> pt.tiago.nightcrush.R.string.blocker_coral
    },
)
