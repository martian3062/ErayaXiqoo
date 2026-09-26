package com.evolet.tachyon.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.R
import com.evolet.tachyon.ui.theme.ErayaBackdrop

/** Branded Compose hand-off shown while the two private, on-device engines warm up. */
@Composable
fun BrandLaunchScreen() {
    val motion = rememberInfiniteTransition(label = "launch-motion")
    val rotation = motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500)),
        label = "launch-ring",
    ).value
    val pulse = motion.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "launch-pulse",
    ).value

    ErayaBackdrop {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(154.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    rotate(rotation) {
                        drawArc(
                            brush = ErayaGradient,
                            startAngle = 18f,
                            sweepAngle = 278f,
                            useCenter = false,
                            topLeft = Offset(7.dp.toPx(), 7.dp.toPx()),
                            size = size.copy(width = size.width - 14.dp.toPx(), height = size.height - 14.dp.toPx()),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                Box(
                    Modifier
                        .size(116.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .background(Color(0xFF1D0718), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "ERAYA",
                        modifier = Modifier.size(108.dp),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "tachyon",
                style = MaterialTheme.typography.displaySmall.copy(brush = ErayaGradientOnLight),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "built on eraya",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Preparing your private AI workspace",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
