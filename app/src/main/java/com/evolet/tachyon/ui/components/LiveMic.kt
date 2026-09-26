package com.evolet.tachyon.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The record button. While recording, rings pulse outwards and swell with the live mic [level].
 */
@Composable
fun PulseMicButton(
    recording: Boolean,
    enabled: Boolean,
    level: Float,
    onClick: () -> Unit,
    size: Dp = 128.dp,
) {
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(
        if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, tween(300), label = "mic",
    )
    val ringColor = container
    val smooth by animateFloatAsState(level, spring(stiffness = 300f), label = "level")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "t")

    Box(Modifier.size(size * 1.9f), contentAlignment = Alignment.Center) {
        if (recording) {
            Canvas(Modifier.fillMaxSize()) {
                val base = size.toPx() / 2
                for (k in 0..1) {
                    val p = (t + k * 0.5f) % 1f
                    drawCircle(ringColor.copy(alpha = (1 - p) * 0.35f), radius = base * (1f + p * 0.8f), style = Stroke(3.dp.toPx()))
                }
                drawCircle(ringColor.copy(alpha = 0.18f), radius = base * (1f + smooth * 0.55f))
            }
        }
        FilledIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier.size(size).semantics { contentDescription = if (recording) "Stop recording" else "Start recording" },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container),
        ) {
            Icon(if (recording) TachyonIcons.Stop else TachyonIcons.Mic, contentDescription = null, modifier = Modifier.size(size * 0.42f))
        }
    }
}

/** Scrolling bar waveform of the last ~5 s of mic loudness. */
@Composable
fun LiveWaveform(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val bars = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
    LaunchedEffect(level, active) {
        bars.removeAt(0)
        bars.add(if (active) level else 0f)
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(48.dp)) {
        val w = this.size.width / BARS
        val mid = this.size.height / 2
        bars.forEachIndexed { i, v ->
            val h = (4.dp.toPx() + v * this.size.height).coerceAtMost(this.size.height)
            drawRoundRect(
                color.copy(alpha = 0.35f + 0.65f * (i / BARS.toFloat())),
                topLeft = Offset(i * w + w * 0.2f, mid - h / 2),
                size = Size(w * 0.6f, h),
                cornerRadius = CornerRadius(w, w),
            )
        }
    }
}

private const val BARS = 48
