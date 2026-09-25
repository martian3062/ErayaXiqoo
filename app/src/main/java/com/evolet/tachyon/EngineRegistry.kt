package com.evolet.tachyon

import android.content.Context
import com.evolet.tachyon.asr.AsrEngine
import com.evolet.tachyon.asr.WhisperKitAsr
import com.evolet.tachyon.asr.WhisperServerAsr
import com.evolet.tachyon.data.AppSettings
import com.evolet.tachyon.data.Tier
import com.evolet.tachyon.llm.GenieXLlm
import com.evolet.tachyon.llm.LlamaServerLlm
import com.evolet.tachyon.llm.LlmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

enum class EngineState { IDLE, LOADING, READY, FAILED }

data class EngineHealth(val name: String = "—", val state: EngineState = EngineState.IDLE, val error: String? = null)

data class EnginesStatus(val asr: EngineHealth = EngineHealth(), val llm: EngineHealth = EngineHealth())

/**
 * Holds one engine per tier and hands out whichever Settings selects. Engines load once at app
 * start and again whenever the tier changes, so switching doesn't need a restart (F8).
 */
class EngineRegistry(
    private val context: Context,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    private val asrByTier = ConcurrentHashMap<Tier, AsrEngine>()
    private val llmByTier = ConcurrentHashMap<Tier, LlmEngine>()
    private val loadLock = Mutex()

    private val _status = MutableStateFlow(EnginesStatus())
    val status: StateFlow<EnginesStatus> = _status.asStateFlow()

    init {
        scope.launch {
            settings.state.map { it.asrTier to it.llmTier }.distinctUntilChanged().collect { warmUp() }
        }
    }

    fun asr(): AsrEngine = asrByTier.computeIfAbsent(settings.state.value.asrTier) { tier ->
        when (tier) {
            Tier.NPU -> WhisperKitAsr(context)
            Tier.FALLBACK -> WhisperServerAsr()
        }
    }

    fun llm(): LlmEngine = llmByTier.computeIfAbsent(settings.state.value.llmTier) { tier ->
        when (tier) {
            Tier.NPU -> GenieXLlm(context)
            Tier.FALLBACK -> LlamaServerLlm()
        }
    }

    /** Re-checks both engines, e.g. after starting the Termux servers. */
    fun retry() {
        scope.launch { warmUp() }
    }

    private suspend fun warmUp() = loadLock.withLock {
        coroutineScope {
            launch {
                val e = asr()
                _status.update { it.copy(asr = EngineHealth(e.name, EngineState.LOADING)) }
                val h = probe({ e.name }) { e.load() }
                _status.update { it.copy(asr = h) }
            }
            launch {
                val e = llm()
                _status.update { it.copy(llm = EngineHealth(e.name, EngineState.LOADING)) }
                val h = probe({ e.name }) { e.load() }
                _status.update { it.copy(llm = h) }
            }
        }
    }

    private suspend fun probe(name: () -> String, load: suspend () -> Unit): EngineHealth = try {
        load()
        EngineHealth(name(), EngineState.READY)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        EngineHealth(name(), EngineState.FAILED, e.message ?: e.javaClass.simpleName)
    }
}
