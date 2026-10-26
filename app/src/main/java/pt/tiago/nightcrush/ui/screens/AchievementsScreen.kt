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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.game.Achievements
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.ui.UiCallbacks
import pt.tiago.nightcrush.ui.components.EyebrowLabel
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.ProgressBar
import pt.tiago.nightcrush.ui.components.ScreenScaffold
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef

/** Every achievement by group, unlocked ones lit, the rest with their progress. */
@Composable
fun AchievementsScreen(ui: UiState, callbacks: UiCallbacks) {
    val profile = ui.profile
    val catalog = remember(ui.worlds, ui.levels) {
        Achievements.Catalog(
            worlds = ui.worlds.map { it.from..it.to },
            fullMoons = ui.levels.filter { it.isFullMoon }.mapTo(HashSet()) { it.ordinal },
            available = ui.available,
        )
    }
    val unlocked = profile.achievements.count { id -> Achievements.all.any { it.id == id } }
    ScreenScaffold(
        title = stringResource(R.string.achievements_title),
        eyebrow = stringResource(R.string.achievements_count, unlocked, Achievements.all.size),
        onBack = { callbacks.onBack() },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            for (group in Achievements.Group.entries) {
                item(key = group.name) {
                    EyebrowLabel(groupName(group), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                }
                for (achievement in Achievements.all.filter { it.group == group }) {
                    item(key = achievement.id) {
                        val done = achievement.id in profile.achievements
                        val (value, target) = achievement.progress(profile, catalog)
                        Panel(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), border = if (done) Plankton.copy(alpha = 0.7f) else Reef) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (done) Plankton.copy(alpha = 0.2f) else Abyss)
                                        .border(1.dp, if (done) Plankton else Reef, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    NightIcon(if (done) NightIcons.Star else NightIcons.Lock, tint = if (done) Plankton else Mist, size = 20.dp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(Achievements.titleRes(achievement.id)), color = if (done) Foam else Mist, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(Achievements.descRes(achievement.id)), color = Mist, fontSize = 12.sp)
                                    if (!done && target > 1) {
                                        Spacer(Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ProgressBar(value = value / target.toFloat(), modifier = Modifier.weight(1f), color = Plankton)
                                            Spacer(Modifier.width(8.dp))
                                            Text("${value.coerceAtMost(target)}/$target", color = Mist, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun groupName(group: Achievements.Group): String = stringResource(
    when (group) {
        Achievements.Group.SPECIALS -> R.string.ach_group_specials
        Achievements.Group.COMBOS -> R.string.ach_group_combos
        Achievements.Group.PLAY -> R.string.ach_group_play
        Achievements.Group.WORLDS -> R.string.ach_group_worlds
        Achievements.Group.META -> R.string.ach_group_meta
    },
)
