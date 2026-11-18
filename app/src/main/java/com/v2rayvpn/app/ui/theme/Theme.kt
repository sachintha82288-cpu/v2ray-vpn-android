package com.v2rayvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.v2rayvpn.app.domain.model.SurfaceStyle
import com.v2rayvpn.app.domain.model.ThemeAccent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palette with a user-selectable accent and surface style.
 *
 * The values are backed by Compose state, so reading `VpnColors.Purple` inside a composable both
 * returns the current accent and subscribes the caller to accent changes. That keeps the twelve
 * screens free of palette plumbing while still letting the theme switch at runtime.
 */
object VpnColors {

    var accentChoice: ThemeAccent by mutableStateOf(ThemeAccent.VIOLET)
    var surfaceChoice: SurfaceStyle by mutableStateOf(SurfaceStyle.AMOLED)

    private fun accentColor(value: Long): Color = Color(value)

    val Purple: Color get() = accentColor(accentChoice.primary)
    val PurpleDeep: Color get() = accentColor(accentChoice.deep)
    val Blue: Color get() = accentColor(accentChoice.secondary)
    val Cyan: Color get() = accentColor(accentChoice.secondary)

    val Black: Color
        get() = when (surfaceChoice) {
            SurfaceStyle.AMOLED -> Color(0xFF000000)
            SurfaceStyle.MIDNIGHT -> Color(0xFF0B0B14)
        }

    val Surface: Color
        get() = when (surfaceChoice) {
            SurfaceStyle.AMOLED -> Color(0xFF08080C)
            SurfaceStyle.MIDNIGHT -> Color(0xFF12121C)
        }

    val SurfaceElevated: Color
        get() = when (surfaceChoice) {
            SurfaceStyle.AMOLED -> Color(0xFF101016)
            SurfaceStyle.MIDNIGHT -> Color(0xFF1A1A26)
        }

    val SurfaceSunken: Color
        get() = when (surfaceChoice) {
            SurfaceStyle.AMOLED -> Color(0xFF05050A)
            SurfaceStyle.MIDNIGHT -> Color(0xFF0E0E16)
        }

    val GlassTop: Color = Color(0x1FFFFFFF)
    val GlassBottom: Color = Color(0x08FFFFFF)
    val GlassBorder: Color = Color(0x2BFFFFFF)
    val Green: Color = Color(0xFF22C55E)
    val Amber: Color = Color(0xFFF59E0B)
    val Red: Color = Color(0xFFEF4444)
    val TextPrimary: Color = Color(0xFFF4F4F7)
    val TextSecondary: Color = Color(0xFF9AA0AE)
    val TextTertiary: Color = Color(0xFF6B7280)
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

private fun appColorScheme() = darkColorScheme(
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
    // Dark-only by design: the surface style and accent are the user's choices, not light/dark.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = appColorScheme(),
        typography = AppTypography,
        content = content
    )
}
