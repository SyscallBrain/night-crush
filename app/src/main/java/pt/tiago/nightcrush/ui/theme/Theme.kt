package pt.tiago.nightcrush.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The game is dark only: it is night in the sea. There is no light theme.
private val NightCrushColors = darkColorScheme(
    primary = Glow,
    onPrimary = OnGlow,
    secondary = Current,
    onSecondary = Abyss,
    tertiary = Plankton,
    onTertiary = Abyss,
    background = Abyss,
    onBackground = Foam,
    surface = Deep,
    onSurface = Foam,
    surfaceVariant = Reef,
    onSurfaceVariant = Mist,
    outline = Reef,
    error = Danger,
    onError = Abyss,
    scrim = Abyss,
)

@Composable
fun NightCrushTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightCrushColors,
        typography = NightCrushTypography,
        content = content,
    )
}
