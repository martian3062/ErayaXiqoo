package com.evolet.tachyon.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val Light = lightColorScheme(
    primary = Color(0xFF9D174D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E5),
    onPrimaryContainer = Color(0xFF3B001C),
    secondary = Color(0xFF805162),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF32101E),
    tertiary = Color(0xFF7D5637),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFFFF7FA),
    onBackground = Color(0xFF27181E),
    surface = Color(0xFFFFF9FB),
    onSurface = Color(0xFF27181E),
    surfaceVariant = Color(0xFFF3DDE5),
    onSurfaceVariant = Color(0xFF584049),
    surfaceContainer = Color(0xF2FFF7FA),
    surfaceContainerHigh = Color(0xF7FFEFF5),
    surfaceContainerLow = Color(0xE6FFFBFC),
    outline = Color(0xFF8C707A),
    outlineVariant = Color(0xFFDCC1CA),
    error = Color(0xFFB91C1C),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB0C8),
    onPrimary = Color(0xFF5F0A31),
    primaryContainer = Color(0xFF7E2047),
    onPrimaryContainer = Color(0xFFFFD8E5),
    secondary = Color(0xFFF1B7CA),
    onSecondary = Color(0xFF4A2533),
    secondaryContainer = Color(0xFF633B4B),
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4A2808),
    tertiaryContainer = Color(0xFF633F20),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF160E12),
    onBackground = Color(0xFFF5DCE5),
    surface = Color(0xFF201519),
    onSurface = Color(0xFFF5DCE5),
    surfaceVariant = Color(0xFF57404A),
    onSurfaceVariant = Color(0xFFDBC0CA),
    surfaceContainer = Color(0xE62D1C23),
    surfaceContainerHigh = Color(0xF23A242D),
    surfaceContainerLow = Color(0xD924171C),
    outline = Color(0xFFA88A95),
    outlineVariant = Color(0xFF57404A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Semantic colours Material doesn't have: the amber "Tight for you" risk chip. */
@Immutable
data class Signals(val risk: Color, val onRisk: Color, val ok: Color)

val LocalSignals = staticCompositionLocalOf { Signals(Color(0xFFFDE68A), Color(0xFF451A03), Color(0xFF15803D)) }

private val TachyonShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

private val TachyonTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 37.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 32.sp, letterSpacing = (-0.35).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp),
)

/** Fixed ERAYA colour system. Wallpaper colours must never dilute the product identity. */
@Composable
fun TachyonTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val signals = if (dark) Signals(Color(0xFF704116), Color(0xFFFFE1B7), Color(0xFF67E8A5))
    else Signals(Color(0xFFFFE0A6), Color(0xFF4D2900), Color(0xFF08784A))
    CompositionLocalProvider(LocalSignals provides signals) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = TachyonShapes,
            typography = TachyonTypography,
            content = content,
        )
    }
}

/**
 * Edge-to-edge animated ERAYA mesh. The motion is deliberately slow so the interface feels alive
 * without competing with recording, review or accessibility focus.
 */
@Composable
fun ErayaBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val motion = rememberInfiniteTransition(label = "eraya-mesh")
    val phase = motion.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(20_000), RepeatMode.Restart),
        label = "mesh-phase",
    ).value
    val breathe = motion.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(8_000), RepeatMode.Reverse),
        label = "mesh-breathe",
    ).value

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val base = if (dark) {
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0D0710), Color(0xFF24101F), Color(0xFF180F25)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF8FB), Color(0xFFFFEDF4), Color(0xFFF2EDFF)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            }
            drawRect(base)

            fun glow(center: Offset, radius: Float, color: Color) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = color.alpha * 0.28f), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            val pink = if (dark) Color(0x66FF3F8E) else Color(0x70FF7FB0)
            val violet = if (dark) Color(0x554D37FF) else Color(0x667E6BFF)
            val peach = if (dark) Color(0x44FF8C58) else Color(0x70FFC39F)
            glow(
                Offset(size.width * (0.78f + 0.12f * cos(phase)), size.height * (0.12f + 0.08f * sin(phase))),
                size.minDimension * 0.56f * breathe,
                pink,
            )
            glow(
                Offset(size.width * (0.12f + 0.10f * sin(phase * 0.72f)), size.height * (0.72f + 0.12f * cos(phase * 0.72f))),
                size.minDimension * 0.62f,
                violet,
            )
            glow(
                Offset(size.width * (0.76f + 0.08f * cos(phase * 1.2f)), size.height * (0.86f + 0.08f * sin(phase * 1.2f))),
                size.minDimension * 0.48f,
                peach,
            )
        }
        content()
    }
}
