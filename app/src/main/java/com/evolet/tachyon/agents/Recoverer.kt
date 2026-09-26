package com.evolet.tachyon.agents

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Recoverer: when an engine degrades, re-probe the engines so the status strip shows the truth
 * (✕ + "Open Termux") instead of a stale ● ready. At most one re-probe per [minGapMs].
 */
class Recoverer(
    private val bus: AgentBus,
    private val reprobe: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val minGapMs: Long = 5_000,
) {
    private var last = 0L

    fun start(scope: CoroutineScope) {
        scope.launch {
            bus.events.filterIsInstance<AgentEvent.EngineDegraded>().collect { e ->
                if (onDegraded(e)) Log.w(TAG, "${e.engine} degraded: ${e.reason} → re-probing engines")
            }
        }
    }

    /** Returns true when a re-probe was triggered. */
    internal fun onDegraded(@Suppress("UNUSED_PARAMETER") e: AgentEvent.EngineDegraded): Boolean {
        val t = now()
        if (t - last < minGapMs) return false
        last = t
        reprobe()
        return true
    }

    private companion object {
        const val TAG = "Recoverer"
    }
}
