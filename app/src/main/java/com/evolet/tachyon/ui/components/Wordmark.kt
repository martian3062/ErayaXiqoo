package com.evolet.tachyon.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** ERAYA brand gradient, sampled from the eraya wordmark: blush -> pink -> hot pink. */
val ErayaGradient = Brush.linearGradient(listOf(Color(0xFFFFE0E2), Color(0xFFFF96AE), Color(0xFFF55C83)))
val ErayaHot = Color(0xFFF55C83)

/** Light surfaces: the blush end would vanish on white, so shift one step darker. */
val ErayaGradientOnLight = Brush.linearGradient(listOf(Color(0xFFFF96AE), Color(0xFFF55C83), Color(0xFFB0305E)))

/** App title in the ERAYA gradient with the "built on eraya" line. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "tachyon",
            style = MaterialTheme.typography.headlineSmall.copy(brush = if (isSystemInDarkTheme()) ErayaGradient else ErayaGradientOnLight),
            fontWeight = FontWeight.ExtraBold,
        )
        Text(": built on eraya", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
