package com.vini.miniprinter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Ink on receipt paper": charcoal ink, warm paper background, sunny yellow accent (matches the icon). */
object Brand {
    val Ink = Color(0xFF1E1E24)
    val Yellow = Color(0xFFF4C542)
    val YellowSoft = Color(0xFFFFE9A8)
    val Paper = Color(0xFFFAF7F0)
    val PaperDim = Color(0xFFF0EBDF)
    val Ok = Color(0xFF2E9E5B)
}

private val Light = lightColorScheme(
    primary = Brand.Ink,
    onPrimary = Color.White,
    primaryContainer = Brand.YellowSoft,
    onPrimaryContainer = Color(0xFF2B2100),
    secondary = Color(0xFF6E5B1F),
    onSecondary = Color.White,
    secondaryContainer = Brand.Yellow,
    onSecondaryContainer = Brand.Ink,
    tertiary = Color(0xFFB4532A),
    background = Brand.Paper,
    onBackground = Brand.Ink,
    surface = Brand.Paper,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.PaperDim,
    onSurfaceVariant = Color(0xFF5B5649),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCF5),
    surfaceContainer = Color(0xFFF5F1E7),
    surfaceContainerHigh = Color(0xFFEFEADF),
    surfaceContainerHighest = Color(0xFFE8E2D5),
    outline = Color(0xFFB9B2A2),
    outlineVariant = Color(0xFFDCD5C6),
    error = Color(0xFFC62828),
)

private val Dark = darkColorScheme(
    primary = Brand.Yellow,
    onPrimary = Brand.Ink,
    primaryContainer = Color(0xFF4A3D10),
    onPrimaryContainer = Brand.YellowSoft,
    secondary = Brand.YellowSoft,
    onSecondary = Brand.Ink,
    secondaryContainer = Brand.Yellow,
    onSecondaryContainer = Brand.Ink,
    tertiary = Color(0xFFFFB590),
    background = Color(0xFF141417),
    onBackground = Color(0xFFEDEAE3),
    surface = Color(0xFF141417),
    onSurface = Color(0xFFEDEAE3),
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = Color(0xFFC9C4B8),
    surfaceContainerLowest = Color(0xFF0F0F12),
    surfaceContainerLow = Color(0xFF1A1A1E),
    surfaceContainer = Color(0xFF1F1F24),
    surfaceContainerHigh = Color(0xFF26262C),
    surfaceContainerHighest = Color(0xFF303037),
    outline = Color(0xFF6E6A62),
    outlineVariant = Color(0xFF3B3A40),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val MonoLabel = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.2.sp)

@Composable
fun MiniPrinterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
