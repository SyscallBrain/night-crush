package pt.tiago.nightcrush.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.core.model.WorldEntry
import pt.tiago.nightcrush.game.ChestOpening
import pt.tiago.nightcrush.ui.art.StickerArt
import pt.tiago.nightcrush.ui.art.StickerTile
import pt.tiago.nightcrush.ui.components.NightIcon
import pt.tiago.nightcrush.ui.components.NightIcons
import pt.tiago.nightcrush.ui.components.Panel
import pt.tiago.nightcrush.ui.components.PrimaryButton
import pt.tiago.nightcrush.ui.components.localized
import pt.tiago.nightcrush.ui.theme.Abyss
import pt.tiago.nightcrush.ui.theme.Current
import pt.tiago.nightcrush.ui.theme.Danger
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Glow
import pt.tiago.nightcrush.ui.theme.Mist
import pt.tiago.nightcrush.ui.theme.Plankton
import pt.tiago.nightcrush.ui.theme.Syne

/** A chest opens like a shell (design 8.4), then shows exactly what came out. */
@Composable
fun ChestDialog(chest: ChestOpening, worlds: List<WorldEntry>, reducedMotion: Boolean, onDismiss: () -> Unit) {
    val open = remember(chest) { Animatable(if (reducedMotion) 1f else 0f) }
    val reveal = remember(chest) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(chest) {
        if (reducedMotion) return@LaunchedEffect
        delay(250)
        open.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        reveal.animateTo(1f, tween(400))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Abyss.copy(alpha = 0.9f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Panel(modifier = Modifier.padding(20.dp), border = Plankton.copy(alpha = 0.6f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.verticalScroll(rememberScrollState())) {
                val title = if (chest.world) {
                    val world = worlds.getOrNull(chest.worldIndex)
                    stringResource(R.string.chest_title_world, world?.let { localized(it.namePt, it.nameEn) }.orEmpty())
                } else {
                    stringResource(R.string.chest_title_star)
                }
                Text(title, color = Plankton, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Syne, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Shell(progress = open.value, modifier = Modifier.size(150.dp, 120.dp))
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * 24f },
                ) {
                    RewardRow(NightIcons.Sparkle, Plankton, stringResource(R.string.chest_pearls, chest.pearls))
                    val b = chest.boosters
                    if (b.harpoon > 0) RewardRow(NightIcons.Harpoon, Glow, stringResource(R.string.booster_harpoon) + " ×${b.harpoon}")
                    if (b.freeSwap > 0) RewardRow(NightIcons.Swap, Glow, stringResource(R.string.booster_free_swap) + " ×${b.freeSwap}")
                    if (b.shuffle > 0) RewardRow(NightIcons.Shuffle, Glow, stringResource(R.string.booster_shuffle) + " ×${b.shuffle}")
                    if (b.rayStart > 0) RewardRow(NightIcons.Star, Current, stringResource(R.string.booster_ray_start) + " ×${b.rayStart}")
                    if (b.jellyStart > 0) RewardRow(NightIcons.Star, Current, stringResource(R.string.booster_jelly_start) + " ×${b.jellyStart}")
                    if (b.pearlStart > 0) RewardRow(NightIcons.Star, Current, stringResource(R.string.booster_pearl_start) + " ×${b.pearlStart}")
                    if (chest.infiniteMinutes > 0) {
                        RewardRow(NightIcons.Heart, Danger, stringResource(R.string.chest_infinite, chest.infiniteMinutes))
                    }
                    for ((id, isNew) in chest.stickers) {
                        val page = StickerArt.page(id)
                        val position = StickerArt.position(id)
                        val world = worlds.getOrNull(page - 1)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            StickerTile(page = page, position = position, owned = true, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stickerName(position) + (world?.let { " · " + localized(it.namePt, it.nameEn) } ?: ""),
                                    color = Foam, fontSize = 14.sp,
                                )
                                Text(
                                    stringResource(if (isNew) R.string.chest_new_sticker else R.string.chest_duplicate),
                                    color = if (isNew) Plankton else Mist,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(stringResource(R.string.dialog_ok), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
fun stickerName(position: Int): String = stringResource(
    when (position) {
        1 -> R.string.sticker_name_1
        2 -> R.string.sticker_name_2
        3 -> R.string.sticker_name_3
        4 -> R.string.sticker_name_4
        5 -> R.string.sticker_name_5
        6 -> R.string.sticker_name_6
        7 -> R.string.sticker_name_7
        8 -> R.string.sticker_name_8
        else -> R.string.sticker_name_9
    },
)

@Composable
private fun RewardRow(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) { NightIcon(icon, tint = tint, size = 24.dp) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Foam, fontSize = 15.sp)
    }
}

/** A scallop shell whose top valve swings open on [progress], with a pearl of light inside. */
@Composable
private fun Shell(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val hinge = Offset(w / 2f, h * 0.62f)
        val radius = w * 0.42f
        // Light escaping from inside.
        if (progress > 0f) {
            drawCircle(
                Brush.radialGradient(listOf(Plankton.copy(alpha = 0.55f * progress), Color.Transparent), hinge, radius * 1.3f),
                radius * 1.3f,
                hinge,
            )
            drawCircle(Color.White.copy(alpha = progress), radius * 0.16f, Offset(hinge.x, hinge.y - radius * 0.12f))
        }
        // Bottom valve.
        val bottom = Path().apply {
            moveTo(hinge.x - radius, hinge.y)
            cubicTo(hinge.x - radius, hinge.y + radius * 0.8f, hinge.x + radius, hinge.y + radius * 0.8f, hinge.x + radius, hinge.y)
            close()
        }
        drawPath(bottom, Brush.verticalGradient(listOf(Color(0xFF2BD7B0), Color(0xFF0F5E52)), startY = hinge.y, endY = hinge.y + radius * 0.6f))
        // Top valve: a fan that flips up around the hinge line.
        val top = Path().apply {
            moveTo(hinge.x - radius, hinge.y)
            cubicTo(hinge.x - radius, hinge.y - radius * 1.1f, hinge.x + radius, hinge.y - radius * 1.1f, hinge.x + radius, hinge.y)
            close()
        }
        withTransform({ scale(1f, 1f - 1.55f * progress, pivot = Offset(hinge.x, hinge.y + radius * 0.05f)) }) {
            drawPath(top, Brush.verticalGradient(listOf(Color(0xFF8CF7DC), Glow), startY = hinge.y - radius, endY = hinge.y))
            for (k in 0..6) {
                val angle = Math.PI * (1.0 + k / 6.0)
                val end = Offset(hinge.x + radius * 0.92f * kotlin.math.cos(angle).toFloat(), hinge.y + radius * 0.8f * kotlin.math.sin(angle).toFloat())
                drawLine(Color(0xFF0F5E52).copy(alpha = 0.5f), hinge, end, strokeWidth = 2.dp.toPx())
            }
            drawPath(top, Foam.copy(alpha = 0.5f), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}
