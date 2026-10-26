package pt.tiago.nightcrush.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LimitType
import pt.tiago.nightcrush.core.model.Objective
import pt.tiago.nightcrush.core.model.Order
import pt.tiago.nightcrush.game.ActiveBooster
import pt.tiago.nightcrush.game.GameMode
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.ui.ConfirmDialog
import pt.tiago.nightcrush.ui.UiCallbacks
import pt.tiago.nightcrush.ui.art.PieceIcon
import pt.tiago.nightcrush.ui.art.PiecePalette
import pt.tiago.nightcrush.ui.art.PiecePalettes
import pt.tiago.nightcrush.ui.art.PieceSprites
import pt.tiago.nightcrush.ui.board.BoardAccessibilityGrid
import pt.tiago.nightcrush.ui.board.BoardCanvas
import pt.tiago.nightcrush.ui.board.rememberTouchExploration
import pt.tiago.nightcrush.ui.components.EyebrowLabel
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.RoundIconButton
import pt.tiago.nightcrush.ui.components.ScoreStarsBar
import pt.tiago.nightcrush.ui.components.ObjectiveRow
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.PrimaryButton
import pt.tiago.nightcrush.ui.components.SecondaryButton
import pt.tiago.nightcrush.ui.components.difficultyLabel
import pt.tiago.nightcrush.ui.components.objectiveLabelKey
import pt.tiago.nightcrush.ui.components.objectiveProgress
import pt.tiago.nightcrush.ui.components.orderLabel
import pt.tiago.nightcrush.ui.components.pieceNameKey
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Danger
import pt.tiago.nightcrush.ui.theme.Deep
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.OnGlow
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef
import pt.tiago.nightcrush.ui.theme.Syne

@Composable
fun GameScreen(ui: UiState, callbacks: UiCallbacks) {
    val board = ui.board ?: return
    val settings = ui.profile.settings
    val style = remember(settings.boardTheme) {
        when (settings.boardTheme) {
            "GEM" -> PieceSprites.Style.GEM
            "CANDY" -> PieceSprites.Style.CANDY
            "NEON" -> PieceSprites.Style.NEON
            else -> PieceSprites.Style.BIO
        }
    }
    val palette = remember(settings.colorPalette) { PiecePalettes.byName(settings.colorPalette) }
    val variant = ui.level?.variants?.get(ui.difficulty.name.lowercase())

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Top bar: pause, title and difficulty, moves or time.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(NightIcons.Pause, contentDescription = stringResource(R.string.game_pause)) { callbacks.onPause() }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    val title = when (ui.mode) {
                        GameMode.ZEN -> stringResource(R.string.free_zen)
                        GameMode.TIME_ATTACK -> stringResource(R.string.free_time)
                        GameMode.ENDLESS -> stringResource(R.string.free_endless)
                        GameMode.DAILY -> stringResource(R.string.daily_title)
                        GameMode.PUZZLE -> stringResource(R.string.puzzle_title, ui.puzzle?.number ?: 0)
                        else -> stringResource(R.string.game_level, ui.level?.ordinal ?: 0)
                    }
                    Text(title, color = Foam, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Syne, maxLines = 1)
                    EyebrowLabel(
                        text = if (ui.mode == GameMode.PUZZLE) stringResource(R.string.puzzle_pack, ui.puzzle?.pack ?: 1) else difficultyLabel(ui.difficulty),
                        color = Mist,
                    )
                }
                // Moves or time: a big number with a small label, never wrapped.
                val (value, label, urgent) = when {
                    ui.mode == GameMode.ZEN -> Triple("∞", "", false)
                    board.limitType == LimitType.TIME ->
                        Triple("${ui.timeLeftSec}", stringResource(R.string.game_time_label), ui.timeLeftSec <= 10)
                    else -> Triple("${board.movesLeft}", stringResource(R.string.game_moves_label), board.movesLeft <= 3 && ui.mode != GameMode.PUZZLE)
                }
                Column(
                    modifier = Modifier
                        .width(82.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Deep)
                        .border(1.dp, if (urgent) Danger else Reef, RoundedCornerShape(16.dp))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        value,
                        color = if (urgent) Danger else Foam,
                        fontSize = 26.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Syne,
                        maxLines = 1,
                    )
                    if (label.isNotEmpty()) Text(label, color = Mist, fontSize = 11.sp, maxLines = 1)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Goals and score.
            if (ui.mode != GameMode.ZEN) {
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        GoalRows(board, palette, style, puzzle = ui.mode == GameMode.PUZZLE)
                        if (ui.mode == GameMode.PUZZLE) {
                            Text(stringResource(R.string.puzzle_exact, board.limitValue), color = Plankton, fontSize = 13.sp)
                        } else {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.result_score, board.score),
                                color = Foam,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            if (variant != null) {
                                ScoreStarsBar(score = board.score, two = variant.stars.two, three = variant.stars.three)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                BoardCanvas(
                    state = board,
                    timeline = ui.timeline,
                    selected = ui.selected,
                    hint = ui.hint,
                    style = style,
                    palette = palette,
                    reducedMotion = settings.reducedMotion,
                    contrastSymbols = settings.contrastSymbols,
                    onCellTap = callbacks::onCellTap,
                    onSwap = callbacks::onSwap,
                    modifier = Modifier.fillMaxSize(),
                )
                if (rememberTouchExploration()) {
                    BoardAccessibilityGrid(
                        state = board,
                        selected = ui.selected,
                        hint = ui.hint,
                        onCellTap = callbacks::onCellTap,
                        onSwap = callbacks::onSwap,
                    )
                }
                // TalkBack reads the summary of each move from this invisible live region.
                Box(
                    modifier = Modifier.size(1.dp).semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = ui.announcement.orEmpty()
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            BoosterBar(ui, callbacks)
        }

        val tutorial = ui.tutorialKey
        if (tutorial != null) {
            TutorialOverlay(textKey = tutorial, onDismiss = callbacks::onDismissTutorial)
        } else if (ui.paused) {
            PauseOverlay(
                ui = ui,
                callbacks = callbacks,
                showCost = ui.mode == GameMode.CAMPAIGN,
            )
        } else if (ui.confirmShuffle) {
            ConfirmDialog(
                title = stringResource(R.string.shuffle_confirm_title),
                body = stringResource(R.string.shuffle_confirm_body, ui.profile.boosters.shuffle),
                confirm = stringResource(R.string.shuffle_confirm_yes),
                onConfirm = { callbacks.onConfirmShuffle(true) },
                onCancel = { callbacks.onConfirmShuffle(false) },
            )
        }
    }
}

/** One row per goal; collecting several colours shows one row per colour. */
@Composable
private fun GoalRows(board: GameState, palette: PiecePalette, style: PieceSprites.Style, puzzle: Boolean = false) {
    board.objectives.forEachIndexed { index, objective ->
        if (objective is Objective.CollectColors) {
            val slice = board.progress[index]
            for (color in 0 until 6) {
                val target = objective.counts[color]
                if (target <= 0) continue
                val value = slice[color].coerceAtMost(target)
                ObjectiveRow(board, index, stringResource(pieceNameKey(color)), value, target) {
                    PieceIcon(color = color, palette = palette, style = style, size = 24.dp)
                }
                Spacer(Modifier.height(8.dp))
            }
        } else if (objective is Objective.Orders) {
            // Orders count at the same time, so each one gets its own row.
            val slice = board.progress[index]
            objective.orders.forEachIndexed { slot, order ->
                val target = order.target
                val value = slice[slot].coerceAtMost(target)
                ObjectiveRow(board, index, orderLabel(order), value, target) {
                    if (order is Order.Collect) {
                        PieceIcon(color = order.color, palette = palette, style = style, size = 24.dp)
                    } else {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            NightIcon(NightIcons.Sparkle, tint = Plankton, size = 18.dp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            val (value, target) = objectiveProgress(board, index)
            val label = if (puzzle) stringResource(R.string.puzzle_goal) else stringResource(objectiveLabelKey(objective))
            ObjectiveRow(board, index, label, value.coerceAtMost(target), target) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    NightIcon(NightIcons.Target, tint = Glow, size = 20.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BoosterBar(ui: UiState, callbacks: UiCallbacks) {
    val boosters = ui.profile.boosters
    val active = ui.activeBooster
    // Puzzles are about the exact line: only the hint is allowed there.
    val puzzle = ui.mode == GameMode.PUZZLE
    val items: List<@Composable RowScope.() -> Unit> = if (puzzle) {
        listOf({ BoosterButton(NightIcons.Bulb, stringResource(R.string.game_hint), count = null, active = false) { callbacks.onHint() } })
    } else listOf(
        {
            BoosterButton(NightIcons.Harpoon, stringResource(R.string.booster_harpoon), boosters.harpoon, active == ActiveBooster.HARPOON) {
                callbacks.onUseBooster(ActiveBooster.HARPOON)
            }
        },
        {
            BoosterButton(NightIcons.Swap, stringResource(R.string.booster_free_swap), boosters.freeSwap, active == ActiveBooster.FREE_SWAP) {
                callbacks.onUseBooster(ActiveBooster.FREE_SWAP)
            }
        },
        {
            BoosterButton(NightIcons.Shuffle, stringResource(R.string.booster_shuffle), boosters.shuffle, false) {
                callbacks.onUseBooster(ActiveBooster.SHUFFLE)
            }
        },
        {
            BoosterButton(NightIcons.Bulb, stringResource(R.string.game_hint), count = null, active = false) { callbacks.onHint() }
        },
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        if (active != null) {
            Text(
                text = stringResource(if (active == ActiveBooster.FREE_SWAP) R.string.game_booster_pick_swap else R.string.game_booster_pick_cell),
                color = Plankton,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left-handed mode mirrors the bar so the hint sits under the left thumb.
            for (item in if (ui.profile.settings.leftHanded) items.reversed() else items) item()
        }
    }
}

/** One booster: icon, name and a count badge (no badge for the hint). */
@Composable
private fun RowScope.BoosterButton(icon: ImageVector, label: String, count: Int?, active: Boolean, onClick: () -> Unit) {
    val enabled = count == null || count > 0
    Box(modifier = Modifier.weight(1f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, end = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) Glow else Deep)
                .border(1.dp, if (active) Glow else Reef, RoundedCornerShape(16.dp))
                .clickable(enabled = enabled) { onClick() }
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NightIcon(icon, tint = if (active) OnGlow else if (enabled) Glow else Mist, size = 24.dp)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                color = if (active) OnGlow else if (enabled) Foam else Mist,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (count != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (count > 0) Plankton else Reef),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", color = Abyss, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The lesson of a tutorial level, shown once before the first move. */
@Composable
private fun TutorialOverlay(textKey: String, onDismiss: () -> Unit) {
    val text = tutorialText(textKey) ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Abyss.copy(alpha = 0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Panel(modifier = Modifier.padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(text.first), color = Foam, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(text.second), color = Foam, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.tut_ok), onClick = onDismiss)
            }
        }
    }
}

/** Title and body for a tutorial key from the level file; null for unknown keys. */
private fun tutorialText(key: String): Pair<Int, Int>? = when (key) {
    "tut_swap" -> R.string.tut_swap_title to R.string.tut_swap
    "tut_ray" -> R.string.tut_ray_title to R.string.tut_ray
    "tut_jelly" -> R.string.tut_jelly_title to R.string.tut_jelly
    "tut_fish" -> R.string.tut_fish_title to R.string.tut_fish
    "tut_pearl" -> R.string.tut_pearl_title to R.string.tut_pearl
    "tut_combo" -> R.string.tut_combo_title to R.string.tut_combo
    "tut_barnacle" -> R.string.tut_barnacle_title to R.string.tut_barnacle
    "tut_silt" -> R.string.tut_silt_title to R.string.tut_silt
    "tut_lanterns" -> R.string.tut_lanterns_title to R.string.tut_lanterns
    "tut_coral" -> R.string.tut_coral_title to R.string.tut_coral
    "tut_net" -> R.string.tut_net_title to R.string.tut_net
    "tut_oil" -> R.string.tut_oil_title to R.string.tut_oil
    "tut_seahorse" -> R.string.tut_seahorse_title to R.string.tut_seahorse
    "tut_conveyor" -> R.string.tut_conveyor_title to R.string.tut_conveyor
    "tut_glow" -> R.string.tut_glow_title to R.string.tut_glow
    "tut_oyster" -> R.string.tut_oyster_title to R.string.tut_oyster
    "tut_sinkhole" -> R.string.tut_sinkhole_title to R.string.tut_sinkhole
    "tut_octopus" -> R.string.tut_octopus_title to R.string.tut_octopus
    else -> null
}

@Composable
private fun PauseOverlay(
    ui: UiState,
    callbacks: UiCallbacks,
    showCost: Boolean,
) {
    val settings = ui.profile.settings
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Abyss.copy(alpha = 0.85f))
            // Swallow every touch so the board underneath cannot be played while paused.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Panel(modifier = Modifier.padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.pause_title), color = Foam, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.pause_resume), onClick = callbacks::onResumePlay)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.pause_restart), modifier = Modifier.fillMaxWidth(), onClick = callbacks::onRetry)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.pause_quit), modifier = Modifier.fillMaxWidth(), onClick = callbacks::onLeaveLevel)
                if (showCost) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.pause_restart_cost), color = Mist, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                EyebrowLabel(stringResource(R.string.pause_quick))
                Spacer(Modifier.height(8.dp))
                // Quick settings (design 7.2): sound, music and speed without leaving the level.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickToggle(NightIcons.Waves, stringResource(R.string.settings_sound), settings.soundOn, Modifier.weight(1f)) {
                        callbacks.onToggleSetting("sound")
                    }
                    QuickToggle(NightIcons.Sparkle, stringResource(R.string.settings_music), settings.musicOn, Modifier.weight(1f)) {
                        callbacks.onToggleSetting("music")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    for ((speed, label) in listOf(1f to "1×", 1.5f to "1,5×", 2f to "2×")) {
                        val active = settings.animationSpeed == speed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Glow else Deep)
                                .border(1.dp, if (active) Glow else Reef, RoundedCornerShape(12.dp))
                                .clickable { callbacks.onAnimationSpeed(speed) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (active) OnGlow else Foam, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.pause_settings),
                    color = Mist,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { callbacks.onOpenSettings() }.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickToggle(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Glow.copy(alpha = 0.16f) else Deep)
            .border(1.dp, if (on) Glow else Reef, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        NightIcon(icon, tint = if (on) Glow else Mist, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (on) Foam else Mist, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
