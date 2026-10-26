package pt.tiago.nightcrush.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.game.StickerBook
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.ui.UiCallbacks
import pt.tiago.nightcrush.ui.art.CoastArt
import pt.tiago.nightcrush.ui.art.CoastPanorama
import pt.tiago.nightcrush.ui.art.StickerTile
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.PrimaryButton
import pt.tiago.nightcrush.ui.components.ProgressBar
import pt.tiago.nightcrush.ui.components.ScreenScaffold
import pt.tiago.nightcrush.ui.components.localized
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Reef
import pt.tiago.nightcrush.ui.themeName

/** One page per world: its coast panorama with the lights won so far, and its nine stickers. */
@Composable
fun CollectionScreen(ui: UiState, callbacks: UiCallbacks) {
    val collection = ui.profile.collection
    val pages = StickerBook.completePages(collection)
    var fullscreen by remember { mutableIntStateOf(-1) }
    ScreenScaffold(title = stringResource(R.string.collection_title), onBack = { callbacks.onBack() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.collection_pages, pages, StickerBook.PAGES), color = Mist, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            ProgressBar(value = pages / StickerBook.PAGES.toFloat(), color = Plankton)
            Spacer(Modifier.height(10.dp))
            for (theme in StickerBook.unlockedThemes(collection).filter { it != "BIO" }) {
                Text(stringResource(R.string.collection_theme_unlocked, themeName(theme)), color = Plankton, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            for (page in 1..StickerBook.PAGES) {
                val world = ui.worlds.getOrNull(page - 1)
                val lights = world?.let { CoastArt.lights(ui.profile, it.from, it.to) } ?: IntArray(50)
                val lit = lights.count { it > 0 }
                val allLit = lit == lights.size && lights.isNotEmpty()
                val owned = (1..StickerBook.PER_PAGE).count { (collection.stickers[StickerBook.stickerId(page, it)] ?: 0) > 0 }
                val complete = owned == StickerBook.PER_PAGE
                Panel(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), border = if (complete) Plankton else Reef) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = allLit) { fullscreen = page - 1 },
                        ) {
                            CoastPanorama(world = page - 1, lights = lights, modifier = Modifier.fillMaxSize())
                            if (!allLit) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Abyss.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    NightIcon(NightIcons.Lock, tint = Mist, size = 12.dp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.collection_lights, lit, lights.size), color = Mist, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                world?.let { localized(it.namePt, it.nameEn) } ?: stringResource(R.string.collection_page, page),
                                color = Foam, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            Text("$owned/${StickerBook.PER_PAGE}", color = if (complete) Plankton else Mist, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                            for (position in 1..StickerBook.PER_PAGE) {
                                val has = (collection.stickers[StickerBook.stickerId(page, position)] ?: 0) > 0
                                StickerTile(
                                    page = page,
                                    position = position,
                                    owned = has,
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (fullscreen >= 0) {
        val world = ui.worlds.getOrNull(fullscreen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Abyss)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fullscreen = -1 },
        ) {
            val lights = world?.let { CoastArt.lights(ui.profile, it.from, it.to) } ?: IntArray(50)
            CoastPanorama(world = fullscreen, lights = lights, modifier = Modifier.fillMaxSize())
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    world?.let { localized(it.namePt, it.nameEn) }.orEmpty(),
                    color = Foam, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.close)) { fullscreen = -1 }
            }
        }
    }
}
