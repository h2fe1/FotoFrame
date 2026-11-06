package com.fotoframe.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand palette translated from the Mac app: warm paper, dark ink, bronze accent.
 * The layout/components are Material 3; only the colour + camera assets are shared.
 */
@Immutable
data class Palette(
    val paper: Color,
    val surface: Color,
    val stage: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val line2: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val ok: Color,
    val error: Color,
    val isDark: Boolean,
)

val LightPalette = Palette(
    paper = Color(0xFFFBF8F1),
    surface = Color(0xFFFFFCF6),
    stage = Color(0xFFF5F1E8),
    ink = Color(0xFF1B1814),
    ink2 = Color(0xFF6F675A),
    ink3 = Color(0xFF9C927F),
    line = Color(0xFFE5DED1),
    line2 = Color(0xFFF0EADF),
    accent = Color(0xFFA2652F),
    accentInk = Color(0xFFFFF7E9),
    accentSoft = Color(0xFFF1E5D6),
    ok = Color(0xFF6F8A5B),
    error = Color(0xFFA3482E),
    isDark = false,
)

val DarkPalette = Palette(
    paper = Color(0xFF121110),
    surface = Color(0xFF1B1917),
    stage = Color(0xFF0D0C0B),
    ink = Color(0xFFECE6DA),
    ink2 = Color(0xFFAAA193),
    ink3 = Color(0xFF7B7264),
    line = Color(0xFF302C26),
    line2 = Color(0xFF24211C),
    accent = Color(0xFFD9A465),
    accentInk = Color(0xFF1A1208),
    accentSoft = Color(0xFF2D2418),
    ok = Color(0xFF8FAE74),
    error = Color(0xFFE08A6A),
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun FotoFrameTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentInk,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.ink,
            secondary = palette.ink2,
            onSecondary = palette.paper,
            secondaryContainer = palette.accentSoft,
            onSecondaryContainer = palette.ink,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.accentSoft,
            onSurfaceVariant = palette.ink2,
            outline = palette.line,
            outlineVariant = palette.line2,
            error = palette.error,
            onError = palette.paper,
            inverseSurface = palette.ink,
            inverseOnSurface = palette.paper,
            inversePrimary = palette.accent,
            scrim = Color.Black,
            surfaceTint = palette.accent,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentInk,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.ink,
            secondary = palette.ink2,
            onSecondary = palette.paper,
            secondaryContainer = palette.accentSoft,
            onSecondaryContainer = palette.ink,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.accentSoft,
            onSurfaceVariant = palette.ink2,
            outline = palette.line,
            outlineVariant = palette.line2,
            error = palette.error,
            onError = palette.paper,
            inverseSurface = palette.ink,
            inverseOnSurface = palette.paper,
            inversePrimary = palette.accent,
            scrim = Color.Black,
            surfaceTint = palette.accent,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Serif is reserved for the brand/empty-state moment; UI otherwise stays Material sans. */
fun Palette.serifHeadline(size: Int = 28): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = size.sp,
    lineHeight = (size * 1.2f).sp,
    letterSpacing = 0.2.sp,
    color = ink,
    fontWeight = FontWeight.Normal,
)
