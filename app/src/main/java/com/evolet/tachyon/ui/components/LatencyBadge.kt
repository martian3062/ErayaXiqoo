package com.evolet.tachyon.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/** F7: "ASR 2.1 s · LLM 3.4 s · 18 tok/s · llama.cpp·CPU". Real measured numbers only. */
@Composable
fun LatencyBadge(
    asrMs: Long,
    llmMs: Long,
    tokensPerSec: Double?,
    engines: List<String?>,
    modifier: Modifier = Modifier,
) {
    val parts = buildList {
        if (asrMs > 0) add("ASR ${seconds(asrMs)}")
        if (llmMs > 0) add("LLM ${seconds(llmMs)}")
        tokensPerSec?.let { add(String.format(Locale.US, "%.0f tok/s", it)) }
        engines.filterNotNull().distinct().forEach { add(it) }
    }
    if (parts.isEmpty()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun seconds(ms: Long) = String.format(Locale.US, "%.1f s", ms / 1000.0)
