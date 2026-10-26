package pt.tiago.nightcrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import pt.tiago.nightcrush.ui.art.PlanktonBackground
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.RoundIconButton
import pt.tiago.nightcrush.ui.components.ScreenScaffold
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.BuildConfig
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.PreLevelBooster
import pt.tiago.nightcrush.game.ChestRules
import pt.tiago.nightcrush.game.DailyReward
import pt.tiago.nightcrush.data.Profile
import pt.tiago.nightcrush.game.GameMode
import pt.tiago.nightcrush.game.LifeRules
import pt.tiago.nightcrush.game.Missions
import pt.tiago.nightcrush.game.ProfileReducer
import pt.tiago.nightcrush.game.StickerBook
import pt.tiago.nightcrush.game.UiScreen
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.ui.components.DifficultyChips
import pt.tiago.nightcrush.ui.components.EyebrowLabel
import pt.tiago.nightcrush.ui.components.HeartsRow
import pt.tiago.nightcrush.ui.components.formatDuration
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.PrimaryButton
import pt.tiago.nightcrush.ui.components.ProgressBar
import pt.tiago.nightcrush.ui.components.SecondaryButton
import pt.tiago.nightcrush.ui.components.StarRow
import pt.tiago.nightcrush.ui.components.describeObjective
import pt.tiago.nightcrush.ui.components.difficultyLabel
import pt.tiago.nightcrush.ui.components.localized
import pt.tiago.nightcrush.ui.screens.AchievementsScreen
import pt.tiago.nightcrush.ui.screens.CollectionScreen
import pt.tiago.nightcrush.ui.screens.EventScreen
import pt.tiago.nightcrush.ui.screens.ChestDialog
import pt.tiago.nightcrush.ui.screens.GameScreen
import pt.tiago.nightcrush.ui.screens.HomeScreen
import pt.tiago.nightcrush.ui.screens.ProfileScreen
import pt.tiago.nightcrush.ui.screens.PuzzlesScreen
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Current
import pt.tiago.nightcrush.ui.theme.Danger
import pt.tiago.nightcrush.ui.theme.Deep
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.OnGlow
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef
import pt.tiago.nightcrush.ui.theme.Syne

/**
 * The whole app navigation: one state object in, one screen out. Screens are
 * dumb; every decision happens in the view model.
 */
@Composable
fun NightCrushApp(ui: UiState, callbacks: UiCallbacks) {
    val settings = ui.profile.settings
    val density = LocalDensity.current
    // "Larger text" scales every sp in the app on top of the system font size.
    val scaled = remember(density, settings.largerText) {
        if (settings.largerText) Density(density.density, density.fontScale * 1.15f) else density
    }
    CompositionLocalProvider(LocalDensity provides scaled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Abyss, Deep))),
        ) {
            PlanktonBackground(reducedMotion = settings.reducedMotion, modifier = Modifier.fillMaxSize())
            // The app draws edge to edge; content stays clear of the status and gesture bars.
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (ui.screen) {
                    UiScreen.HOME -> HomeScreen(ui, callbacks)
                    UiScreen.MAP -> MapScreen(ui, callbacks)
                    UiScreen.INTRO -> IntroScreen(ui, callbacks)
                    UiScreen.GAME -> GameScreen(ui, callbacks)
                    UiScreen.RESULT -> ResultScreen(ui, callbacks)
                    UiScreen.SETTINGS -> SettingsScreen(ui, callbacks)
                    UiScreen.PROFILE -> ProfileScreen(ui, callbacks)
                    UiScreen.COLLECTION -> CollectionScreen(ui, callbacks)
                    UiScreen.FREE_PLAY -> FreePlayScreen(ui, callbacks)
                    UiScreen.DAILY -> DailyScreen(ui, callbacks)
                    UiScreen.ABOUT -> AboutScreen(ui, callbacks)
                    UiScreen.MISSIONS -> MissionsScreen(ui, callbacks)
                    UiScreen.EVENT -> EventScreen(ui, callbacks)
                    UiScreen.ACHIEVEMENTS -> AchievementsScreen(ui, callbacks)
                    UiScreen.PUZZLES -> PuzzlesScreen(ui, callbacks)
                }
                ui.message?.let { message ->
                    MessageBox(message) { callbacks.onDismissMessage() }
                }
            }
            if (ui.noLives) {
                NoLivesDialog(ui, callbacks)
            }
            ui.chest?.let { chest ->
                ChestDialog(chest, ui.worlds, reducedMotion = settings.reducedMotion) { callbacks.onDismissChest() }
            }
            if (ui.chest == null && ui.screen != UiScreen.GAME) {
                ui.dailyStep?.let { day ->
                    val step = DailyReward.steps[day - 1]
                    RewardDialog(
                        title = stringResource(R.string.daily_reward_title, day),
                        body = dailyRewardText(step),
                    ) { callbacks.onClaimDailyReward() }
                }
            }
        }
    }
}

@Composable
private fun NoLivesDialog(ui: UiState, callbacks: UiCallbacks) {
    val wait = formatDuration(LifeRules.nextLifeInMillis(ui.profile, ui.nowEpochMs))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Abyss.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { callbacks.onDismissNoLives() },
        contentAlignment = Alignment.Center,
    ) {
        Panel(modifier = Modifier.padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HeartsRow(lives = 0, max = Profile.MAX_LIVES, size = 26)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.nolives_title), color = Foam, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Syne)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.nolives_body, wait), color = Mist, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.nolives_daily)) {
                    callbacks.onDismissNoLives()
                    callbacks.onOpenDaily()
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.nolives_free), modifier = Modifier.fillMaxWidth()) {
                    callbacks.onDismissNoLives()
                    callbacks.onOpenFreePlay()
                }
            }
        }
    }
}

/** A modal yes/no question; [danger] paints the confirm button red. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, danger: Boolean = false, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Abyss.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Panel(modifier = Modifier.padding(24.dp), border = if (danger) Danger else Reef) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Foam, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Syne, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(body, color = Mist, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                if (danger) {
                    SecondaryButton(confirm, modifier = Modifier.fillMaxWidth(), textColor = Danger, onClick = onConfirm)
                } else {
                    PrimaryButton(confirm, onClick = onConfirm)
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.cancel), modifier = Modifier.fillMaxWidth(), onClick = onCancel)
            }
        }
    }
}

@Composable
private fun dailyRewardText(step: DailyReward.Step): String {
    val parts = ArrayList<String>()
    if (step.pearls > 0) parts.add(stringResource(R.string.reward_pearls, step.pearls))
    when (step.booster) {
        "harpoon" -> parts.add(stringResource(R.string.booster_harpoon))
        "freeSwap" -> parts.add(stringResource(R.string.booster_free_swap) + " ×2")
        "shuffle" -> parts.add(stringResource(R.string.booster_shuffle))
    }
    if (step.infiniteMinutes > 0) parts.add(stringResource(R.string.reward_infinite, step.infiniteMinutes))
    if (step.stickerRare) {
        parts.add(stringResource(R.string.intro_booster_pearl))
        parts.add(stringResource(R.string.reward_rare_sticker))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun RewardDialog(title: String, body: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Abyss.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Panel(modifier = Modifier.padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Plankton, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Syne)
                Spacer(Modifier.height(8.dp))
                Text(body, color = Foam, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.dialog_ok), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun MessageBox(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Reef)
                .clickable { onDismiss() }
                .padding(16.dp),
        ) {
            Text(message, color = Foam, fontSize = 14.sp)
        }
    }
}

// ------------------------------------------------------------------------ map

@Composable
private fun MapScreen(ui: UiState, callbacks: UiCallbacks) {
    val world = ui.worlds.getOrNull(ui.worldIndex)
    val entries = remember(ui.levels) { ui.levels.associateBy { it.ordinal } }
    val next = ui.nextOrdinal()
    ScreenScaffold(
        title = world?.let { localized(it.namePt, it.nameEn) } ?: stringResource(R.string.nav_map),
        eyebrow = stringResource(R.string.map_world_of, ui.worldIndex + 1, ui.worlds.size.coerceAtLeast(1)),
        onBack = { callbacks.onBack() },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DifficultyChips(
                selected = ui.difficulty,
                labelOf = { difficultyLabel(it) },
                onSelect = { callbacks.onDifficultyChanged(it) },
            )
            Spacer(Modifier.height(10.dp))
            if (world == null) {
                Text(stringResource(R.string.msg_not_ready), color = Mist)
                return@Column
            }
            val ordinals = (world.from..world.to).toList()
            val worldStars = ordinals.sumOf { ui.profile.levels[ProfileReducer.key(it, ui.difficulty)]?.stars ?: 0 }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RoundIconButton(
                    NightIcons.Back,
                    contentDescription = stringResource(R.string.map_prev_world),
                    tint = if (ui.worldIndex > 0) Foam else Reef,
                ) { if (ui.worldIndex > 0) callbacks.onWorldChanged(ui.worldIndex - 1) }
                Text(
                    stringResource(R.string.stars_of, worldStars, ordinals.count { it in ui.available } * 3),
                    color = Plankton,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(
                    NightIcons.Forward,
                    contentDescription = stringResource(R.string.map_next_world),
                    tint = if (ui.worldIndex < ui.worlds.size - 1) Foam else Reef,
                ) { if (ui.worldIndex < ui.worlds.size - 1) callbacks.onWorldChanged(ui.worldIndex + 1) }
            }
            var onlyMissing by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (onlyMissing) Plankton.copy(alpha = 0.18f) else Deep)
                    .border(1.dp, if (onlyMissing) Plankton else Reef, RoundedCornerShape(20.dp))
                    .clickable { onlyMissing = !onlyMissing }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NightIcon(NightIcons.Star, tint = if (onlyMissing) Plankton else Mist, size = 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.map_filter_missing), color = if (onlyMissing) Foam else Mist, fontSize = 12.sp)
            }
            // The path climbs the screen: level 1 of the world sits at the bottom.
            val listState = rememberLazyListState()
            LaunchedEffect(world.from, next) {
                val index = (next - world.from).coerceIn(0, ordinals.size - 1)
                listState.scrollToItem((index - 2).coerceAtLeast(0))
            }
            // Swiping sideways changes world, like turning a page of the coast.
            var drag by remember { mutableFloatStateOf(0f) }
            val worldIndex = ui.worldIndex
            val worldCount = ui.worlds.size
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize().pointerInput(worldIndex, worldCount) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            val threshold = 72.dp.toPx()
                            if (drag < -threshold && worldIndex < worldCount - 1) callbacks.onWorldChanged(worldIndex + 1)
                            if (drag > threshold && worldIndex > 0) callbacks.onWorldChanged(worldIndex - 1)
                        },
                        onHorizontalDrag = { _, amount -> drag += amount },
                    )
                },
            ) {
                items(ordinals.size, key = { ordinals[it] }) { index ->
                    val ordinal = ordinals[index]
                    val entry = entries[ordinal]
                    val progress = ui.profile.levels[ProfileReducer.key(ordinal, ui.difficulty)]
                    PathNode(
                        index = index,
                        last = index == ordinals.size - 1,
                        ordinal = ordinal,
                        title = entry?.let { localized(it.titlePt, it.titleEn) }.orEmpty(),
                        stars = progress?.stars ?: 0,
                        missing = entry == null,
                        locked = !ui.isUnlocked(ordinal),
                        current = ordinal == next,
                        fullMoon = entry?.isFullMoon == true,
                        reducedMotion = ui.profile.settings.reducedMotion,
                        dimmed = onlyMissing && (progress?.stars ?: 0) >= 3,
                    ) { callbacks.onSelectLevel(ordinal) }
                }
                // Declared last so it sits at the top of the reversed path: the world chest (design 8.4), earned by winning all 50.
                item(key = "chest") {
                    val claimed = ChestRules.worldKey(ui.worldIndex, ui.difficulty) in ui.profile.worldChestsClaimed
                    val won = ordinals.count { ui.isWon(it) }
                    WorldChestNode(claimed = claimed, won = won, total = ordinals.size)
                }
            }
        }
    }
}

@Composable
private fun WorldChestNode(claimed: Boolean, won: Int, total: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (claimed) Plankton.copy(alpha = 0.25f) else Deep)
                .border(2.dp, if (claimed) Plankton else Reef, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            NightIcon(if (claimed) NightIcons.Check else NightIcons.Chest, tint = if (claimed) Plankton else Mist, size = 30.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.map_world_chest, won, total),
            color = if (claimed) Plankton else Mist,
            fontSize = 12.sp,
        )
    }
}

/** Horizontal position of a node on the winding path, as a fraction of the width. */
private fun pathX(index: Int): Float = 0.5f + 0.28f * kotlin.math.sin(index * 0.95f)

@Composable
private fun PathNode(
    index: Int,
    last: Boolean,
    ordinal: Int,
    title: String,
    stars: Int,
    missing: Boolean,
    locked: Boolean,
    current: Boolean,
    fullMoon: Boolean,
    reducedMotion: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    val pulse = if (current && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "node")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "pulse",
        ).value
    } else {
        0f
    }
    val nodeSize = 58.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .drawBehind {
                if (last) return@drawBehind
                // Dashed curve up to the next node (one row above).
                val from = Offset(size.width * pathX(index), size.height * 0.42f)
                val to = Offset(size.width * pathX(index + 1), size.height * 0.42f - size.height)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(from.x, from.y)
                    val midY = (from.y + to.y) / 2f
                    cubicTo(from.x, midY, to.x, midY, to.x, to.y)
                }
                drawPath(
                    path,
                    color = (if (locked || missing) Reef else Glow).copy(alpha = 0.55f),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 9.dp.toPx())),
                    ),
                )
            },
    ) {
        val x = maxWidth * pathX(index)
        val onLeft = pathX(index) > 0.5f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(x = x - 40.dp).width(80.dp).graphicsLayer { alpha = if (dimmed) 0.3f else 1f },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(nodeSize + 16.dp)) {
                if (fullMoon) {
                    // Full Moon levels wear a dashed gold ring (design 7.2).
                    Canvas(modifier = Modifier.size(nodeSize + 10.dp)) {
                        drawCircle(
                            Plankton,
                            radius = size.minDimension / 2f - 2.dp.toPx(),
                            style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
                        )
                    }
                }
                if (current) {
                    Box(
                        modifier = Modifier
                            .size(nodeSize + 16.dp * pulse)
                            .clip(CircleShape)
                            .background(Current.copy(alpha = 0.35f * (1f - pulse))),
                    )
                }
                val fill = when {
                    missing || locked -> Deep
                    current -> Current
                    stars > 0 -> Glow
                    else -> Current
                }
                Box(
                    modifier = Modifier
                        .size(nodeSize)
                        .clip(CircleShape)
                        .background(fill)
                        .border(
                            width = 1.dp,
                            color = if (locked || missing) Reef else Foam.copy(alpha = 0.4f),
                            shape = CircleShape,
                        )
                        .clickable(enabled = !locked && !missing) { onClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (locked || missing) {
                        NightIcon(NightIcons.Lock, tint = Mist, size = 20.dp)
                    } else {
                        Text("$ordinal", color = OnGlow, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Syne)
                    }
                }
            }
            if (!locked && !missing) StarRow(stars, size = 14)
        }
        Text(
            text = if (missing) stringResource(R.string.map_coming_soon) else title,
            color = if (current) Foam else Mist,
            fontSize = 12.sp,
            maxLines = 2,
            textAlign = if (onLeft) TextAlign.End else TextAlign.Start,
            modifier = Modifier
                .width(maxWidth * 0.34f)
                .align(if (onLeft) Alignment.TopStart else Alignment.TopEnd)
                .padding(top = 22.dp),
        )
    }
}

// ---------------------------------------------------------------------- intro

@Composable
private fun IntroScreen(ui: UiState, callbacks: UiCallbacks) {
    val level = ui.level ?: return
    val variant = level.variants[ui.difficulty.name.lowercase()] ?: return
    val title = localized(level.titlePt, level.titleEn).ifBlank { stringResource(R.string.game_level, level.ordinal) }
    ScreenScaffold(
        title = title,
        eyebrow = stringResource(R.string.game_level, level.ordinal) + " · " + difficultyLabel(ui.difficulty),
        onBack = { callbacks.onBack() },
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    EyebrowLabel(stringResource(R.string.intro_goals))
                    Spacer(Modifier.height(10.dp))
                    for (objective in variant.objectives) {
                        if (objective.type == "orders") {
                            Text(stringResource(R.string.goal_orders_title), color = Mist, fontSize = 13.sp)
                        }
                        for (line in describeObjective(objective)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                NightIcon(NightIcons.Target, tint = Glow, size = 18.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(line, color = Foam, fontSize = 16.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        IntroStat(
                            value = "${variant.limit.value}",
                            label = stringResource(if (variant.limit.type == "time") R.string.game_time_label else R.string.game_moves_label),
                            modifier = Modifier.weight(1f),
                        )
                        Column(
                            modifier = Modifier
                                .weight(2f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Abyss.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(R.string.intro_star_targets), color = Mist, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StarTarget(count = 2, score = variant.stars.two)
                                Spacer(Modifier.width(14.dp))
                                StarTarget(count = 3, score = variant.stars.three)
                            }
                        }
                    }
                    if (ui.mode == GameMode.CAMPAIGN) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NightIcon(NightIcons.Heart, tint = Danger, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.intro_cost_life), color = Mist, fontSize = 13.sp)
                        }
                        val streak = ui.profile.streak
                        if (streak >= 3) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NightIcon(NightIcons.Sparkle, tint = Current, size = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.home_streak_next, streak, streakBonusText(streak)), color = Current, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    EyebrowLabel(stringResource(R.string.intro_boosters))
                    Spacer(Modifier.height(8.dp))
                    val inventory = ui.profile.boosters
                    BoosterOption(stringResource(R.string.intro_booster_ray), inventory.rayStart, PreLevelBooster.RAY in ui.pendingBoosters) {
                        callbacks.onToggleBooster(PreLevelBooster.RAY)
                    }
                    BoosterOption(stringResource(R.string.intro_booster_jelly), inventory.jellyStart, PreLevelBooster.JELLY in ui.pendingBoosters) {
                        callbacks.onToggleBooster(PreLevelBooster.JELLY)
                    }
                    BoosterOption(stringResource(R.string.intro_booster_pearl), inventory.pearlStart, PreLevelBooster.ABYSS_PEARL in ui.pendingBoosters) {
                        callbacks.onToggleBooster(PreLevelBooster.ABYSS_PEARL)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.intro_play), modifier = Modifier.fillMaxWidth()) { callbacks.onStartLevel() }
        }
    }
}

@Composable
private fun streakBonusText(streak: Int): String = stringResource(
    when {
        streak >= 7 -> R.string.streak_bonus_7
        streak >= 5 -> R.string.streak_bonus_5
        else -> R.string.streak_bonus_3
    },
)

@Composable
private fun StarTarget(count: Int, score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { NightIcon(NightIcons.Star, tint = Plankton, size = 14.dp) }
        Spacer(Modifier.width(4.dp))
        Text("$score", color = Foam, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IntroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Abyss.copy(alpha = 0.5f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Foam, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Syne)
        Text(label, color = Mist, fontSize = 12.sp)
    }
}

@Composable
private fun BoosterOption(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Glow.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (active) Glow else Reef.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(enabled = count > 0) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckDot(active)
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (count > 0) Foam else Mist, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("×$count", color = if (count > 0) Plankton else Mist, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Round selection mark shared by booster and list choices. */
@Composable
private fun CheckDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (active) Glow else Color.Transparent)
            .border(2.dp, if (active) Glow else Reef, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (active) NightIcon(NightIcons.Check, tint = OnGlow, size = 14.dp)
    }
}

// --------------------------------------------------------------------- result

@Composable
private fun ResultScreen(ui: UiState, callbacks: UiCallbacks) {
    val result = ui.result ?: return
    val timeAttack = result.mode == GameMode.TIME_ATTACK
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(
                    when {
                        timeAttack -> R.string.result_time_up
                        result.won -> R.string.result_won
                        else -> R.string.result_lost
                    },
                ),
                color = if (result.won) Glow else Danger,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Syne,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            if (result.won && !timeAttack) {
                AnimatedStars(stars = result.stars, reducedMotion = ui.profile.settings.reducedMotion)
            } else if (!result.won) {
                Text(
                    text = when (result.reason) {
                        Outcome.Lost.OutOfTime -> stringResource(R.string.result_lost_time)
                        Outcome.Lost.PufferExploded -> stringResource(R.string.result_lost_puffer)
                        else -> stringResource(R.string.result_lost_moves)
                    },
                    color = Mist,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            val shown = remember(result) { Animatable(if (ui.profile.settings.reducedMotion) result.score.toFloat() else 0f) }
            LaunchedEffect(result) { shown.animateTo(result.score.toFloat(), tween(900)) }
            Text(stringResource(R.string.result_score, shown.value.toInt()), color = Foam, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (result.pearls > 0) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.result_pearls, result.pearls), color = Plankton, fontSize = 16.sp)
            }
            if (result.won && result.mode == GameMode.CAMPAIGN && result.streak >= 3) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.result_streak, result.streak, streakBonusText(result.streak)), color = Current, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            if (result.won && result.mode == GameMode.CAMPAIGN) {
                val total = ProfileReducer.totalStars(ui.profile)
                Spacer(Modifier.height(10.dp))
                Column(modifier = Modifier.width(220.dp)) {
                    Text(stringResource(R.string.result_chest_progress, total % 20, 20), color = Mist, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    ProgressBar(value = (total % 20) / 20f, color = Plankton)
                }
            }
            if (!result.won && result.mode != GameMode.TIME_ATTACK) {
                ui.board?.let { board -> MissingGoals(board) }
            }
            if (result.lifeLost) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.result_life_lost), color = Danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            val canAdvance = result.won && (result.mode == GameMode.CAMPAIGN || result.mode == GameMode.ENDLESS || result.mode == GameMode.PUZZLE)
            if (canAdvance) {
                PrimaryButton(stringResource(R.string.result_next), modifier = Modifier.fillMaxWidth()) { callbacks.onNext() }
                Spacer(Modifier.height(8.dp))
            }
            val retryLabel = if (result.won && result.stars < 3 && result.mode == GameMode.CAMPAIGN) {
                R.string.result_replay
            } else {
                R.string.result_retry
            }
            if (canAdvance) {
                SecondaryButton(stringResource(retryLabel), modifier = Modifier.fillMaxWidth()) { callbacks.onRetry() }
            } else {
                PrimaryButton(stringResource(retryLabel), modifier = Modifier.fillMaxWidth()) { callbacks.onRetry() }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                stringResource(if (result.mode == GameMode.CAMPAIGN) R.string.result_map else R.string.back),
                modifier = Modifier.fillMaxWidth(),
            ) { callbacks.onBack() }
        }
    }
}

/** On a loss, what was left of each goal ("4 × Bubble"). */
@Composable
private fun MissingGoals(board: pt.tiago.nightcrush.core.model.GameState) {
    val lines = ArrayList<String>()
    board.objectives.forEachIndexed { index, objective ->
        val slice = board.progress[index]
        when (objective) {
            is pt.tiago.nightcrush.core.model.Objective.CollectColors -> for (color in 0 until 6) {
                val left = objective.counts[color] - slice[color]
                if (objective.counts[color] > 0 && left > 0) lines.add("$left × " + stringResource(pt.tiago.nightcrush.ui.components.pieceNameKey(color)))
            }
            is pt.tiago.nightcrush.core.model.Objective.Orders -> objective.orders.forEachIndexed { slot, order ->
                val left = order.target - slice[slot]
                if (left > 0) lines.add("$left × " + pt.tiago.nightcrush.ui.components.orderLabel(order))
            }
            else -> {
                val (value, target) = pt.tiago.nightcrush.ui.components.objectiveProgress(board, index)
                if (value < target) lines.add("${target - value} × " + stringResource(pt.tiago.nightcrush.ui.components.objectiveLabelKey(objective)))
            }
        }
    }
    if (lines.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    EyebrowLabel(stringResource(R.string.result_missing))
    Spacer(Modifier.height(4.dp))
    for (line in lines) Text(line, color = Foam, fontSize = 15.sp)
}

/** The won stars pop in one after the other. */
@Composable
private fun AnimatedStars(stars: Int, reducedMotion: Boolean) {
    val scales = remember(stars) { List(3) { Animatable(if (reducedMotion) 1f else 0f) } }
    LaunchedEffect(stars) {
        if (reducedMotion) return@LaunchedEffect
        scales.forEachIndexed { i, anim ->
            delay(if (i == 0) 250L else 280L)
            anim.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 3) {
            val big = i == 1
            NightIcon(
                NightIcons.Star,
                tint = if (i < stars) Plankton else Reef,
                size = if (big) 64.dp else 50.dp,
                modifier = Modifier.graphicsLayer {
                    val s = scales[i].value
                    scaleX = s
                    scaleY = s
                    alpha = s.coerceIn(0f, 1f)
                },
            )
        }
    }
}

// ----------------------------------------------------------------- free / daily

@Composable
private fun FreePlayScreen(ui: UiState, callbacks: UiCallbacks) {
    ScreenScaffold(title = stringResource(R.string.free_title), onBack = { callbacks.onBack() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DifficultyChips(
                selected = ui.difficulty,
                labelOf = { difficultyLabel(it) },
                onSelect = { callbacks.onDifficultyChanged(it) },
            )
            Spacer(Modifier.height(10.dp))
            ModeCard(stringResource(R.string.free_daily), stringResource(R.string.free_daily_desc)) { callbacks.onOpenDaily() }
            ModeCard(stringResource(R.string.free_endless), stringResource(R.string.free_endless_desc)) {
                callbacks.onStartEndless(ui.difficulty)
            }
            ModeCard(stringResource(R.string.free_time), stringResource(R.string.free_time_desc)) {
                callbacks.onStartTimeAttack(ui.difficulty)
            }
            ModeCard(stringResource(R.string.free_zen), stringResource(R.string.free_zen_desc)) { callbacks.onStartZen() }
            ModeCard(stringResource(R.string.free_puzzles), stringResource(R.string.free_puzzles_desc)) { callbacks.onOpenPuzzles() }
            Spacer(Modifier.height(8.dp))
            val records = ui.profile.freePlay
            val key = ui.difficulty.name.lowercase()
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    EyebrowLabel(stringResource(R.string.profile_records) + " · " + difficultyLabel(ui.difficulty))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.free_endless_record, records.endlessLevels[key] ?: 0), color = Foam, fontSize = 14.sp)
                    Text(stringResource(R.string.free_time_record, records.timeAttackScore[key] ?: 0), color = Foam, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, description: String, onClick: () -> Unit) {
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { onClick() },
    ) {
        Column {
            Text(title, color = Foam, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Mist, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DailyScreen(ui: UiState, callbacks: UiCallbacks) {
    ScreenScaffold(title = stringResource(R.string.daily_title), onBack = { callbacks.onBack() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DifficultyChips(
                selected = ui.difficulty,
                labelOf = { difficultyLabel(it) },
                onSelect = {
                    callbacks.onDifficultyChanged(it)
                    callbacks.onOpenDaily()
                },
            )
            Spacer(Modifier.height(10.dp))
            val entry = ui.dailyEntry
            if (entry == null) {
                Text(stringResource(R.string.msg_not_ready), color = Mist)
            } else {
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            stringResource(R.string.daily_today, entry.ordinal, localized(entry.titlePt, entry.titleEn)),
                            color = Foam, fontSize = 16.sp,
                        )
                        val key = ui.difficulty.name.lowercase()
                        val played = ui.profile.dailyHistory.firstOrNull { it.epochDay == ui.today && it.difficulty == key }
                        if (played != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.daily_played, played.stars), color = Plankton, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.free_start), modifier = Modifier.fillMaxWidth()) {
                    callbacks.onSelectDailyLevel(entry.ordinal, GameMode.DAILY)
                }
            }
            if (ui.profile.dailyHistory.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                EyebrowLabel(stringResource(R.string.profile_daily_history))
                Spacer(Modifier.height(6.dp))
                val formatter = remember { java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM) }
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        for (item in ui.profile.dailyHistory.take(30)) {
                            val difficulty = Difficulty.entries.firstOrNull { it.name.lowercase() == item.difficulty }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(java.time.LocalDate.ofEpochDay(item.epochDay).format(formatter), color = Foam, fontSize = 14.sp)
                                    Text(difficulty?.let { difficultyLabel(it) } ?: item.difficulty, color = Mist, fontSize = 12.sp)
                                }
                                Text("${item.score}", color = Mist, fontSize = 13.sp)
                                Spacer(Modifier.width(10.dp))
                                StarRow(item.stars, size = 14)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------- settings

@Composable
private fun SettingsScreen(ui: UiState, callbacks: UiCallbacks) {
    val settings = ui.profile.settings
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = { callbacks.onBack() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsSection(stringResource(R.string.settings_section_sound)) {
                SettingToggle(stringResource(R.string.settings_sound), settings.soundOn) { callbacks.onToggleSetting("sound") }
                if (settings.soundOn) {
                    VolumeSlider(stringResource(R.string.settings_volume_sfx), settings.sfxVolume) { callbacks.onVolume(music = false, value = it) }
                }
                SettingToggle(stringResource(R.string.settings_music), settings.musicOn) { callbacks.onToggleSetting("music") }
                if (settings.musicOn) {
                    VolumeSlider(stringResource(R.string.settings_volume_music), settings.musicVolume) { callbacks.onVolume(music = true, value = it) }
                }
                SettingToggle(stringResource(R.string.settings_vibration), settings.vibrationOn) { callbacks.onToggleSetting("vibration") }
            }
            SettingsSection(stringResource(R.string.settings_section_play)) {
                Text(stringResource(R.string.settings_speed), color = Foam, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    for ((speed, label) in listOf(1f to "1", 1.5f to "1,5", 2f to "2")) {
                        Choice(stringResource(R.string.settings_speed_value, label), settings.animationSpeed == speed, Modifier.weight(1f)) {
                            callbacks.onAnimationSpeed(speed)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.settings_hints), color = Foam, fontSize = 15.sp)
                for ((mode, label) in listOf(
                    "delay" to R.string.settings_hints_delay,
                    "buttonOnly" to R.string.settings_hints_button,
                    "off" to R.string.settings_hints_off,
                )) {
                    ListChoice(stringResource(label), settings.hints == mode || (mode == "delay" && settings.hints == "always")) {
                        callbacks.onHintsMode(mode)
                    }
                }
                Spacer(Modifier.height(4.dp))
                SettingToggle(stringResource(R.string.settings_confirm_boosters), settings.confirmBoosters) { callbacks.onToggleSetting("confirmBoosters") }
            }
            SettingsSection(stringResource(R.string.settings_section_access)) {
                SettingToggle(stringResource(R.string.settings_reduced_motion), settings.reducedMotion) { callbacks.onToggleSetting("reducedMotion") }
                SettingToggle(stringResource(R.string.settings_left_handed), settings.leftHanded) { callbacks.onToggleSetting("leftHanded") }
                SettingToggle(stringResource(R.string.settings_larger_text), settings.largerText) { callbacks.onToggleSetting("largerText") }
                SettingToggle(stringResource(R.string.settings_contrast_symbols), settings.contrastSymbols) { callbacks.onToggleSetting("contrastSymbols") }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_palette), color = Foam, fontSize = 15.sp)
                for ((palette, label) in listOf(
                    "normal" to R.string.settings_palette_normal,
                    "deuteranopia" to R.string.settings_palette_deuteranopia,
                    "protanopia" to R.string.settings_palette_protanopia,
                    "tritanopia" to R.string.settings_palette_tritanopia,
                )) {
                    ListChoice(stringResource(label), settings.colorPalette == palette) { callbacks.onPalette(palette) }
                }
            }
            SettingsSection(stringResource(R.string.settings_section_look)) {
                Text(stringResource(R.string.settings_theme), color = Foam, fontSize = 15.sp)
                val unlocked = StickerBook.unlockedThemes(ui.profile.collection)
                val pages = StickerBook.completePages(ui.profile.collection)
                for ((theme, pagesNeeded) in listOf("BIO" to 0, "GEM" to 4, "CANDY" to 8, "NEON" to 12)) {
                    val open = unlocked.contains(theme)
                    ListChoice(
                        label = themeName(theme),
                        active = settings.boardTheme == theme,
                        enabled = open,
                        note = if (open) null else stringResource(R.string.settings_theme_locked, pagesNeeded - pages),
                    ) {
                        callbacks.onTheme(theme)
                    }
                }
            }
            var confirmReset by remember { mutableStateOf(false) }
            SettingsSection(stringResource(R.string.settings_section_data)) {
                SecondaryButton(stringResource(R.string.about_export), modifier = Modifier.fillMaxWidth()) { callbacks.onExportProgress() }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.about_import), modifier = Modifier.fillMaxWidth()) { callbacks.onImportProgress() }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.about_diagnostic), modifier = Modifier.fillMaxWidth()) { callbacks.onExportDiagnostic() }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.settings_reset), modifier = Modifier.fillMaxWidth(), textColor = Danger) { confirmReset = true }
            }
            SecondaryButton(stringResource(R.string.settings_about), modifier = Modifier.fillMaxWidth()) { callbacks.onOpenAbout() }
            Spacer(Modifier.height(8.dp))
            if (confirmReset) {
                ConfirmDialog(
                    title = stringResource(R.string.settings_reset_title),
                    body = stringResource(R.string.settings_reset_body),
                    confirm = stringResource(R.string.settings_reset_yes),
                    danger = true,
                    onConfirm = {
                        confirmReset = false
                        callbacks.onResetProgress()
                    },
                    onCancel = { confirmReset = false },
                )
            }
        }
    }
}

@Composable
private fun VolumeSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Mist, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", color = Mist, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = Glow, activeTrackColor = Glow, inactiveTrackColor = Reef),
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    EyebrowLabel(title)
    Spacer(Modifier.height(6.dp))
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
fun themeName(theme: String): String = stringResource(
    when (theme) {
        "GEM" -> R.string.theme_gem
        "CANDY" -> R.string.theme_candy
        "NEON" -> R.string.theme_neon
        else -> R.string.theme_bio
    },
)

@Composable
private fun Choice(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Glow else Abyss.copy(alpha = 0.5f))
            .border(1.dp, if (active) Glow else Reef, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) OnGlow else Foam, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ListChoice(
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    note: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enabled) CheckDot(active) else NightIcon(NightIcons.Lock, tint = Mist, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = if (!enabled) Mist else Foam, fontSize = 15.sp)
            if (note != null) Text(note, color = Mist.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Foam, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Glow,
                checkedThumbColor = OnGlow,
                checkedBorderColor = Glow,
                uncheckedTrackColor = Abyss,
                uncheckedThumbColor = Mist,
                uncheckedBorderColor = Reef,
            ),
        )
    }
}

// -------------------------------------------------------------------- missions

@Composable
private fun MissionsScreen(ui: UiState, callbacks: UiCallbacks) {
    val missions = ui.profile.missions
    ScreenScaffold(title = stringResource(R.string.missions_title), onBack = { callbacks.onBack() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.missions_desc), color = Mist, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            missions.ids.forEachIndexed { index, id ->
                val template = Missions.pool.firstOrNull { it.id == id } ?: return@forEachIndexed
                val progress = missions.progress.getOrElse(index) { 0 }.coerceAtMost(template.target)
                val claimed = missions.claimed.getOrElse(index) { false }
                Panel(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column {
                        Text(missionLabel(id), color = Foam, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        ProgressBar(value = if (template.target <= 0) 1f else progress.toFloat() / template.target)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$progress/${template.target}", color = Mist, fontSize = 13.sp)
                            when {
                                claimed -> Text(stringResource(R.string.mission_claimed), color = Glow, fontSize = 13.sp)
                                progress >= template.target -> SecondaryButton(stringResource(R.string.mission_claim)) {
                                    callbacks.onClaimMission(index)
                                }
                                else -> Text(stringResource(R.string.mission_reward), color = Plankton, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun missionLabel(id: String): String = stringResource(
    when (id) {
        "create_rays" -> R.string.mission_create_rays
        "special_combos" -> R.string.mission_special_combos
        "win_full_moon" -> R.string.mission_win_full_moon
        "win_medium" -> R.string.mission_win_medium
        "collect_coral" -> R.string.mission_collect_coral
        "deliver_lanterns" -> R.string.mission_deliver_lanterns
        "play_daily" -> R.string.mission_play_daily
        "cascade_4" -> R.string.mission_cascade_4
        "win_easy" -> R.string.mission_win_easy
        "clear_silt" -> R.string.mission_clear_silt
        "free_seahorses" -> R.string.mission_free_seahorses
        else -> R.string.mission_create_fish
    },
)

@Composable
private fun AboutScreen(ui: UiState, callbacks: UiCallbacks) {
    val uriHandler = LocalUriHandler.current
    val email = stringResource(R.string.about_author_email)
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = { callbacks.onBack() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(stringResource(R.string.app_name), color = Foam, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Syne)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = Mist, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.about_description), color = Foam, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            EyebrowLabel(stringResource(R.string.about_author))
            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(stringResource(R.string.about_author_name), color = Foam, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        email,
                        color = Glow,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { runCatching { uriHandler.openUri("mailto:$email") } }.padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_offline), color = Mist, fontSize = 13.sp)
            Text(stringResource(R.string.about_licences), color = Mist, fontSize = 13.sp)
            ui.recoveryNote?.let {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.msg_recovered, it), color = Plankton, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            SecondaryButton(stringResource(R.string.about_diagnostic), modifier = Modifier.fillMaxWidth()) { callbacks.onExportDiagnostic() }
        }
    }
}
