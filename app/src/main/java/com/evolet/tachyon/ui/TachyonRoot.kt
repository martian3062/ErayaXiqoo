package com.evolet.tachyon.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.evolet.tachyon.AppContainer
import com.evolet.tachyon.audio.SampleAudio
import com.evolet.tachyon.calendar.CalendarBridge
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.data.Tier
import com.evolet.tachyon.session.Phase
import com.evolet.tachyon.ui.components.OfflineBadge
import com.evolet.tachyon.ui.components.TachyonIcons
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    RECORD("Record", TachyonIcons.Mic),
    TASKS("Tasks", TachyonIcons.List),
    SETTINGS("Settings", TachyonIcons.Sliders),
}

@Composable
fun TachyonRoot(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ui by container.session.ui.collectAsState()
    val level by container.session.level.collectAsState()
    val peopleList by container.personaStore.people.collectAsState()
    val people = remember(peopleList) { peopleList.associateBy { it.id } }
    val engines by container.engines.status.collectAsState()
    val settings by container.settings.state.collectAsState()
    val net by container.connectivity.state.collectAsState(initial = container.connectivity.snapshot())
    val accepted by remember { container.db.commitmentDao().observeByStatus(Status.ACCEPTED) }.collectAsState(initial = emptyList())
    val sessionItems by remember(ui.sessionId) {
        ui.sessionId?.let { container.db.commitmentDao().observeSession(it) } ?: emptyFlow()
    }.collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableStateOf(Tab.RECORD) }
    val interviewUi by container.interview.ui.collectAsState()
    var draftFor by remember { mutableStateOf<com.evolet.tachyon.data.Commitment?>(null) }
    draftFor?.let { c -> DraftDialog(container, c, peopleList) { draftFor = null } }
    LaunchedEffect(ui.phase) { if (ui.phase == Phase.REVIEW) tab = Tab.RECORD }

    // Mic + notification permission on first launch, and again on tap if it was denied.
    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] != true) {
            Toast.makeText(context, "Tachyon needs the microphone to record", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) { if (!micGranted(context)) launcher.launch(permissions) }

    val compute = when {
        settings.asrTier == Tier.NPU && settings.llmTier == Tier.NPU -> "NPU"
        settings.asrTier == Tier.NPU || settings.llmTier == Tier.NPU -> "NPU+CPU"
        else -> "CPU"
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.evolet.tachyon.ui.components.Wordmark(Modifier.weight(1f))
                OfflineBadge(net, compute)
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        if (interviewUi.stage != com.evolet.tachyon.twin.Stage.IDLE) {
            Box(Modifier.padding(padding)) { com.evolet.tachyon.ui.onboarding.InterviewScreen(container.interview) { } }
            return@Scaffold
        }
        AnimatedContent(tab, Modifier.padding(padding), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { current ->
            when (current) {
                Tab.RECORD -> if (ui.phase == Phase.REVIEW) {
                    ProposalsScreen(
                        ui = ui,
                        items = sessionItems,
                        people = people,
                        onAccept = { id -> scope.launch { container.confirmation.accept(id) } },
                        onReject = { id -> scope.launch { container.confirmation.reject(id) } },
                        onUndo = { id -> scope.launch { container.confirmation.undo(id) } },
                        onDone = {
                            container.session.reset()
                            tab = Tab.TASKS
                        },
                    )
                } else {
                    RecordScreen(
                        ui = ui,
                        level = level,
                        engines = engines,
                        onStart = {
                            if (micGranted(context)) container.session.startRecording() else launcher.launch(permissions)
                        },
                        onStop = { container.session.stopRecording() },
                        onRetryExtraction = { container.session.retryExtraction() },
                        onRetryEngines = { container.engines.retry() },
                        onOpenTermux = { openTermux(context) },
                    )
                }
                Tab.TASKS -> TasksScreen(
                    accepted, people,
                    onAddToCalendar = { c ->
                        if (!CalendarBridge.open(context, c)) Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
                    },
                    onDraft = { draftFor = it },
                )
                Tab.SETTINGS -> {
                    val modelsDir = context.getExternalFilesDir("models")
                    SettingsScreen(
                        settings = settings,
                        onChange = container.settings::update,
                        modelsDir = modelsDir?.path ?: "(storage unavailable)",
                        modelFiles = remember(tab) { modelsDir?.list()?.sorted().orEmpty() },
                        promptsDir = container.prompts.overrideDir?.path ?: "(storage unavailable)",
                        sampleWavPath = SampleAudio.externalFile(context)?.path ?: SampleAudio.FILE_NAME,
                        onUseSample = {
                            container.session.runSample()
                            tab = Tab.RECORD
                        },
                        onOpenTermux = { openTermux(context) },
                        onBatterySettings = { openBatterySettings(context) },
                        onExportPrefs = { includeNames ->
                            scope.launch {
                                val msg = runCatching {
                                    val pairs = container.db.preferenceDao().all()
                                    val jsonl = com.evolet.tachyon.trust.TrustPolicy.exportJsonl(pairs, peopleList, includeNames)
                                    "Exported ${pairs.count { it.rejected != null }} pairs to " + com.evolet.tachyon.export.PrefExporter.write(context, jsonl)
                                }.getOrElse { "Export failed: ${it.message}" }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        onInterview = { demo -> container.interview.start(demo) },
                        onTestReminder = {
                            val latest = accepted.firstOrNull()
                            if (latest == null) Toast.makeText(context, "Accept a task first", Toast.LENGTH_SHORT).show()
                            else {
                                container.reminders.fireSoon(latest)
                                Toast.makeText(context, "Reminder in ~3 s: ${latest.task}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeleteTwin = {
                            scope.launch {
                                val deleted = container.personaStore.wipe()
                                container.db.preferenceDao().clear()
                                Toast.makeText(context, "Deleted twin data: ${deleted.joinToString().ifEmpty { "nothing stored" }} + preference pairs", Toast.LENGTH_LONG).show()
                            }
                        },
                        personaSummary = container.personaStore.persona.value.let { p -> "${p.owner.name} · ${p.style.rules.size} style rules · ${p.style.examples.size} examples · ${p.traits.size} traits" },
                    )
                }
            }
        }
    }
}

private fun micGranted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun openTermux(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
    if (intent != null) context.startActivity(intent)
    else Toast.makeText(context, "Termux is not installed", Toast.LENGTH_SHORT).show()
}

private fun openBatterySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        .onFailure { Toast.makeText(context, "Open Settings → Battery manually", Toast.LENGTH_SHORT).show() }
}
