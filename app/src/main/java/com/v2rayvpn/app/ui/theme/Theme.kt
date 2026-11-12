package com.v2rayvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pure AMOLED palette with restrained neon accents. */
object VpnColors {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF08080C)
    val SurfaceElevated = Color(0xFF101016)
    val SurfaceSunken = Color(0xFF05050A)
    val GlassTop = Color(0x1FFFFFFF)
    val GlassBottom = Color(0x08FFFFFF)
    val GlassBorder = Color(0x2BFFFFFF)
    val Purple = Color(0xFFA855F7)
    val PurpleDeep = Color(0xFF7C3AED)
    val Blue = Color(0xFF3B82F6)
    val Cyan = Color(0xFF22D3EE)
    val Green = Color(0xFF22C55E)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFF4F4F7)
    val TextSecondary = Color(0xFF9AA0AE)
    val TextTertiary = Color(0xFF6B7280)
}

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    )
)

private val AppColorScheme = darkColorScheme(
    primary = VpnColors.Purple,
    onPrimary = Color.White,
    primaryContainer = VpnColors.PurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = VpnColors.Blue,
    onSecondary = Color.White,
    tertiary = VpnColors.Cyan,
    onTertiary = VpnColors.Black,
    background = VpnColors.Black,
    onBackground = VpnColors.TextPrimary,
    surface = VpnColors.Surface,
    onSurface = VpnColors.TextPrimary,
    surfaceVariant = VpnColors.SurfaceElevated,
    onSurfaceVariant = VpnColors.TextSecondary,
    outline = VpnColors.GlassBorder,
    error = VpnColors.Red,
    onError = Color.White
)

@Composable
fun V2RayVpnTheme(content: @Composable () -> Unit) {
    // The app is dark-only by design: pure black is the product's identity, not a preference.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
