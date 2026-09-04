package com.mreddy.liftz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mreddy.liftz.R

/* ------------------------------------------------------------------------------------------
 * PALETTE — warm paper.
 *
 * Off-white notebook stock rather than a black app surface, with orange as the action colour and
 * yellow as the highlight. Green survives from the old palette for "goal hit" and gold for the
 * crown, both warmed slightly so they sit on cream instead of slate.
 * ---------------------------------------------------------------------------------------- */

/* Light — the default, and the look the app is designed around. */
val PaperBg = Color(0xFFFBF7EF)        // aged paper
val PaperSurface = Color(0xFFFFFDF8)   // a sheet laid on it
val PaperSurfaceAlt = Color(0xFFF4EDE0) // tinted block / pressed state
val InkStrong = Color(0xFF241F1A)      // near-black warm brown, body text
val InkMuted = Color(0xFF8A7F72)       // secondary text
val PaperLine = Color(0xFFE4DACA)      // hairline rules and card borders

val LiftzOrange = Color(0xFFF97316)    // primary action
val LiftzOrangeDeep = Color(0xFFC2560B) // pressed / on-light contrast
val LiftzYellow = Color(0xFFFBBF24)    // highlight, secondary
val LiftzGold = Color(0xFFE8A21C)      // crown moments
val LiftzGreen = Color(0xFF3F9142)     // goal hit (darkened for contrast on cream)
val LiftzGreenDim = Color(0xFF2E6B31)

/* Dark — a warm dark, not the old blue-slate. Same accents so the brand holds. */
val NightBg = Color(0xFF17130F)
val NightSurface = Color(0xFF211C17)
val NightSurfaceAlt = Color(0xFF2C251E)
val NightInk = Color(0xFFF3EADC)
val NightMuted = Color(0xFFA79683)
val NightLine = Color(0xFF3A3129)

/* ------------------------------------------------------------------------------------------
 * TYPE — Plus Jakarta Sans.
 *
 * A geometric humanist sans in the same family of shapes as the fonts Anthropic uses in its own
 * material. Those (Styrene, Tiempos) are commercially licensed and cannot be bundled; this is
 * SIL Open Font License, which can. Attribution copy lives at licenses/PlusJakartaSans-OFL.txt.
 *
 * The file is a VARIABLE font, so every weight comes from one 176KB resource rather than five
 * separate files. Variable weight axes need API 26, which is exactly this app's minSdk.
 * ---------------------------------------------------------------------------------------- */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Jakarta = FontFamily(
    jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800)
)

val LiftzTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Jakarta, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Jakarta, fontSize = 23.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Jakarta, fontSize = 19.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Jakarta, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(fontFamily = Jakarta, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Jakarta, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Jakarta, fontSize = 12.sp),
    labelLarge = TextStyle(
        fontFamily = Jakarta, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontFamily = Jakarta, fontSize = 12.sp, fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontFamily = Jakarta, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
)

private val LightColors = lightColorScheme(
    primary = LiftzOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE7D2),
    onPrimaryContainer = LiftzOrangeDeep,
    secondary = LiftzYellow,
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFFFFF1CC),
    onSecondaryContainer = Color(0xFF5B4200),
    background = PaperBg,
    onBackground = InkStrong,
    surface = PaperSurface,
    onSurface = InkStrong,
    surfaceVariant = PaperSurfaceAlt,
    onSurfaceVariant = InkMuted,
    // The surfaceContainer roles are what NavigationBar, Card and friends actually draw with.
    // Leaving them unset falls back to Material's BASELINE PURPLE, which is how the bottom bar
    // ended up lavender against a cream app. Every one of them has to be defined for a custom
    // palette to hold across all M3 components.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFCF6),
    surfaceContainer = Color(0xFFF6F0E4),
    surfaceContainerHigh = Color(0xFFF1E9DA),
    surfaceContainerHighest = Color(0xFFEBE2D0),
    surfaceBright = Color(0xFFFFFDF8),
    surfaceDim = Color(0xFFEDE5D6),
    surfaceTint = LiftzOrange,
    inverseSurface = Color(0xFF322C25),
    inverseOnSurface = Color(0xFFF7EFE2),
    outline = PaperLine,
    outlineVariant = Color(0xFFEFE6D7),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = LiftzOrange,
    onPrimary = Color(0xFF2A1200),
    primaryContainer = Color(0xFF6B3308),
    onPrimaryContainer = Color(0xFFFFE7D2),
    secondary = LiftzYellow,
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF5B4200),
    onSecondaryContainer = Color(0xFFFFF1CC),
    background = NightBg,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceAlt,
    onSurfaceVariant = NightMuted,
    surfaceContainerLowest = Color(0xFF110E0B),
    surfaceContainerLow = Color(0xFF1D1813),
    surfaceContainer = Color(0xFF241E18),
    surfaceContainerHigh = Color(0xFF2E2720),
    surfaceContainerHighest = Color(0xFF393128),
    surfaceBright = Color(0xFF3A322A),
    surfaceDim = Color(0xFF17130F),
    surfaceTint = LiftzOrange,
    inverseSurface = Color(0xFFF3EADC),
    inverseOnSurface = Color(0xFF322C25),
    outline = NightLine,
    outlineVariant = Color(0xFF2F281F),
    error = Color(0xFFF2B8B5)
)

/** Green that stays readable on whichever background is active. */
@Composable
fun goalGreen(): Color = if (isDarkNow()) Color(0xFF5BC45F) else LiftzGreen

/** Gold for crown / PR moments, likewise adjusted per theme. */
@Composable
fun crownGold(): Color = if (isDarkNow()) Color(0xFFFFD05C) else LiftzGold

@Composable
private fun isDarkNow(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun Color.luminance(): Float =
    (0.299f * red + 0.587f * green + 0.114f * blue)

@Composable
fun MreddyLiftzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LiftzTypography,
        content = content
    )
}
