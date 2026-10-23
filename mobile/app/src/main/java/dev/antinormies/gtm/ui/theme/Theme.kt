package dev.antinormies.gtm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object GtmColors {
    val ink = Color(0xFF1D1D1F)
    val ink2 = Color(0xFF424245)
    val muted = Color(0xFF6E6E73)
    val meta = Color(0xFF86868B)
    val surface = Color(0xFFFFFFFF)
    val surfaceSoft = Color(0xFFF5F5F7)
    val line = Color(0xFFE5E5EA)
    val lineStrong = Color(0xFFD2D2D7)
    val accent = Color(0xFF0071E3)
    val accentSoft = Color(0x1A0071E3)
    val success = Color(0xFF16A34A)
    val warning = Color(0xFFB45309)
    val danger = Color(0xFFDC2626)
    val stage = Color(0xFF000000)
    val stageCard = Color(0xFF1C1C1F)
    val stageLine = Color(0x22FFFFFF)
    val stageText = Color(0xFFF5F5F7)
    val stageMuted = Color(0xFFA1A1A6)
    val wave = Color(0xFF2997FF)
    val ok = Color(0xFF30D158)
    val alert = Color(0xFFFFB340)
}

private val LightScheme = lightColorScheme(
    primary = GtmColors.accent,
    onPrimary = Color.White,
    secondary = GtmColors.ink,
    onSecondary = Color.White,
    background = GtmColors.surfaceSoft,
    onBackground = GtmColors.ink,
    surface = GtmColors.surface,
    onSurface = GtmColors.ink,
    surfaceVariant = GtmColors.surfaceSoft,
    onSurfaceVariant = GtmColors.muted,
    outline = GtmColors.lineStrong,
    error = GtmColors.danger
)

private val GtmTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.3.sp)
)

@Composable
fun GtmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, typography = GtmTypography, content = content)
}
