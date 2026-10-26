package pt.tiago.nightcrush.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.game.PuzzleRules
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.ui.UiCallbacks
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.ProgressBar
import pt.tiago.nightcrush.ui.components.ScreenScaffold
import pt.tiago.nightcrush.ui.components.StarRow
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Current
import pt.tiago.nightcrush.ui.theme.Deep
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.OnGlow
import pt.tiago.nightcrush.ui.theme.Reef
import pt.tiago.nightcrush.ui.theme.Syne

/** Four packs of 25 puzzles; each pack opens after 10 solved in the one before. */
@Composable
fun PuzzlesScreen(ui: UiState, callbacks: UiCallbacks) {
    val profile = ui.profile
    val pack = ui.puzzlePack
    ScreenScaffold(title = stringResource(R.string.puzzles_title), onBack = { callbacks.onBack() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.puzzle_rules), color = Mist, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (p in 1..4) {
                    val open = PuzzleRules.isPackOpen(profile, p)
                    val active = p == pack
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) Glow else Deep)
                            .border(1.dp, if (active) Glow else Reef, RoundedCornerShape(12.dp))
                            .clickable { callbacks.onPuzzlePack(p) }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!open) {
                            NightIcon(NightIcons.Lock, tint = if (active) OnGlow else Mist, size = 14.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("$p", color = if (active) OnGlow else Foam, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val solved = PuzzleRules.solvedIn(profile, pack)
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(stringResource(R.string.puzzle_pack, pack), color = Foam, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.puzzle_progress, solved, PuzzleRules.PER_PACK), color = Mist, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(value = solved / PuzzleRules.PER_PACK.toFloat())
                    if (!PuzzleRules.isPackOpen(profile, pack)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.puzzle_pack_locked, PuzzleRules.TO_OPEN_NEXT_PACK, pack - 1),
                            color = Current, fontSize = 13.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val first = (pack - 1) * PuzzleRules.PER_PACK + 1
            for (row in 0 until 5) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    for (col in 0 until 5) {
                        val number = first + row * 5 + col
                        val available = ui.puzzles.any { it.number == number }
                        val unlocked = available && PuzzleRules.isUnlocked(profile, number)
                        val stars = profile.freePlay.puzzleStars[number.toString()] ?: 0
                        val done = number in profile.freePlay.puzzlesSolved
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.82f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (done) Glow.copy(alpha = 0.14f) else if (unlocked) Deep else Abyss.copy(alpha = 0.6f))
                                .border(1.dp, if (done) Glow else Reef, RoundedCornerShape(14.dp))
                                .clickable(enabled = unlocked) { callbacks.onStartPuzzle(number) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (unlocked) {
                                Text("$number", color = Foam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Syne)
                                Spacer(Modifier.height(4.dp))
                                Box { StarRow(stars, size = 10) }
                            } else {
                                NightIcon(NightIcons.Lock, tint = Reef, size = 18.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
