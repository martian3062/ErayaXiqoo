package com.evolet.tachyon.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.net.NetState

/**
 * F7: "OFFLINE · NPU" during the demo. [compute] reflects the engines actually selected
 * (NPU, CPU or NPU+CPU). ONLINE shows as a warning, because the demo should run in airplane mode.
 */
@Composable
fun OfflineBadge(net: NetState, compute: String, modifier: Modifier = Modifier) {
    val offline = !net.online
    val label = buildString {
        append(if (offline) "OFFLINE" else "ONLINE")
        append(" · ").append(compute)
        if (net.airplane) append(" · ✈")
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (offline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (offline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
