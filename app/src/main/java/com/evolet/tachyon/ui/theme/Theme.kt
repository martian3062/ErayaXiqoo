package com.evolet.tachyon.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
)

/** Fixed ERAYA colour system. Wallpaper colours must never dilute the product identity. */
@Composable
fun TachyonTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val signals = if (dark) Signals(Color(0xFF704116), Color(0xFFFFE1B7), Color(0xFF67E8A5))
    else Signals(Color(0xFFFFE0A6), Color(0xFF4D2900), Color(0xFF08784A))
    CompositionLocalProvider(LocalSignals provides signals) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = TachyonShapes, content = content)
    }
}

/** Edge-to-edge ERAYA atmosphere shared by every navigation destination and launch state. */
@Composable
fun ErayaBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = if (dark) {
        Brush.verticalGradient(
            listOf(Color(0xFF10080D), Color(0xFF2A0C20), Color(0xFF48112F), Color(0xFF211526)),
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFFFF5F9), Color(0xFFFFD7E5), Color(0xFFF3C5E1), Color(0xFFFFDFCB)),
        )
    }
    val glow = if (dark) Color(0x66F55C83) else Color(0x80FFFFFF)
    val accent = if (dark) Color(0x407D5CFF) else Color(0x59A989F9)

    Box(modifier.fillMaxSize().background(base)) {
        Box(
            Modifier
                .size(310.dp)
                .align(Alignment.TopEnd)
                .offset(x = 105.dp, y = (-82).dp)
                .background(Brush.radialGradient(listOf(glow, Color.Transparent)), CircleShape),
        )
        Box(
            Modifier
                .size(360.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-145).dp, y = 125.dp)
                .background(Brush.radialGradient(listOf(accent, Color.Transparent)), CircleShape),
        )
        content()
    }
}
