package pt.tiago.nightcrush.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.game.WeeklyEvents
import pt.tiago.nightcrush.ui.UiCallbacks
import pt.tiago.nightcrush.ui.components.EyebrowLabel
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.PrimaryButton
import pt.tiago.nightcrush.ui.components.ProgressBar
import pt.tiago.nightcrush.ui.components.ScreenScaffold
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.OnGlow
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef

/** The weekly event (design 8.8): what it is, how far along, the tiers to claim. */
@Composable
fun EventScreen(ui: UiState, callbacks: UiCallbacks) {
    val day = ui.today
    val kind = WeeklyEvents.kindFor(day)
    val state = WeeklyEvents.current(ui.profile, day)
    ScreenScaffold(
        title = eventName(kind),
        eyebrow = stringResource(R.string.event_title),
        onBack = { callbacks.onBack() },
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Panel(modifier = Modifier.fillMaxWidth(), border = Glow.copy(alpha = 0.4f)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NightIcon(eventIcon(kind), tint = Glow, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            pluralStringResource(R.plurals.event_days_left, WeeklyEvents.daysLeft(day), WeeklyEvents.daysLeft(day)),
                            color = Plankton, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (kind) {
                            WeeklyEvents.Kind.RAYS -> stringResource(R.string.event_rays_desc, WeeklyEvents.PEARLS_PER_RAY, WeeklyEvents.RAY_PEARLS_CAP)
                            WeeklyEvents.Kind.LANTERNS -> stringResource(R.string.event_lanterns_desc)
                            WeeklyEvents.Kind.COMBOS -> stringResource(R.string.event_combos_desc)
                            WeeklyEvents.Kind.STICKERS -> stringResource(R.string.event_stickers_desc)
                        },
                        color = Foam, fontSize = 15.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            when (kind) {
                WeeklyEvents.Kind.RAYS -> Panel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            stringResource(R.string.event_rays_progress, state.pearlsEarned, WeeklyEvents.RAY_PEARLS_CAP),
                            color = Foam, fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(value = state.pearlsEarned / WeeklyEvents.RAY_PEARLS_CAP.toFloat(), color = Plankton)
                    }
                }
                WeeklyEvents.Kind.STICKERS -> Panel(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.event_stickers_progress, state.progress), color = Foam, fontSize = 15.sp)
                }
                else -> {
                    val tiers = WeeklyEvents.tiers(kind)
                    EyebrowLabel("${state.progress} / ${tiers.last()}")
                    Spacer(Modifier.height(8.dp))
                    Panel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            tiers.forEachIndexed { index, threshold ->
                                val reached = state.progress >= threshold
                                val claimed = index in state.claimedTiers
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(if (reached) Glow else Abyss)
                                            .border(1.dp, if (reached) Glow else Reef, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (claimed) {
                                            NightIcon(NightIcons.Check, tint = OnGlow, size = 16.dp)
                                        } else {
                                            Text("${index + 1}", color = if (reached) OnGlow else Mist, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.event_tier, index + 1, threshold), color = Foam, fontSize = 14.sp)
                                        ProgressBar(value = (state.progress / threshold.toFloat()).coerceIn(0f, 1f))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.chest_pearls, WeeklyEvents.tierPearls[index]), color = Plankton, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    val ready = WeeklyEvents.claimable(ui.profile, day)
                    if (ready.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(stringResource(R.string.event_claim, ready.sumOf { WeeklyEvents.tierPearls[it] })) {
                            callbacks.onClaimEvent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun eventName(kind: WeeklyEvents.Kind): String = stringResource(
    when (kind) {
        WeeklyEvents.Kind.RAYS -> R.string.event_rays
        WeeklyEvents.Kind.LANTERNS -> R.string.event_lanterns
        WeeklyEvents.Kind.COMBOS -> R.string.event_combos
        WeeklyEvents.Kind.STICKERS -> R.string.event_stickers
    },
)

fun eventIcon(kind: WeeklyEvents.Kind): ImageVector = when (kind) {
    WeeklyEvents.Kind.RAYS -> NightIcons.Waves
    WeeklyEvents.Kind.LANTERNS -> NightIcons.Bulb
    WeeklyEvents.Kind.COMBOS -> NightIcons.Sparkle
    WeeklyEvents.Kind.STICKERS -> NightIcons.Album
}
