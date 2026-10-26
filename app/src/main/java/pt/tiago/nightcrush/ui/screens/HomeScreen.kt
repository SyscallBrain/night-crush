package pt.tiago.nightcrush.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.data.Profile
import pt.tiago.nightcrush.game.LifeRules
import pt.tiago.nightcrush.game.Missions
import pt.tiago.nightcrush.game.ProfileReducer
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.game.WeeklyEvents
import pt.tiago.nightcrush.ui.UiCallbacks
import pt.tiago.nightcrush.ui.art.CoastArt
import pt.tiago.nightcrush.ui.art.CoastPanorama
import pt.tiago.nightcrush.ui.components.DifficultyChips
import pt.tiago.nightcrush.ui.components.HeartsRow
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.EyebrowLabel
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.PrimaryButton
import pt.tiago.nightcrush.ui.components.ProgressBar
import pt.tiago.nightcrush.ui.components.SecondaryButton
import pt.tiago.nightcrush.ui.components.difficultyLabel
import pt.tiago.nightcrush.ui.components.formatDuration
import pt.tiago.nightcrush.ui.components.localized
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Deep
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef
import pt.tiago.nightcrush.ui.theme.Syne

/** The home screen: the world card, lives, streak and the side tiles. */
@Composable
fun HomeScreen(ui: UiState, callbacks: UiCallbacks) {
    val profile = ui.profile
    val difficulty = ui.difficulty
    val nextOrdinal = ui.nextOrdinal()
    val worldIndex = ((nextOrdinal - 1) / 50).coerceIn(0, (ui.worlds.size - 1).coerceAtLeast(0))
    val world = ui.worlds.getOrNull(worldIndex)
    val worldStars = world?.let { w ->
        (w.from..w.to).sumOf { profile.levels[ProfileReducer.key(it, difficulty)]?.stars ?: 0 }
    } ?: 0
    val worldMaxStars = world?.let { w -> (w.from..w.to).count { it in ui.available } * 3 }?.coerceAtLeast(1) ?: 150
    val now = ui.nowEpochMs
    val infinite = profile.infiniteLivesUntilEpochMs > now
    val lives = if (infinite) Profile.MAX_LIVES else LifeRules.refill(profile, now).lives

    val dailyDone = profile.dailyHistory.any { it.epochDay == ui.today && it.difficulty == difficulty.name.lowercase() }
    val totalStars = ProfileReducer.totalStars(profile)
    val missionsDone = missionsDoneCount(ui)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    // The logo is stacked like in the design: "Night" over "Crush".
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Night", color = Foam, fontSize = 44.sp, lineHeight = 40.sp,
                            fontWeight = FontWeight.ExtraBold, fontFamily = Syne, maxLines = 1, softWrap = false,
                        )
                        Text(
                            "Crush", color = Glow, fontSize = 44.sp, lineHeight = 40.sp,
                            fontWeight = FontWeight.ExtraBold, fontFamily = Syne, maxLines = 1, softWrap = false,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.home_tagline), color = Mist, fontSize = 14.sp)
                    }
                    PearlChip(profile.pearls)
                }
                Spacer(Modifier.height(16.dp))
                DifficultyChips(
                    selected = difficulty,
                    labelOf = { difficultyLabel(it) },
                    onSelect = { callbacks.onDifficultyChanged(it) },
                )
                Spacer(Modifier.height(12.dp))

                profile.savedMatch?.let { saved ->
                    val ordinal = saved.levelId.removePrefix("level-").toIntOrNull() ?: 0
                    Panel(modifier = Modifier.fillMaxWidth(), border = Plankton) {
                        Column {
                            EyebrowLabel(stringResource(R.string.home_saved_match), color = Plankton)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                PrimaryButton(stringResource(R.string.home_resume, ordinal), modifier = Modifier.weight(1f)) {
                                    callbacks.onResumeSavedMatch()
                                }
                                SecondaryButton(stringResource(R.string.home_discard)) { callbacks.onDiscardSavedMatch() }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Panel(modifier = Modifier.fillMaxWidth(), border = Glow.copy(alpha = 0.35f)) {
                    Column {
                        if (world != null) {
                            CoastPanorama(
                                world = worldIndex,
                                lights = CoastArt.lights(profile, world.from, world.to),
                                modifier = Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        EyebrowLabel(stringResource(R.string.home_world_eyebrow, worldIndex + 1, ui.worlds.size.coerceAtLeast(1)))
                        Text(
                            text = world?.let { localized(it.namePt, it.nameEn) }.orEmpty(),
                            color = Foam, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Syne,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.home_level_progress,
                                nextOrdinal,
                                600,
                                worldStars,
                                worldMaxStars,
                                difficultyLabel(difficulty),
                            ),
                            color = Mist, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(value = worldStars / worldMaxStars.toFloat())
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(stringResource(R.string.home_play_level, nextOrdinal), modifier = Modifier.fillMaxWidth()) {
                            callbacks.onSelectLevel(nextOrdinal)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Panel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeartsRow(lives = lives, max = Profile.MAX_LIVES)
                            Spacer(Modifier.weight(1f))
                            if (infinite) {
                                Text(
                                    stringResource(R.string.home_infinite_lives, formatDuration(profile.infiniteLivesUntilEpochMs - now)),
                                    color = Plankton, fontSize = 12.sp,
                                )
                            } else if (lives < Profile.MAX_LIVES) {
                                Text(
                                    stringResource(R.string.home_next_life, formatDuration(LifeRules.nextLifeInMillis(profile, now))),
                                    color = Mist, fontSize = 12.sp,
                                )
                            }
                        }
                        if (profile.streak > 0) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                for (i in 1..7) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (i <= profile.streak) Glow else Reef),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    pluralStringResource(R.plurals.home_streak_wins, profile.streak, profile.streak),
                                    color = Mist, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeTile(
                        icon = NightIcons.Calendar,
                        title = stringResource(R.string.home_tile_daily),
                        subtitle = stringResource(if (dailyDone) R.string.home_tile_daily_done else R.string.home_tile_daily_sub),
                        highlight = !dailyDone,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { callbacks.onOpenDaily() }
                    val ready = profile.unopenedStarChests
                    HomeTile(
                        icon = NightIcons.Chest,
                        title = stringResource(R.string.home_tile_chest),
                        subtitle = if (ready > 0) {
                            pluralStringResource(R.plurals.home_chest_ready, ready, ready)
                        } else {
                            stringResource(R.string.stars_of, totalStars % STARS_PER_CHEST, STARS_PER_CHEST)
                        },
                        highlight = ready > 0,
                        progress = if (ready > 0) null else (totalStars % STARS_PER_CHEST) / STARS_PER_CHEST.toFloat(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { callbacks.onOpenChest() }
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeTile(
                        icon = NightIcons.Target,
                        title = stringResource(R.string.home_tile_missions),
                        subtitle = stringResource(R.string.home_tile_missions_sub, missionsDone),
                        highlight = false,
                        progress = missionsDone / 3f,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { callbacks.onOpenMissions() }
                    val eventKind = WeeklyEvents.kindFor(ui.today)
                    val eventState = WeeklyEvents.current(profile, ui.today)
                    val eventTiers = WeeklyEvents.tiers(eventKind)
                    HomeTile(
                        icon = eventIcon(eventKind),
                        title = eventName(eventKind),
                        subtitle = pluralStringResource(R.plurals.event_days_left, WeeklyEvents.daysLeft(ui.today), WeeklyEvents.daysLeft(ui.today)),
                        highlight = WeeklyEvents.claimable(profile, ui.today).isNotEmpty(),
                        progress = when {
                            eventTiers.isNotEmpty() -> eventState.progress / eventTiers.last().toFloat()
                            eventKind == WeeklyEvents.Kind.RAYS -> eventState.pearlsEarned / WeeklyEvents.RAY_PEARLS_CAP.toFloat()
                            else -> null
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { callbacks.onOpenEvent() }
                }
                Spacer(Modifier.height(10.dp))
                HomeTile(
                    icon = NightIcons.Infinity,
                    title = stringResource(R.string.home_tile_free),
                    subtitle = stringResource(R.string.home_tile_free_sub),
                    highlight = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { callbacks.onOpenFreePlay() }
                Spacer(Modifier.height(16.dp))
            }
        }

        BottomNav(
            onHome = callbacks::onHome,
            onMap = callbacks::onOpenMap,
            onCollection = callbacks::onOpenCollection,
            onProfile = callbacks::onOpenProfile,
            onSettings = callbacks::onOpenSettings,
        )
    }
}

private const val STARS_PER_CHEST = 20

@Composable
private fun PearlChip(pearls: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Deep)
            .border(1.dp, Reef, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NightIcon(NightIcons.Sparkle, tint = Plankton, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text("$pearls", color = Foam, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomNav(
    onHome: () -> Unit,
    onMap: () -> Unit,
    onCollection: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Abyss.copy(alpha = 0.92f))
            .border(width = 1.dp, color = Reef.copy(alpha = 0.6f), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavButton(NightIcons.Home, stringResource(R.string.nav_home), active = true, onClick = onHome)
        NavButton(NightIcons.Map, stringResource(R.string.nav_map), active = false, onClick = onMap)
        NavButton(NightIcons.Album, stringResource(R.string.nav_collection), active = false, onClick = onCollection)
        NavButton(NightIcons.User, stringResource(R.string.nav_profile), active = false, onClick = onProfile)
        NavButton(NightIcons.Gear, stringResource(R.string.settings_title), active = false, onClick = onSettings)
    }
}

@Composable
private fun RowScope.NavButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NightIcon(icon, tint = if (active) Glow else Mist, size = 22.dp)
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = if (active) Glow else Mist,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    Panel(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable { onClick() },
        border = if (highlight) Plankton.copy(alpha = 0.7f) else Reef,
    ) {
        Column {
            NightIcon(icon, tint = Glow, size = 24.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Foam, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = if (highlight) Plankton else Mist, fontSize = 12.sp)
            }
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                ProgressBar(value = progress)
            }
        }
    }
}

private fun missionsDoneCount(ui: UiState): Int {
    val missions = ui.profile.missions
    return missions.ids.withIndex().count { (index, id) ->
        val target = Missions.pool.first { it.id == id }.target
        (missions.progress.getOrElse(index) { 0 }) >= target
    }
}
