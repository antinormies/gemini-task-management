package id.ai.antaratask.ui.theme

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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.ai.antaratask.R

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

    // The speech stage is intentionally a deep midnight surface in both themes.
    val stage = Color(0xFF070A12)
    val stageTop = Color(0xFF111827)
    val stageCard = Color(0xFF171C27)
    val stageLine = Color(0x24FFFFFF)
    val stageText = Color(0xFFF7F8FA)
    val stageMuted = Color(0xFF9CA3AF)
    val wave = Color(0xFF5AC8FA)
    val ok = Color(0xFF30D158)
    val alert = Color(0xFFFFB340)

    // Light voice stage, inspired by the soft-lavender reference.
    val speakBgTop = Color(0xFFF8F4FF)
    val speakBg = Color(0xFFF2ECFF)
    val speakBgBottom = Color(0xFFFDFBFF)
    val speakInk = Color(0xFF3D3159)
    val speakMuted = Color(0xFF8C7FA8)
    val speakAccent = Color(0xFF6B4AB8)
    val speakAccentSoft = Color(0xFFEDE3FF)
    val speakCard = Color(0xFFFFFFFF)
    val speakLine = Color(0xFFE7DDF7)
    val orbPink = Color(0xFFFFB0D8)
    val orbBlue = Color(0xFFA6C8FF)
    val orbViolet = Color(0xFFB18CFF)
    val orbLavender = Color(0xFFE2D0FF)
    val orbRose = Color(0xFFFFB3C8)
    val orbMint = Color(0xFFBFF2DF)
}

private val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F5F7),
    onBackground = GtmColors.ink,
    surface = Color.White,
    onSurface = GtmColors.ink,
    surfaceVariant = Color(0xFFF0F0F4),
    onSurfaceVariant = GtmColors.muted,
    outline = GtmColors.lineStrong,
    outlineVariant = GtmColors.line,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF5F5F7),
    surfaceContainerHigh = Color(0xFFEFEFF3),
    surfaceContainerHighest = Color(0xFFE9E9EE),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFF9ECAFF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8ECDFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A7D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF12151B),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF20242C),
    onSurfaceVariant = Color(0xFFB8BCC6),
    outline = Color(0xFF8A909B),
    outlineVariant = Color(0xFF2A2F38),
    surfaceContainerLowest = Color(0xFF090B0F),
    surfaceContainerLow = Color(0xFF15181E),
    surfaceContainer = Color(0xFF191D24),
    surfaceContainerHigh = Color(0xFF20242C),
    surfaceContainerHighest = Color(0xFF2A2F38),
    inverseSurface = Color(0xFFE6E8EE),
    inverseOnSurface = Color(0xFF2A2F38),
    inversePrimary = Color(0xFF0061A4)
)

private val JakartaTypography = Typography(
    displayLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp)
)

private val GtmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GtmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = JakartaTypography,
        shapes = GtmShapes,
        content = content
    )
}
