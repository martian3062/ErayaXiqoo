package com.evolet.tachyon.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evolet.tachyon.session.Phase
import com.evolet.tachyon.ui.theme.LocalSignals
import kotlin.math.abs
import kotlin.math.roundToInt

/** Initials in a colour derived from the name, so the same person always looks the same. */
@Composable
fun Avatar(name: String, isYou: Boolean = false, size: Int = 28) {
    val palette = listOf(0xFF0EA5E9, 0xFF8B5CF6, 0xFFF59E0B, 0xFF10B981, 0xFFEC4899, 0xFF6366F1).map { Color(it) }
    val bg = if (isYou) MaterialTheme.colorScheme.primary else palette[abs(name.hashCode()) % palette.size]
    val initials = if (isYou) "You" else name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(Modifier.size(size.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontSize = (size * if (isYou) 0.32f else 0.42f).sp, fontWeight = FontWeight.Bold)
    }
}

/** "You → Alex" with avatars. */
@Composable
fun PeopleRow(owner: String, ownerIsYou: Boolean, to: String?, relation: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Avatar(owner, ownerIsYou)
        Text(if (ownerIsYou) "You" else owner, style = MaterialTheme.typography.labelLarge)
        if (to != null) {
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Avatar(to)
            Text(to, style = MaterialTheme.typography.labelLarge)
            if (!relation.isNullOrBlank()) Text("· $relation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Amber "Tight for you" chip (F13 RiskScorer). */
@Composable
fun RiskChip(note: String) {
    val s = LocalSignals.current
    Surface(color = s.risk, contentColor = s.onRisk, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(TachyonIcons.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(note, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Small ring showing model confidence. */
@Composable
fun ConfidenceRing(confidence: Double) {
    val p by animateFloatAsState(confidence.toFloat().coerceIn(0f, 1f), label = "conf")
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { p }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Text("${(confidence * 100).roundToInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** Listen → Transcribe → Extract → Verify, with the active step lit. */
@Composable
fun PipelineStepper(phase: Phase, modifier: Modifier = Modifier) {
    val steps = listOf("Listen", "Transcribe", "Extract", "Review")
    val active = when (phase) {
        Phase.RECORDING -> 0
        Phase.TRANSCRIBING -> 1
        Phase.EXTRACTING -> 2
        Phase.REVIEW -> 3
        else -> -1
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, label ->
            val on = i == active
            val done = active > i
            val bg by animateColorAsState(
                when {
                    on -> MaterialTheme.colorScheme.primary
                    done -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }, label = "step",
            )
            val fg = when {
                on -> MaterialTheme.colorScheme.onPrimary
                done -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
                Text((if (done) "✓ " else "") + label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}
