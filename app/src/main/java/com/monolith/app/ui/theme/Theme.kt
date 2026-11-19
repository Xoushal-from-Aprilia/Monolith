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

@Composable
fun MonolithTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = MonoType,
            shapes = MonoShapes,
            content = content,
        )
    }
}
