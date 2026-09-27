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

/** Product wordmark. Internal package names remain stable so updates preserve user data. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "ERAYA",
            style = MaterialTheme.typography.headlineSmall.copy(brush = if (isSystemInDarkTheme()) ErayaGradient else ErayaGradientOnLight),
            fontWeight = FontWeight.ExtraBold,
        )
        Text("your private intelligence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
