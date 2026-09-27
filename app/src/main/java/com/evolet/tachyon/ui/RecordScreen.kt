package com.evolet.tachyon.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.evolet.tachyon.EngineHealth
import com.evolet.tachyon.EngineState
import com.evolet.tachyon.EnginesStatus
import com.evolet.tachyon.conversation.ConversationMessage
import com.evolet.tachyon.conversation.ConversationRole
import com.evolet.tachyon.conversation.ReplicaConversationPhase
import com.evolet.tachyon.conversation.ReplicaConversationUi
import com.evolet.tachyon.session.Phase
import com.evolet.tachyon.session.SessionUi
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.twin.RecallSource
import com.evolet.tachyon.twin.ReplicaSnapshot
import com.evolet.tachyon.ui.components.ErayaActionGradient
import com.evolet.tachyon.ui.components.GlassCard
import com.evolet.tachyon.ui.components.LatencyBadge
import com.evolet.tachyon.ui.components.LiveWaveform
import com.evolet.tachyon.ui.components.PulseMicButton
import com.evolet.tachyon.ui.components.ReplicaPortraitPreview
import com.evolet.tachyon.ui.components.StatusPill
import com.evolet.tachyon.ui.components.TachyonIcons
import java.io.File

/** Private conversational room backed by the local ASR, LLM, TTS, and animated replica. */
@Composable
fun RecordScreen(
    ui: ReplicaConversationUi,
    taskUi: SessionUi,
    level: Float,
    engines: EnginesStatus,
    replica: ReplicaSnapshot,
    portraitFile: File,
    talkingPortraitFile: File,
    blinkPortraitFile: File,
    lookLeftPortraitFile: File,
    lookRightPortraitFile: File,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSubmitText: (String) -> Unit,
    onOpenReplica: () -> Unit,
    onRetryConversation: () -> Unit,
    onStopSpeaking: () -> Unit,
    onClearConversation: () -> Unit,
    onExtractTasks: (String) -> Unit,
    onRetryTaskExtraction: () -> Unit,
    onOpenRecallSource: (String) -> Unit,
    onRetryEngines: () -> Unit,
    onOpenTermux: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(InputMode.PORTRAIT) }
    var typedText by rememberSaveable { mutableStateOf("") }
    val recording = ui.phase == ReplicaConversationPhase.LISTENING
    val speaking = ui.phase == ReplicaConversationPhase.SPEAKING
    val canInput = !ui.busy || recording

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EngineStrip(engines, onRetryEngines, onOpenTermux)
        Spacer(Modifier.height(10.dp))
        PortraitRoomCard(
            replica = replica,
            portraitFile = portraitFile,
            talkingPortraitFile = talkingPortraitFile,
            blinkPortraitFile = blinkPortraitFile,
            lookLeftPortraitFile = lookLeftPortraitFile,
            lookRightPortraitFile = lookRightPortraitFile,
            roomActive = recording && mode == InputMode.PORTRAIT,
            phase = ui.phase,
            audioLevel = level,
            enabled = canInput || speaking,
            onToggleRoom = {
                mode = InputMode.PORTRAIT
                when {
                    recording -> onStop()
                    speaking -> {
                        onStopSpeaking()
                        onStart()
                    }
                    else -> onStart()
                }
            },
            onOpenReplica = onOpenReplica,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InputModeAction(
                label = "Chat",
                supporting = "Type privately",
                icon = TachyonIcons.Chat,
                selected = mode == InputMode.CHAT,
                enabled = !ui.busy,
                modifier = Modifier.weight(1f),
            ) { mode = InputMode.CHAT }
            InputModeAction(
                label = "Voice",
                supporting = when {
                    recording -> "Listening now"
                    speaking -> "ERAYA is speaking"
                    else -> "Speak naturally"
                },
                icon = TachyonIcons.Mic,
                selected = mode == InputMode.VOICE,
                enabled = canInput || speaking,
                modifier = Modifier.weight(1f),
            ) { mode = InputMode.VOICE }
        }

        AnimatedContent(mode, label = "room-input-mode") { activeMode ->
            when (activeMode) {
                InputMode.CHAT -> ChatComposer(
                    text = typedText,
                    enabled = !ui.busy,
                    onTextChange = { typedText = it },
                    onSend = {
                        val clean = typedText.trim()
                        if (clean.isNotEmpty() && !ui.busy) {
                            typedText = ""
                            onSubmitText(clean)
                        }
                    },
                )
                InputMode.VOICE -> VoiceControls(
                    recording = recording,
                    speaking = speaking,
                    enabled = canInput || speaking,
                    level = level,
                    onToggle = {
                        when {
                            recording -> onStop()
                            speaking -> {
                                onStopSpeaking()
                                onStart()
                            }
                            else -> onStart()
                        }
                    },
                )
                InputMode.PORTRAIT -> if (recording) {
                    LiveWaveform(level, true, Modifier.padding(horizontal = 24.dp))
                } else {
                    Text(
                        "Your animated portrait stays on this phone. Choose Chat or Voice below at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                    )
                }
            }
        }

        TaskExtractionStatus(taskUi, onRetryTaskExtraction)

        AnimatedContent(statusLine(ui), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { line ->
            Text(line, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        AnimatedVisibility(
            ui.phase == ReplicaConversationPhase.TRANSCRIBING ||
                ui.phase == ReplicaConversationPhase.RECALLING ||
                ui.phase == ReplicaConversationPhase.THINKING ||
                ui.pendingChunks > 0,
        ) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        if (ui.phase == ReplicaConversationPhase.ERROR) {
            Spacer(Modifier.height(8.dp))
            Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Button(onClick = onRetryConversation) { Text("Retry reply") }
        } else if (ui.error != null) {
            Text(ui.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(8.dp))
        LatencyBadge(ui.asrMs, ui.llmMs, ui.tokensPerSec, listOf(ui.asrEngine, ui.llmEngine))
        Spacer(Modifier.height(8.dp))
        ConversationCard(
            messages = ui.messages,
            liveTranscript = ui.liveTranscript,
            onExtractTasks = onExtractTasks,
            onOpenRecallSource = onOpenRecallSource,
            onClear = onClearConversation,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TaskExtractionStatus(ui: SessionUi, onRetry: () -> Unit) {
    AnimatedVisibility(ui.phase == Phase.EXTRACTING || ui.phase == Phase.TRANSCRIBING || ui.phase == Phase.ERROR) {
        GlassCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    when (ui.phase) {
                        Phase.TRANSCRIBING -> "Finishing private transcription…"
                        Phase.EXTRACTING -> "Finding guarded commitment proposals…"
                        Phase.ERROR -> "Proposal extraction paused"
                        else -> "Preparing proposals…"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (ui.phase == Phase.ERROR) {
                    Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (ui.canRetry) Button(onClick = onRetry) { Text("Retry proposals") }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "Nothing is saved until you review and accept it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private enum class InputMode { PORTRAIT, CHAT, VOICE }

@Composable
private fun PortraitRoomCard(
    replica: ReplicaSnapshot,
    portraitFile: File,
    talkingPortraitFile: File,
    blinkPortraitFile: File,
    lookLeftPortraitFile: File,
    lookRightPortraitFile: File,
    roomActive: Boolean,
    phase: ReplicaConversationPhase,
    audioLevel: Float,
    enabled: Boolean,
    onToggleRoom: () -> Unit,
    onOpenReplica: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    GlassCard(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.22f),
        shape = shape,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF24102C), Color(0xFF68143D), Color(0xFF9C3158)),
                    ),
                ),
        ) {
            if (replica.portraitReady) {
                ReplicaPortraitPreview(
                    portraitFile = portraitFile,
                    talkingPortraitFile = talkingPortraitFile,
                    blinkPortraitFile = blinkPortraitFile,
                    lookLeftPortraitFile = lookLeftPortraitFile,
                    lookRightPortraitFile = lookRightPortraitFile,
                    modifier = Modifier.fillMaxSize(),
                    talking = phase == ReplicaConversationPhase.SPEAKING,
                    listening = phase == ReplicaConversationPhase.LISTENING,
                    audioLevel = audioLevel,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent, Color.Black.copy(alpha = 0.64f)),
                        ),
                    ),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraLarge) {
                        Icon(
                            TachyonIcons.Video,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(18.dp).size(34.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Your private portrait", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Add one portrait for private eye movement, breathing and lip-sync.",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(avatarStatus(phase), active = phase != ReplicaConversationPhase.IDLE)
                    StatusPill("PRIVATE · GUARDED", active = false)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (replica.portraitReady) {
                        Column {
                            Text(
                                if (replica.style == "anime") "Your anime ERAYA" else "Your ERAYA portrait",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                when {
                                    phase == ReplicaConversationPhase.SPEAKING -> "Lip-syncing to ERAYA's voice"
                                    phase == ReplicaConversationPhase.LISTENING -> "Listening on device"
                                    else -> "Blinking, breathing and looking around"
                                },
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else {
                        TextButton(onClick = onOpenReplica) { Text("Add portrait", color = Color.White) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            onClick = onToggleRoom,
                            enabled = enabled,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (roomActive) MaterialTheme.colorScheme.error else Color.White,
                                contentColor = if (roomActive) Color.White else Color(0xFF7C1745),
                            ),
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(if (roomActive) TachyonIcons.Stop else TachyonIcons.Person, contentDescription = if (roomActive) "Stop listening" else "Talk to portrait")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputModeAction(
    label: String,
    supporting: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(38.dp).clip(MaterialTheme.shapes.small).background(if (selected) ErayaActionGradient else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            }
            Column {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChatComposer(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask your ERAYA replica anything…") },
            minLines = 1,
            maxLines = 3,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        FilledIconButton(
            onClick = onSend,
            enabled = enabled && text.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(52.dp),
        ) { Icon(TachyonIcons.Send, contentDescription = "Send typed message") }
    }
}

@Composable
private fun VoiceControls(recording: Boolean, speaking: Boolean, enabled: Boolean, level: Float, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(104.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PulseMicButton(recording = recording, enabled = enabled, level = level, onClick = onToggle, size = 54.dp)
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    recording -> "Listening on device"
                    speaking -> "Tap to interrupt and speak"
                    else -> "Tap to talk to your replica"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            LiveWaveform(level, recording)
        }
    }
}

private fun statusLine(ui: ReplicaConversationUi): String = when (ui.phase) {
    ReplicaConversationPhase.IDLE -> if (ui.messages.isEmpty()) "Ask your private replica anything" else "Ready for your next thought"
    ReplicaConversationPhase.LISTENING -> "Listening to you on device"
    ReplicaConversationPhase.TRANSCRIBING -> "Turning your voice into text"
    ReplicaConversationPhase.RECALLING -> "Searching your private memory"
    ReplicaConversationPhase.THINKING -> "ERAYA is thinking locally"
    ReplicaConversationPhase.SPEAKING -> "Your replica is answering"
    ReplicaConversationPhase.ERROR -> "The conversation paused"
}

private fun avatarStatus(phase: ReplicaConversationPhase): String = when (phase) {
    ReplicaConversationPhase.IDLE -> "READY"
    ReplicaConversationPhase.LISTENING -> "LISTENING"
    ReplicaConversationPhase.TRANSCRIBING -> "TRANSCRIBING"
    ReplicaConversationPhase.RECALLING -> "RECALLING"
    ReplicaConversationPhase.THINKING -> "THINKING"
    ReplicaConversationPhase.SPEAKING -> "AI VOICE"
    ReplicaConversationPhase.ERROR -> "PAUSED"
}

/** Compact when healthy, expands with fixes when an engine is down. */
@Composable
private fun EngineStrip(engines: EnginesStatus, onRetry: () -> Unit, onOpenTermux: () -> Unit) {
    val failed = engines.asr.state == EngineState.FAILED || engines.llm.state == EngineState.FAILED
    Surface(
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            EngineLine("ASR", engines.asr)
            EngineLine("LLM", engines.llm)
            AnimatedVisibility(failed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenTermux) { Text("Open Termux") }
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun EngineLine(label: String, h: EngineHealth) {
    val (mark, color) = when (h.state) {
        EngineState.READY -> "●" to MaterialTheme.colorScheme.primary
        EngineState.LOADING, EngineState.IDLE -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
        EngineState.FAILED -> "✕" to MaterialTheme.colorScheme.error
    }
    Text("$mark $label  ${h.name}", color = color, style = MaterialTheme.typography.bodyMedium)
    if (h.error != null) Text(h.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
}

@Composable
private fun ConversationCard(
    messages: List<ConversationMessage>,
    liveTranscript: String,
    onExtractTasks: (String) -> Unit,
    onOpenRecallSource: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val itemCount = messages.size + if (liveTranscript.isNotBlank()) 1 else 0
    LaunchedEffect(itemCount) { if (itemCount > 0) listState.animateScrollToItem(itemCount) }
    GlassCard(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Private conversation", style = MaterialTheme.typography.titleSmall)
                        Text("Kept in memory only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (messages.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
                }
            }
            if (messages.isEmpty() && liveTranscript.isBlank()) {
                item {
                    Text(
                        "Basic Qwen runs with your confirmed profile and code guardrails, entirely on-device.\nUse “Make tasks” only when you want a message converted into proposals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            itemsIndexed(messages, key = { _, message -> message.id }) { _, message ->
                var shown by remember(message.id) { mutableStateOf(false) }
                LaunchedEffect(message.id) { shown = true }
                AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 3 }) {
                    val user = message.role == ConversationRole.USER
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(0.88f),
                        ) {
                            Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                                Text(
                                    when {
                                        user -> "YOU"
                                        message.recallSources.isNotEmpty() -> "ERAYA · LOCAL MEMORY · ${message.recallSources.size} SOURCE${if (message.recallSources.size == 1) "" else "S"}"
                                        else -> "ERAYA · AI VOICE"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                                message.recallSources.forEachIndexed { index, source ->
                                    RecallSourceCard(index, source) { onOpenRecallSource(source.id) }
                                }
                                if (user) {
                                    TextButton(onClick = { onExtractTasks(message.text) }, modifier = Modifier.align(Alignment.End)) {
                                        Text("Make tasks")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (liveTranscript.isNotBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(0.88f),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("HEARING NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(liveTranscript, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)) }
        }
    }
}

@Composable
private fun RecallSourceCard(index: Int, source: RecallSource, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "SOURCE ${index + 1} · ${if (source.status == Status.ACCEPTED) "CONFIRMED" else "REJECTED"} · #${source.id.take(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(source.task, style = MaterialTheme.typography.titleSmall)
            val details = listOfNotNull(
                source.person?.takeIf(String::isNotBlank)?.let { "For $it" },
                source.deadline?.takeIf(String::isNotBlank)?.let { "Due $it" },
            ).joinToString(" · ")
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
            Text("“${source.evidence}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap to inspect record", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
