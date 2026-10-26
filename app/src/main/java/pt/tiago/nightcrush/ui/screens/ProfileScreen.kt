package pt.tiago.nightcrush.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.SpecialOrderKind
import pt.tiago.nightcrush.game.Achievements
import pt.tiago.nightcrush.game.ProfileReducer
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.ui.UiCallbacks
import pt.tiago.nightcrush.ui.components.EyebrowLabel
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.ProgressBar
import pt.tiago.nightcrush.ui.components.ScreenScaffold
import pt.tiago.nightcrush.ui.components.difficultyLabel
import pt.tiago.nightcrush.ui.components.specialName
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Current
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Syne

/** Statistics by difficulty, specials, records and the way into the achievements (design 8.9). */
@Composable
fun ProfileScreen(ui: UiState, callbacks: UiCallbacks) {
    val profile = ui.profile
    val stats = profile.stats
    ScreenScaffold(title = stringResource(R.string.profile_title), onBack = { callbacks.onBack() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStat(NightIcons.Sparkle, Plankton, "${profile.pearls}", stringResource(R.string.profile_label_pearls), Modifier.weight(1f).fillMaxHeight())
                BigStat(NightIcons.Star, Plankton, "${ProfileReducer.totalStars(profile)}", stringResource(R.string.profile_label_stars), Modifier.weight(1f).fillMaxHeight())
                val minutes = stats.timePlayedMillis / 60_000L
                BigStat(
                    NightIcons.Calendar, Glow,
                    stringResource(R.string.profile_time_value, (minutes / 60).toInt(), (minutes % 60).toInt()),
                    stringResource(R.string.profile_time_played),
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(12.dp))
            val unlocked = profile.achievements.count { id -> Achievements.all.any { it.id == id } }
            Panel(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { callbacks.onOpenAchievements() }, border = Plankton.copy(alpha = 0.6f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NightIcon(NightIcons.Star, tint = Plankton, size = 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.achievements_title), color = Foam, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.achievements_count, unlocked, Achievements.all.size), color = Mist, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(value = unlocked / Achievements.all.size.toFloat(), color = Plankton)
                    }
                    Spacer(Modifier.width(10.dp))
                    NightIcon(NightIcons.Forward, tint = Mist, size = 20.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            for (difficulty in Difficulty.entries) {
                val key = difficulty.name.lowercase()
                val wins = stats.wins[key] ?: 0
                val plays = stats.plays[key] ?: 0
                val rate = if (plays == 0) 0 else 100 * wins / plays
                Panel(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            EyebrowLabel(difficultyLabel(difficulty))
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(R.string.profile_win_rate_value, wins, plays, rate), color = Foam, fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            ProgressBar(value = rate / 100f, color = Current)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NightIcon(NightIcons.Star, tint = Plankton, size = 16.dp)
                                Spacer(Modifier.width(4.dp))
                                Text("${ProfileReducer.starsFor(profile, difficulty)}", color = Foam, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(stringResource(R.string.profile_best_short, stats.bestScore[key] ?: 0), color = Mist, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            EyebrowLabel(stringResource(R.string.profile_specials))
            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    for (kind in SpecialOrderKind.entries) {
                        StatLine(specialName(kind.name), stats.specialsCreated[kind.ordinal.toString()] ?: 0)
                    }
                    StatLine(stringResource(R.string.profile_combos), stats.combos.values.sum())
                    StatLine(stringResource(R.string.profile_max_cascade), stats.maxCascade)
                    StatLine(stringResource(R.string.profile_streak_max), profile.maxStreak)
                }
            }
            Spacer(Modifier.height(16.dp))
            EyebrowLabel(stringResource(R.string.profile_records))
            Spacer(Modifier.height(6.dp))
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    for (difficulty in Difficulty.entries) {
                        val key = difficulty.name.lowercase()
                        Text(difficultyLabel(difficulty), color = Mist, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            stringResource(R.string.profile_records_endless, profile.freePlay.endlessLevels[key] ?: 0) + " · " +
                                stringResource(R.string.profile_records_time, profile.freePlay.timeAttackScore[key] ?: 0),
                            color = Foam, fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    StatLine(stringResource(R.string.profile_puzzles), profile.freePlay.puzzlesSolved.size)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BigStat(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Abyss.copy(alpha = 0.55f)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NightIcon(icon, tint = tint, size = 20.dp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Foam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Syne, maxLines = 1, softWrap = false)
        Text(label, color = Mist, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Foam, fontSize = 14.sp)
        Text("$value", color = Foam, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
