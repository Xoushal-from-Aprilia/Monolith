package com.monolith.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.app.R

val Grotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E4E4),
    onPrimaryContainer = Color(0xFF0A0A0A),
    secondary = Color(0xFF474747),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBEBEB),
    onSecondaryContainer = Color(0xFF141414),
    tertiary = Color(0xFF6B6B6B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = Color(0xFF1C1C1C),
    background = Color(0xFFFAFAF9),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFAFAF9),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFEEEEED),
    onSurfaceVariant = Color(0xFF5E5E5E),
    surfaceTint = Color(0xFF0A0A0A),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFF4F4F4),
    inversePrimary = Color(0xFFE0E0E0),
    error = Color(0xFF1A1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFDADADA),
    onErrorContainer = Color(0xFF0A0A0A),
    outline = Color(0xFFB8B8B8),
    outlineVariant = Color(0xFFE2E2E1),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6E6E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F4),
    surfaceContainer = Color(0xFFF0F0EF),
    surfaceContainerHigh = Color(0xFFEAEAE9),
    surfaceContainerHighest = Color(0xFFE3E3E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFF2B2B2B),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFF9A9A9A),
    onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF222222),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceTint = Color(0xFFF2F2F2),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF141414),
    inversePrimary = Color(0xFF2B2B2B),
    error = Color(0xFFEDEDED),
    onError = Color(0xFF0A0A0A),
    errorContainer = Color(0xFF333333),
    onErrorContainer = Color(0xFFF2F2F2),
    outline = Color(0xFF4D4D4D),
    outlineVariant = Color(0xFF2A2A2A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF151515),
    surfaceContainerHigh = Color(0xFF1D1D1D),
    surfaceContainerHighest = Color(0xFF262626),
)

private fun t(family: FontFamily, weight: FontWeight, size: Int, line: Int, spacing: Float = 0f) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

val MonoType = Typography(
    displayLarge = t(Grotesk, FontWeight.Bold, 56, 60, -1.5f),
    displayMedium = t(Grotesk, FontWeight.Bold, 44, 48, -1f),
    displaySmall = t(Grotesk, FontWeight.Bold, 36, 40, -0.8f),
    headlineLarge = t(Grotesk, FontWeight.Bold, 32, 38, -0.6f),
    headlineMedium = t(Grotesk, FontWeight.Bold, 28, 34, -0.4f),
    headlineSmall = t(Grotesk, FontWeight.Medium, 24, 30, -0.2f),
    titleLarge = t(Grotesk, FontWeight.Medium, 22, 28, -0.2f),
    titleMedium = t(Inter, FontWeight.SemiBold, 16, 22, 0f),
    titleSmall = t(Inter, FontWeight.SemiBold, 14, 20, 0f),
    bodyLarge = t(Inter, FontWeight.Normal, 16, 23, 0f),
    bodyMedium = t(Inter, FontWeight.Normal, 14, 20, 0f),
    bodySmall = t(Inter, FontWeight.Normal, 12, 16, 0.1f),
    labelLarge = t(Inter, FontWeight.SemiBold, 14, 20, 0.1f),
    labelMedium = t(Inter, FontWeight.Medium, 12, 16, 0.3f),
    labelSmall = t(Inter, FontWeight.Medium, 11, 14, 0.4f),
)

val MonoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Material 3 "emphasized" easing and springs used across the app */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    fun <T> bouncy() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    fun <T> smooth() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

val LocalDark = staticCompositionLocalOf { false }

/**
 * A colour theme. Monolith is the black and white original; the others tint the whole app with one accent
 * ([light] on light backgrounds, [dark] on dark ones). "dynamic" takes the colours of the wallpaper (Android 12+).
 */
data class Palette(val key: String, val name: String, val light: Long, val dark: Long)

object Palettes {
    const val MONO = "mono"
    const val DYNAMIC = "dynamic"
    val ALL = listOf(
        Palette(MONO, "Monolith" /*i18n*/, 0xFF0A0A0A, 0xFFF2F2F2),
        Palette("ocean", "Oceano" /*i18n*/, 0xFF1D5FA8, 0xFF9CC4FF),
        Palette("forest", "Foresta" /*i18n*/, 0xFF2D6A3E, 0xFF9AD5A5),
        Palette("sunset", "Tramonto" /*i18n*/, 0xFFB5481A, 0xFFFFB38E),
        Palette("lavender", "Lavanda" /*i18n*/, 0xFF6A4EB0, 0xFFCFBDFF),
        Palette("ruby", "Rubino" /*i18n*/, 0xFFAD2638, 0xFFFFB2B9),
        Palette("amber", "Ambra" /*i18n*/, 0xFF8A5A00, 0xFFF5C66A),
        Palette("mint", "Menta" /*i18n*/, 0xFF00796B, 0xFF7EDCCB),
        Palette("rose", "Rosa" /*i18n*/, 0xFFAE3A78, 0xFFFFB0D4),
        Palette("night", "Notte" /*i18n*/, 0xFF3B4AA8, 0xFFBAC4FF),
        Palette("sand", "Sabbia" /*i18n*/, 0xFF7A5B3C, 0xFFE3C3A1),
        Palette("lime", "Lime" /*i18n*/, 0xFF4F6E00, 0xFFC0E173),
    )
    val DYNAMIC_PALETTE = Palette(DYNAMIC, "Dal tuo sfondo" /*i18n*/, 0xFF5B5F72, 0xFFC3C6DD)
    fun byKey(key: String): Palette = ALL.firstOrNull { it.key == key } ?: if (key == DYNAMIC) DYNAMIC_PALETTE else ALL[0]
    val dynamicAvailable get() = android.os.Build.VERSION.SDK_INT >= 31
}

private fun mix(a: Long, b: Long, k: Float): Color {
    val ca = Color(a); val cb = Color(b)
    return Color(
        red = ca.red + (cb.red - ca.red) * k,
        green = ca.green + (cb.green - ca.green) * k,
        blue = ca.blue + (cb.blue - ca.blue) * k,
    )
}

private const val WHITE = 0xFFFFFFFF
private const val BLACK = 0xFF000000

/** a full light colour scheme tinted with one accent */
private fun tintedLight(a: Long) = lightColorScheme(
    primary = Color(a), onPrimary = Color(WHITE),
    primaryContainer = mix(a, WHITE, 0.82f), onPrimaryContainer = mix(a, BLACK, 0.55f),
    secondary = mix(a, 0xFF5E5E5E, 0.45f), onSecondary = Color(WHITE),
    secondaryContainer = mix(a, WHITE, 0.88f), onSecondaryContainer = mix(a, BLACK, 0.65f),
    tertiary = mix(a, 0xFF6B6B6B, 0.6f), onTertiary = Color(WHITE),
    tertiaryContainer = mix(a, WHITE, 0.92f), onTertiaryContainer = mix(a, BLACK, 0.7f),
    background = mix(0xFFFAFAF9, a, 0.035f), onBackground = mix(0xFF0A0A0A, a, 0.12f),
    surface = mix(0xFFFAFAF9, a, 0.035f), onSurface = mix(0xFF0A0A0A, a, 0.12f),
    surfaceVariant = mix(0xFFEEEEED, a, 0.07f), onSurfaceVariant = mix(0xFF5E5E5E, a, 0.2f),
    surfaceTint = Color(a),
    inverseSurface = mix(0xFF1A1A1A, a, 0.1f), inverseOnSurface = mix(0xFFF4F4F4, a, 0.05f), inversePrimary = mix(a, WHITE, 0.6f),
    error = Color(0xFFBA1A1A), onError = Color(WHITE), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = mix(0xFFB8B8B8, a, 0.15f), outlineVariant = mix(0xFFE2E2E1, a, 0.08f),
    scrim = Color(BLACK),
    surfaceBright = mix(WHITE, a, 0.02f), surfaceDim = mix(0xFFE6E6E5, a, 0.05f),
    surfaceContainerLowest = Color(WHITE), surfaceContainerLow = mix(0xFFF5F5F4, a, 0.04f),
    surfaceContainer = mix(0xFFF0F0EF, a, 0.055f), surfaceContainerHigh = mix(0xFFEAEAE9, a, 0.07f),
    surfaceContainerHighest = mix(0xFFE3E3E2, a, 0.085f),
)

/** a full dark colour scheme tinted with one (light) accent */
private fun tintedDark(a: Long) = darkColorScheme(
    primary = Color(a), onPrimary = mix(a, BLACK, 0.8f),
    primaryContainer = mix(a, 0xFF0A0A0A, 0.72f), onPrimaryContainer = mix(a, WHITE, 0.55f),
    secondary = mix(a, 0xFFBDBDBD, 0.45f), onSecondary = mix(a, BLACK, 0.85f),
    secondaryContainer = mix(a, 0xFF0A0A0A, 0.8f), onSecondaryContainer = mix(a, WHITE, 0.7f),
    tertiary = mix(a, 0xFF9A9A9A, 0.55f), onTertiary = mix(a, BLACK, 0.85f),
    tertiaryContainer = mix(a, 0xFF0A0A0A, 0.85f), onTertiaryContainer = mix(a, WHITE, 0.7f),
    background = mix(0xFF0A0A0A, a, 0.04f), onBackground = mix(0xFFF2F2F2, a, 0.08f),
    surface = mix(0xFF0A0A0A, a, 0.04f), onSurface = mix(0xFFF2F2F2, a, 0.08f),
    surfaceVariant = mix(0xFF1F1F1F, a, 0.07f), onSurfaceVariant = mix(0xFFA3A3A3, a, 0.2f),
    surfaceTint = Color(a),
    inverseSurface = mix(0xFFEDEDED, a, 0.08f), inverseOnSurface = mix(0xFF141414, a, 0.05f), inversePrimary = mix(a, BLACK, 0.55f),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = mix(0xFF4D4D4D, a, 0.15f), outlineVariant = mix(0xFF2A2A2A, a, 0.08f),
    scrim = Color(BLACK),
    surfaceBright = mix(0xFF2A2A2A, a, 0.07f), surfaceDim = mix(0xFF0A0A0A, a, 0.04f),
    surfaceContainerLowest = mix(0xFF050505, a, 0.02f), surfaceContainerLow = mix(0xFF111111, a, 0.04f),
    surfaceContainer = mix(0xFF151515, a, 0.055f), surfaceContainerHigh = mix(0xFF1D1D1D, a, 0.07f),
    surfaceContainerHighest = mix(0xFF262626, a, 0.085f),
)

/** the colour scheme of a theme, light or dark */
fun schemeFor(ctx: android.content.Context, key: String, dark: Boolean): androidx.compose.material3.ColorScheme {
    if (key == Palettes.DYNAMIC && Palettes.dynamicAvailable) {
        return if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx) else androidx.compose.material3.dynamicLightColorScheme(ctx)
    }
    val p = Palettes.byKey(key)
    if (p.key == Palettes.MONO || p.key == Palettes.DYNAMIC) return if (dark) DarkColors else LightColors
    return if (dark) tintedDark(p.dark) else tintedLight(p.light)
}

@Composable
fun MonolithTheme(mode: Int, palette: String = Palettes.MONO, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scheme = androidx.compose.runtime.remember(palette, dark) { schemeFor(ctx, palette, dark) }
    androidx.compose.runtime.CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MonoType,
            shapes = MonoShapes,
            content = content,
        )
    }
}
