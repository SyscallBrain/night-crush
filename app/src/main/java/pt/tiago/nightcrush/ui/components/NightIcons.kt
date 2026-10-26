package pt.tiago.nightcrush.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.tiago.nightcrush.ui.theme.Deep
import pt.tiago.nightcrush.ui.theme.Foam
import pt.tiago.nightcrush.ui.theme.Reef

/**
 * Line icons from the design reference (24×24, 2 px stroke), plus a few filled
 * ones. Kept as vectors so they tint with the theme and stay sharp at any size.
 */
object NightIcons {
    val Home = stroked("M3 11l9-7 9 7v9H3z")
    val Map = stroked("M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z", "M9 4v14M15 6v14")
    val Album = stroked("M6 3h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V5a2 2 0 012-2z", "M8 8h8M8 12h8M8 16h5")
    val User = stroked("M12 4a4 4 0 110 8 4 4 0 010-8z", "M4 21c1-4 4-6 8-6s7 2 8 6")
    val Gear = stroked(
        "M12 9a3 3 0 110 6 3 3 0 010-6z",
        "M19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.8 1.7 1.7 0 00-1.5-1H3a2 2 0 110-4h.1a1.7 1.7 0 001.5-1.1 1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.8.3H9a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.8V9a1.7 1.7 0 001.5 1H21a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z",
    )
    val Calendar = stroked("M6 5h12a3 3 0 013 3v10a3 3 0 01-3 3H6a3 3 0 01-3-3V8a3 3 0 013-3z", "M3 10h18M8 3v4M16 3v4")
    val Chest = stroked("M3 10a5 5 0 015-5h8a5 5 0 015 5v9H3z", "M3 12h18M12 10v5")
    val Infinity = stroked("M7 8c-3 0-4 2-4 4s1 4 4 4c4 0 6-8 10-8 3 0 4 2 4 4s-1 4-4 4c-4 0-6-8-10-8z")
    val Target = stroked("M12 3a9 9 0 110 18 9 9 0 010-18z", "M12 8a4 4 0 110 8 4 4 0 010-8z", "M12 11.5v1")
    val Waves = stroked("M2 8c2.5-3 5-3 7.5 0s5 3 7.5 0 3.5-2 5-1", "M2 13c2.5-3 5-3 7.5 0s5 3 7.5 0 3.5-2 5-1", "M2 18c2.5-3 5-3 7.5 0s5 3 7.5 0 3.5-2 5-1")
    val Harpoon = stroked("M15 10a5 5 0 110 10 5 5 0 010-10z", "M3 3l8 8M7 3l6 6M3 7l6 6")
    val Swap = stroked("M4 8h14l-4-4M20 16H6l4 4")
    val Shuffle = stroked("M3 7h4l10 10h4M3 17h4l3-3M14 10l3-3h4M18 4l3 3-3 3M18 14l3 3-3 3")
    val Bulb = stroked("M9 18h6M10 21h4", "M12 3a6 6 0 00-3.6 10.8c.8.7 1.1 1.4 1.1 2.2h5c0-.8.3-1.5 1.1-2.2A6 6 0 0012 3z")
    val Back = stroked("M15 5l-7 7 7 7")
    val Forward = stroked("M9 5l7 7-7 7")
    val Check = stroked("M5 12.5l4.5 4.5L19 7")
    val Lock = stroked("M7 11h10a2 2 0 012 2v6a2 2 0 01-2 2H7a2 2 0 01-2-2v-6a2 2 0 012-2z", "M8 11V8a4 4 0 018 0v3")
    val Pause = filled("M7 5h3a1 1 0 011 1v12a1 1 0 01-1 1H7a1 1 0 01-1-1V6a1 1 0 011-1zM14 5h3a1 1 0 011 1v12a1 1 0 01-1 1h-3a1 1 0 01-1-1V6a1 1 0 011-1z")
    val Heart = filled("M12 21s-7.5-4.6-9.5-9.3C1.1 8.4 3 5 6.4 5c2.1 0 3.7 1.2 4.6 2.7h2C13.9 6.2 15.5 5 17.6 5 21 5 22.9 8.4 21.5 11.7 19.5 16.4 12 21 12 21z")
    val Star = filled("M12 2.8l2.8 5.9 6.4.8-4.7 4.4 1.2 6.4L12 17.1l-5.7 3.2 1.2-6.4-4.7-4.4 6.4-.8z")
    val Sparkle = filled("M12 2l2 7.5 7.5 2.5-7.5 2.5L12 22l-2-7.5L2.5 12 10 9.5z")
    val Play = filled("M8 5.5v13a1 1 0 001.5.9l10.4-6.5a1 1 0 000-1.8L9.5 4.6A1 1 0 008 5.5z")

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun stroked(vararg paths: String): ImageVector {
        val builder = builder("stroked")
        for (path in paths) {
            builder.addPath(
                pathData = addPathNodes(expandArcFlags(path)),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    private fun filled(path: String): ImageVector =
        builder("filled").addPath(pathData = addPathNodes(expandArcFlags(path)), fill = SolidColor(Color.Black)).build()
}

/**
 * SVG lets arc flags run together ("a4 4 0 110 8" = flags 1, 1 then x 0); the
 * Compose parser reads "110" as one number. Spell every number out instead.
 */
internal fun expandArcFlags(d: String): String {
    val out = StringBuilder()
    var i = 0
    var command = ' '
    var arg = 0
    while (i < d.length) {
        val c = d[i]
        when {
            c.isLetter() -> {
                command = c
                arg = 0
                out.append(c)
                i++
            }
            c.isDigit() || c == '-' || c == '+' || c == '.' -> {
                if ((command == 'a' || command == 'A') && (arg % 7 == 3 || arg % 7 == 4)) {
                    out.append(c)
                    i++
                } else {
                    val start = i
                    if (d[i] == '-' || d[i] == '+') i++
                    var dot = false
                    while (i < d.length && (d[i].isDigit() || (d[i] == '.' && !dot))) {
                        if (d[i] == '.') dot = true
                        i++
                    }
                    out.append(d, start, i)
                }
                out.append(' ')
                arg++
            }
            else -> i++
        }
    }
    return out.toString().trim()
}

@Composable
fun NightIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** A round, outlined icon button, used for back and pause. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Foam,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Deep)
            .border(1.dp, Reef, CircleShape)
            .clickable(onClickLabel = contentDescription) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}
