package com.mreddy.liftz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* Palette: dark gym-slate base, one green for progress, one gold for crown/PR moments. */
val LiftzGreen = Color(0xFF2ECC71)
val LiftzGreenDim = Color(0xFF1E7A45)
val LiftzGold = Color(0xFFF5C542)
val LiftzBg = Color(0xFF0E1113)
val LiftzSurface = Color(0xFF161B1F)
val LiftzSurfaceHigh = Color(0xFF1F262B)
val LiftzOnDark = Color(0xFFE7EDF2)
val LiftzMuted = Color(0xFF8A97A3)

private val DarkColors = darkColorScheme(
    primary = LiftzGreen,
    onPrimary = Color(0xFF06210F),
    secondary = LiftzGold,
    onSecondary = Color(0xFF231A00),
    background = LiftzBg,
    onBackground = LiftzOnDark,
    surface = LiftzSurface,
    onSurface = LiftzOnDark,
    surfaceVariant = LiftzSurfaceHigh,
    onSurfaceVariant = LiftzMuted,
    outline = Color(0xFF33404A)
)

private val LightColors = lightColorScheme(
    primary = LiftzGreenDim,
    secondary = Color(0xFFB98A00),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF)
)

val LiftzTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

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
