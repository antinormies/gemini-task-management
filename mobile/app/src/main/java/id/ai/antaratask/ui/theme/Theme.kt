package id.ai.antaratask.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.ai.antaratask.R

/**
 * Numo-derived design system. Values mirror `.agents/design/tokens.json`; that file is
 * the source of truth and `python3 .agents/design/tools/check_contrast.py` gates every
 * colour pair declared here. Do not add a colour without adding its pair there.
 */
@Immutable
data class NumoColors(
    val isDark: Boolean,
    val brand: Color,
    val brandStrong: Color,
    val brandSoft: Color,
    val onBrand: Color,
    val canvas: Color,
    val surface: Color,
    val surfaceWarm: Color,
    val surfaceWarmSoft: Color,
    val ink: Color,
    val inkBody: Color,
    val muted: Color,
    val mutedSoft: Color,
    val line: Color,
    val lineStrong: Color,
    val success: Color,
    val successStrong: Color,
    val successSoft: Color,
    val onSuccess: Color,
    val danger: Color,
    val dangerFill: Color,
    val dangerStrong: Color,
    val dangerSoft: Color,
    val onDanger: Color,
    val warning: Color,
    val warningSoft: Color,
    val onWarning: Color,
    val magenta: Color,
    val magentaSoft: Color,
    val onMagenta: Color,
    val scrim: Color
)

val LightNumoColors = NumoColors(
    isDark = false,
    brand = Color(0xFF0067FF),
    brandStrong = Color(0xFF0054CC),
    brandSoft = Color(0xFFE8F1FF),
    onBrand = Color(0xFFFFFFFF),
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFDFE),
    surfaceWarm = Color(0xFFFFF2DB),
    surfaceWarmSoft = Color(0xFFFFF9EE),
    ink = Color(0xFF000000),
    inkBody = Color(0xFF2F2F2F),
    muted = Color(0xFF6B6B70),
    mutedSoft = Color(0xFF8A8A90),
    line = Color(0xFFEFEFF2),
    lineStrong = Color(0xFFE2E2E7),
    success = Color(0xFF01C459),
    successStrong = Color(0xFF067237),
    successSoft = Color(0xFFD7F5E4),
    onSuccess = Color(0xFF000000),
    danger = Color(0xFFFF3E32),
    dangerFill = Color(0xFFD92B20),
    dangerStrong = Color(0xFFC62828),
    dangerSoft = Color(0xFFFFEBEB),
    onDanger = Color(0xFFFFFFFF),
    warning = Color(0xFFFF9901),
    warningSoft = Color(0xFFFFF1D8),
    onWarning = Color(0xFF000000),
    magenta = Color(0xFFF549C8),
    magentaSoft = Color(0xFFFFE9F8),
    onMagenta = Color(0xFF000000),
    scrim = Color(0x8C000000)
)

val DarkNumoColors = NumoColors(
    isDark = true,
    brand = Color(0xFF3D8BFF),
    brandStrong = Color(0xFF7FB4FF),
    brandSoft = Color(0xFF16243A),
    onBrand = Color(0xFF0B0B0F),
    canvas = Color(0xFF0B0B0F),
    surface = Color(0xFF15161B),
    surfaceWarm = Color(0xFF241E14),
    surfaceWarmSoft = Color(0xFF1C1811),
    ink = Color(0xFFFFFFFF),
    inkBody = Color(0xFFE8E8EA),
    muted = Color(0xFF9A9AA2),
    mutedSoft = Color(0xFF75757C),
    line = Color(0xFF24252C),
    lineStrong = Color(0xFF2F3038),
    success = Color(0xFF34D97B),
    successStrong = Color(0xFF34D97B),
    successSoft = Color(0xFF17301F),
    onSuccess = Color(0xFF0B0B0F),
    danger = Color(0xFFFF6B61),
    dangerFill = Color(0xFFFF6B61),
    dangerStrong = Color(0xFFFF8A80),
    dangerSoft = Color(0xFF33191A),
    onDanger = Color(0xFF0B0B0F),
    warning = Color(0xFFFFB340),
    warningSoft = Color(0xFF34260F),
    onWarning = Color(0xFF0B0B0F),
    magenta = Color(0xFFFF6BD8),
    magentaSoft = Color(0xFF33122A),
    onMagenta = Color(0xFF0B0B0F),
    scrim = Color(0xB3000000)
)

private val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold)
)

@Immutable
data class NumoTypography(
    val displayXL: TextStyle,
    val displayL: TextStyle,
    val displayM: TextStyle,
    val titleL: TextStyle,
    val titleM: TextStyle,
    val bodyL: TextStyle,
    val bodyM: TextStyle,
    val meta: TextStyle,
    val label: TextStyle,
    val numberXL: TextStyle,
    val numberL: TextStyle
)

private fun style(
    size: Int,
    line: Int,
    tracking: Float,
    weight: FontWeight,
    tabular: Boolean = false
) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) "tnum" else null
)

val NumoType = NumoTypography(
    displayXL = style(40, 44, -1.6f, FontWeight.ExtraBold),
    displayL = style(34, 38, -1.2f, FontWeight.ExtraBold),
    displayM = style(28, 32, -0.9f, FontWeight.ExtraBold),
    titleL = style(22, 28, -0.5f, FontWeight.ExtraBold),
    titleM = style(18, 24, -0.3f, FontWeight.Bold),
    bodyL = style(17, 24, 0f, FontWeight.Medium),
    bodyM = style(15, 21, 0f, FontWeight.Medium),
    meta = style(13, 18, 0f, FontWeight.SemiBold, tabular = true),
    label = style(12, 16, 0.4f, FontWeight.Bold),
    numberXL = style(44, 48, -1.5f, FontWeight.ExtraBold, tabular = true),
    numberL = style(32, 36, -1f, FontWeight.ExtraBold, tabular = true)
)

@Immutable
data class NumoSpace(
    val gutter: androidx.compose.ui.unit.Dp = 18.dp,
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 12.dp,
    val lg: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 20.dp,
    val xxl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxxl: androidx.compose.ui.unit.Dp = 32.dp,
    val huge: androidx.compose.ui.unit.Dp = 40.dp,
    val rowGap: androidx.compose.ui.unit.Dp = 10.dp,
    val cardGap: androidx.compose.ui.unit.Dp = 8.dp,
    val sectionGap: androidx.compose.ui.unit.Dp = 24.dp
)

@Immutable
data class NumoRadii(
    val control: RoundedCornerShape = RoundedCornerShape(12.dp),
    val row: RoundedCornerShape = RoundedCornerShape(20.dp),
    val card: RoundedCornerShape = RoundedCornerShape(28.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(28.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50)
)

@Immutable
data class NumoMotion(
    val quick: Int = 120,
    val base: Int = 200,
    val slow: Int = 280,
    val pulse: Int = 1200,
    val dots: Int = 900,
    val easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
)

@Immutable
data class NumoSizes(
    val rowHeight: androidx.compose.ui.unit.Dp = 72.dp,
    val checkbox: androidx.compose.ui.unit.Dp = 26.dp,
    val orb: androidx.compose.ui.unit.Dp = 150.dp,
    val orbCore: androidx.compose.ui.unit.Dp = 96.dp,
    val orbGlyph: androidx.compose.ui.unit.Dp = 56.dp,
    val micFab: androidx.compose.ui.unit.Dp = 48.dp,
    val addFab: androidx.compose.ui.unit.Dp = 60.dp,
    val submitFab: androidx.compose.ui.unit.Dp = 56.dp,
    val bottomBar: androidx.compose.ui.unit.Dp = 68.dp,
    val headerMin: androidx.compose.ui.unit.Dp = 128.dp,
    val chipHeight: androidx.compose.ui.unit.Dp = 36.dp,
    val controlHeight: androidx.compose.ui.unit.Dp = 52.dp,
    val minTouch: androidx.compose.ui.unit.Dp = 44.dp,
    val avatar: androidx.compose.ui.unit.Dp = 40.dp,
    val streak: androidx.compose.ui.unit.Dp = 34.dp,
    val iconSm: androidx.compose.ui.unit.Dp = 18.dp,
    val iconMd: androidx.compose.ui.unit.Dp = 22.dp,
    val iconLg: androidx.compose.ui.unit.Dp = 26.dp
)

private val LocalNumoColors = staticCompositionLocalOf { LightNumoColors }
private val LocalNumoType = staticCompositionLocalOf { NumoType }
private val LocalNumoSpace = staticCompositionLocalOf { NumoSpace() }
private val LocalNumoRadii = staticCompositionLocalOf { NumoRadii() }
private val LocalNumoMotion = staticCompositionLocalOf { NumoMotion() }
private val LocalNumoSizes = staticCompositionLocalOf { NumoSizes() }
private val LocalNumoReducedMotion = staticCompositionLocalOf { false }

/** Token accessors. Screens must read these instead of raw hex/dp values. */
object NumoTheme {
    val colors: NumoColors
        @Composable get() = LocalNumoColors.current
    val type: NumoTypography
        @Composable get() = LocalNumoType.current
    val space: NumoSpace
        @Composable get() = LocalNumoSpace.current
    val radius: NumoRadii
        @Composable get() = LocalNumoRadii.current
    val motion: NumoMotion
        @Composable get() = LocalNumoMotion.current
    val size: NumoSizes
        @Composable get() = LocalNumoSizes.current
    val reducedMotion: Boolean
        @Composable get() = LocalNumoReducedMotion.current
}

private fun androidColorScheme(c: NumoColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.brand,
        onPrimary = c.onBrand,
        primaryContainer = c.brandSoft,
        onPrimaryContainer = c.brandStrong,
        secondary = c.success,
        onSecondary = c.onSuccess,
        tertiary = c.magenta,
        onTertiary = c.onMagenta,
        error = c.dangerFill,
        onError = c.onDanger,
        errorContainer = c.dangerSoft,
        onErrorContainer = c.dangerStrong,
        background = c.canvas,
        onBackground = c.inkBody,
        surface = c.surface,
        onSurface = c.inkBody,
        surfaceVariant = c.surfaceWarm,
        onSurfaceVariant = c.muted,
        outline = c.lineStrong,
        outlineVariant = c.line,
        scrim = c.scrim
    )
} else {
    lightColorScheme(
        primary = c.brand,
        onPrimary = c.onBrand,
        primaryContainer = c.brandSoft,
        onPrimaryContainer = c.brandStrong,
        secondary = c.success,
        onSecondary = c.onSuccess,
        tertiary = c.magenta,
        onTertiary = c.onMagenta,
        error = c.dangerFill,
        onError = c.onDanger,
        errorContainer = c.dangerSoft,
        onErrorContainer = c.dangerStrong,
        background = c.canvas,
        onBackground = c.inkBody,
        surface = c.surface,
        onSurface = c.inkBody,
        surfaceVariant = c.surfaceWarm,
        onSurfaceVariant = c.muted,
        outline = c.lineStrong,
        outlineVariant = c.line,
        scrim = c.scrim
    )
}

private fun materialType(t: NumoTypography) = Typography(
    displayLarge = t.displayXL,
    displayMedium = t.displayL,
    displaySmall = t.displayM,
    headlineLarge = t.displayM,
    headlineMedium = t.titleL,
    headlineSmall = t.titleM,
    titleLarge = t.titleL,
    titleMedium = t.titleM,
    titleSmall = t.bodyM,
    bodyLarge = t.bodyL,
    bodyMedium = t.bodyM,
    bodySmall = t.meta,
    labelLarge = t.titleM,
    labelMedium = t.label,
    labelSmall = t.label
)

private val NumoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Installs the token set. [reducedMotion] defaults to following the platform animator
 * scale so a device with animations disabled stops all decorative loops.
 */
@Composable
fun NumoThemeProvider(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkNumoColors else LightNumoColors
    val reduced = reducedMotion ?: !systemAnimationsEnabled()
    CompositionLocalProvider(
        LocalNumoColors provides colors,
        LocalNumoType provides NumoType,
        LocalNumoSpace provides NumoSpace(),
        LocalNumoRadii provides NumoRadii(),
        LocalNumoMotion provides NumoMotion(),
        LocalNumoSizes provides NumoSizes(),
        LocalNumoReducedMotion provides reduced
    ) {
        MaterialTheme(
            colorScheme = androidColorScheme(colors),
            typography = materialType(NumoType),
            shapes = NumoShapes,
            content = content
        )
    }
}

/**
 * Reads the platform "Animator duration scale". When it is 0 (Developer options or
 * accessibility "remove animations"), every decorative loop in the app renders static.
 * Kept as a plain read because Compose's MotionDurationScale local is not present in
 * this Compose build.
 */
@Composable
private fun systemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val scale = remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
    }
    return scale > 0f
}

/**
 * Legacy palette bridge. The pre-Numo screens still reference these names; every value
 * now resolves to a Numo token so nothing renders with the retired lavender stage.
 * Delete this object once no screen references it (tracked in design.md §9).
 */
@Deprecated("Use NumoTheme.colors; this bridge is removed once all screens are migrated.")
object GtmColors {
    val speakBgTop: Color = LightNumoColors.surfaceWarmSoft
    val speakBg: Color = LightNumoColors.canvas
    val speakBgBottom: Color = LightNumoColors.canvas
    val speakInk: Color = LightNumoColors.ink
    val speakMuted: Color = LightNumoColors.muted
    val speakAccent: Color = LightNumoColors.brand
    val speakAccentSoft: Color = LightNumoColors.brandSoft
    val speakLine: Color = LightNumoColors.line
    val orbPink: Color = LightNumoColors.magenta
    val orbBlue: Color = LightNumoColors.brand
    val orbViolet: Color = LightNumoColors.brandStrong
    val orbLavender: Color = LightNumoColors.magentaSoft
    val orbMint: Color = LightNumoColors.success
}
