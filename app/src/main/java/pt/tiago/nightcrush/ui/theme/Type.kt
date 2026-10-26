package pt.tiago.nightcrush.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import pt.tiago.nightcrush.R

// Display: Syne (titles, logo, big numbers, combo words). Body: Figtree. Both SIL OFL.
val Syne = FontFamily(
    Font(R.font.syne_bold, FontWeight.Bold),
    Font(R.font.syne_extrabold, FontWeight.ExtraBold),
)
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

// Scale: 40 / 28 / 22 / 17 / 15 / 13 / 11 sp.
val NightCrushTypography = Typography(
    displayLarge = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp),
    displayMedium = TextStyle(fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineLarge = TextStyle(fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

/** Uppercase eyebrow labels: small, wide tracking, secondary colour. */
val Eyebrow = TextStyle(
    fontFamily = Figtree,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    letterSpacing = 0.14.em,
)
