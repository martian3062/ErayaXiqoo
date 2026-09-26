package com.evolet.tachyon.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.evolet.tachyon.AppContainer
import com.evolet.tachyon.audio.SampleAudio
import com.evolet.tachyon.calendar.CalendarBridge
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.data.Tier
import com.evolet.tachyon.session.Phase
import com.evolet.tachyon.twin.Stage
import com.evolet.tachyon.ui.components.OfflineBadge
import com.evolet.tachyon.ui.components.TachyonIcons
import com.evolet.tachyon.ui.components.Wordmark
import com.evolet.tachyon.ui.theme.ErayaBackdrop
import com.evolet.tachyon.ui.navigation.AboutRoute
import com.evolet.tachyon.ui.navigation.AppRoute
import com.evolet.tachyon.ui.navigation.CaptureRoute
import com.evolet.tachyon.ui.navigation.DemoToolsRoute
import com.evolet.tachyon.ui.navigation.EnginesRoute
import com.evolet.tachyon.ui.navigation.InterviewRoute
import com.evolet.tachyon.ui.navigation.LanguageRoute
import com.evolet.tachyon.ui.navigation.PeopleRoute
import com.evolet.tachyon.ui.navigation.PrivacyRoute
import com.evolet.tachyon.ui.navigation.ProfileRoute
import com.evolet.tachyon.ui.navigation.RemindersRoute
import com.evolet.tachyon.ui.navigation.ReviewRoute
import com.evolet.tachyon.ui.navigation.SessionDetailRoute
import com.evolet.tachyon.ui.navigation.SessionsRoute
import com.evolet.tachyon.ui.navigation.SettingsRoute
import com.evolet.tachyon.ui.navigation.TaskDetailRoute
import com.evolet.tachyon.ui.navigation.TasksRoute
import com.evolet.tachyon.ui.navigation.TopLevelTab
import com.evolet.tachyon.ui.navigation.YouRoute
import com.evolet.tachyon.ui.navigation.isTopLevel
import com.evolet.tachyon.ui.navigation.rememberTachyonNavigator
import com.evolet.tachyon.ui.navigation.title
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TachyonRoot(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = rememberTachyonNavigator()

    val ui by container.session.ui.collectAsState()
    val level by container.session.level.collectAsState()
    val peopleList by container.personaStore.people.collectAsState()
    val persona by container.personaStore.persona.collectAsState()
    val people = remember(peopleList) { peopleList.associateBy { it.id } }
    val engines by container.engines.status.collectAsState()
    val settings by container.settings.state.collectAsState()
    val net by container.connectivity.state.collectAsState(initial = container.connectivity.snapshot())
    val accepted by remember { container.db.commitmentDao().observeByStatus(Status.ACCEPTED) }.collectAsState(initial = emptyList())
    val sessions by remember { container.db.sessionDao().observeAll() }.collectAsState(initial = emptyList())

    var draftFor by remember { mutableStateOf<Commitment?>(null) }
    draftFor?.let { commitment -> DraftDialog(container, commitment, peopleList) { draftFor = null } }

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
    LaunchedEffect(ui.phase, ui.sessionId) {
        if (ui.phase == Phase.REVIEW && ui.sessionId != null) {
            navigator.navigateIn(TopLevelTab.CAPTURE, ReviewRoute(ui.sessionId!!))
        }
    }

    val compute = when {
        settings.asrTier == Tier.NPU && settings.llmTier == Tier.NPU -> "NPU"
        settings.asrTier == Tier.NPU || settings.llmTier == Tier.NPU -> "NPU+CPU"
        else -> "CPU"
    }
    val modelsDir = context.getExternalFilesDir("models")
    val modelFiles = remember(modelsDir?.path, navigator.currentRoute) { modelsDir?.list()?.sorted().orEmpty() }

    fun exportPrefs(includeNames: Boolean) {
        scope.launch {
            val message = runCatching {
                val pairs = container.db.preferenceDao().all()
                val jsonl = com.evolet.tachyon.trust.TrustPolicy.exportJsonl(pairs, peopleList, includeNames)
                "Exported ${pairs.count { it.rejected != null }} pairs to ${com.evolet.tachyon.export.PrefExporter.write(context, jsonl)}"
            }.getOrElse { "Export failed: ${it.message}" }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun deleteTwin() {
        scope.launch {
            val deleted = container.personaStore.wipe()
            container.db.preferenceDao().clear()
            Toast.makeText(context, "Deleted twin data: ${deleted.joinToString().ifEmpty { "nothing stored" }} + preference pairs", Toast.LENGTH_LONG).show()
        }
    }

    fun goBack() {
        when (navigator.currentRoute) {
            is ReviewRoute -> container.session.reset()
            is InterviewRoute -> container.interview.cancel()
            else -> Unit
        }
        navigator.back()
    }

    BackHandler(enabled = navigator.activeStack.size == 1 && navigator.selectedTab != TopLevelTab.CAPTURE) {
        navigator.select(TopLevelTab.CAPTURE)
    }

    val captureDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<AppRoute>())
    val sessionDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<AppRoute>())
    val taskDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<AppRoute>())
    val youDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<AppRoute>())
    val decorators = remember(captureDecorators, sessionDecorators, taskDecorators, youDecorators) {
        mapOf(
            TopLevelTab.CAPTURE to captureDecorators,
            TopLevelTab.SESSIONS to sessionDecorators,
            TopLevelTab.TASKS to taskDecorators,
            TopLevelTab.YOU to youDecorators,
        )
    }

    val entries = entryProvider {
        entry<CaptureRoute> {
            RecordScreen(
                ui, level, engines,
                onStart = { if (micGranted(context)) container.session.startRecording() else launcher.launch(permissions) },
                onStop = container.session::stopRecording,
                onRetryExtraction = container.session::retryExtraction,
                onRetryEngines = container.engines::retry,
                onOpenTermux = { openTermux(context) },
            )
        }
        entry<ReviewRoute> { route ->
            val items by remember(route.sessionId) { container.db.commitmentDao().observeSession(route.sessionId) }.collectAsState(initial = emptyList())
            ProposalsScreen(
                ui, items, people,
                onAccept = { id -> scope.launch { container.confirmation.accept(id) } },
                onReject = { id -> scope.launch { container.confirmation.reject(id) } },
                onUndo = { id -> scope.launch { container.confirmation.undo(id) } },
                onDone = {
                    container.session.reset()
                    navigator.reset(TopLevelTab.CAPTURE)
                    navigator.select(TopLevelTab.TASKS)
                },
            )
        }
        entry<SessionsRoute> { SessionsScreen(sessions) { navigator.navigate(SessionDetailRoute(it.id)) } }
        entry<SessionDetailRoute> { route -> SessionDetailDestination(container, route, people) }
        entry<TasksRoute> { TasksScreen(accepted, people) { navigator.navigate(TaskDetailRoute(it.id)) } }
        entry<TaskDetailRoute> { route ->
            TaskDetailDestination(container, route, people, onCalendar = { openCalendar(context, it) }, onDraft = { draftFor = it })
        }
        entry<YouRoute> {
            YouScreen(
                persona, peopleList,
                onProfile = { navigator.navigate(ProfileRoute) },
                onPeople = { navigator.navigate(PeopleRoute) },
                onInterview = { navigator.navigate(InterviewRoute(it)) },
                onPrivacy = { navigator.navigate(PrivacyRoute) },
            )
        }
        entry<ProfileRoute> {
            ProfileScreen(persona) { index ->
                if (index in persona.traits.indices) container.personaStore.savePersona(persona.copy(traits = persona.traits.filterIndexed { i, _ -> i != index }))
            }
        }
        entry<PeopleRoute> { PeopleScreen(peopleList, container.personaStore::savePeople) }
        entry<PrivacyRoute> { PrivacyScreen(persona, peopleList.size, ::exportPrefs, ::deleteTwin) }
        entry<InterviewRoute> { route ->
            LaunchedEffect(route) { if (container.interview.ui.value.stage == Stage.IDLE) container.interview.start(route.demo) }
            com.evolet.tachyon.ui.onboarding.InterviewScreen(container.interview) { navigator.back() }
        }
        entry<SettingsRoute> {
            SettingsHomeScreen(
                settings,
                onEngines = { navigator.navigate(EnginesRoute) },
                onLanguage = { navigator.navigate(LanguageRoute) },
                onReminders = { navigator.navigate(RemindersRoute) },
                onDemoTools = { navigator.navigate(DemoToolsRoute) },
                onAbout = { navigator.navigate(AboutRoute) },
            )
        }
        entry<EnginesRoute> { EnginesSettingsScreen(settings, engines, container.settings::update, modelsDir?.path ?: "(storage unavailable)", modelFiles) { openTermux(context) } }
        entry<LanguageRoute> { LanguageSettingsScreen(settings, container.settings::update) }
        entry<RemindersRoute> {
            ReminderSettingsScreen(settings, container.settings::update) {
                val latest = accepted.firstOrNull()
                if (latest == null) Toast.makeText(context, "Accept a task first", Toast.LENGTH_SHORT).show()
                else {
                    container.reminders.fireSoon(latest)
                    Toast.makeText(context, "Reminder in about 3 seconds: ${latest.task}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        entry<DemoToolsRoute> {
            DemoToolsScreen(
                sampleWavPath = SampleAudio.externalFile(context)?.path ?: SampleAudio.FILE_NAME,
                promptsDir = container.prompts.overrideDir?.path ?: "(storage unavailable)",
                onUseSample = {
                    navigator.reset(TopLevelTab.CAPTURE)
                    navigator.select(TopLevelTab.CAPTURE)
                    container.session.runSample()
                },
                onOpenTermux = { openTermux(context) },
                onBatterySettings = { openBatterySettings(context) },
            )
        }
        entry<AboutRoute> { AboutScreen() }
    }

    val interviewVisible = navigator.currentRoute is InterviewRoute
    ErayaBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!interviewVisible) AppTopBar(navigator.currentRoute, net, compute, onBack = ::goBack, onSettings = { navigator.navigate(SettingsRoute) })
            },
            bottomBar = {
                if (!interviewVisible) {
                    FloatingTabBar(
                        selected = navigator.selectedTab,
                        onSelect = { tab -> if (navigator.selectedTab == tab) navigator.reset(tab) else navigator.select(tab) },
                    )
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = navigator.selectedTab,
                modifier = Modifier.fillMaxSize().padding(padding),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(300)) { direction * it / 4 } + fadeIn(tween(240)) + scaleIn(tween(300), initialScale = 0.97f)) togetherWith
                        (slideOutHorizontally(tween(260)) { -direction * it / 5 } + fadeOut(tween(190)) + scaleOut(tween(260), targetScale = 0.98f))
                },
                label = "top-level-tab",
            ) { tab ->
                NavDisplay(
                    backStack = navigator.stacks.getValue(tab),
                    onBack = ::goBack,
                    entryDecorators = decorators.getValue(tab),
                    entryProvider = entries,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { slideInHorizontally { it / 4 } + fadeIn() togetherWith slideOutHorizontally { -it / 4 } + fadeOut() },
                    popTransitionSpec = { slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it / 4 } + fadeOut() },
                    predictivePopTransitionSpec = { slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it / 4 } + fadeOut() },
                )
            }
        }
    }
}

@Composable
private fun FloatingTabBar(selected: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
        ) {
            NavigationBar(
                modifier = Modifier.height(72.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                TopLevelTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { onSelect(tab) },
                        icon = { Icon(tab.icon(), contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(route: AppRoute, net: com.evolet.tachyon.net.NetState, compute: String, onBack: () -> Unit, onSettings: () -> Unit) {
    if (route.isTopLevel()) {
        TopAppBar(
            title = { if (route == CaptureRoute) Wordmark() else Text(route.title()) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            actions = {
                OfflineBadge(net, compute)
                IconButton(onClick = onSettings) { Icon(TachyonIcons.Settings, contentDescription = "Settings") }
            },
        )
    } else {
        TopAppBar(
            title = { Text(route.title()) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = { IconButton(onClick = onBack) { Icon(TachyonIcons.Back, contentDescription = "Back") } },
        )
    }
}

@Composable
private fun SessionDetailDestination(container: AppContainer, route: SessionDetailRoute, people: Map<String, com.evolet.tachyon.twin.Person>) {
    val session by remember(route.sessionId) { container.db.sessionDao().observe(route.sessionId) }.collectAsState(initial = null)
    val commitments by remember(route.sessionId) { container.db.commitmentDao().observeSession(route.sessionId) }.collectAsState(initial = emptyList())
    SessionDetailScreen(session, commitments, people)
}

@Composable
private fun TaskDetailDestination(
    container: AppContainer,
    route: TaskDetailRoute,
    people: Map<String, com.evolet.tachyon.twin.Person>,
    onCalendar: (Commitment) -> Unit,
    onDraft: (Commitment) -> Unit,
) {
    val commitment by remember(route.commitmentId) { container.db.commitmentDao().observeById(route.commitmentId) }.collectAsState(initial = null)
    val persona by container.personaStore.persona.collectAsState()
    TaskDetailScreen(commitment, people, persona, onCalendar, onDraft)
}

private fun TopLevelTab.icon(): ImageVector = when (this) {
    TopLevelTab.CAPTURE -> TachyonIcons.Mic
    TopLevelTab.SESSIONS -> TachyonIcons.History
    TopLevelTab.TASKS -> TachyonIcons.List
    TopLevelTab.YOU -> TachyonIcons.Person
}

private fun micGranted(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun openCalendar(context: Context, commitment: Commitment) {
    if (!CalendarBridge.open(context, commitment)) Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
}

private fun openTermux(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
    if (intent != null) context.startActivity(intent) else Toast.makeText(context, "Termux is not installed", Toast.LENGTH_SHORT).show()
}

private fun openBatterySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        .onFailure { Toast.makeText(context, "Open Settings → Battery manually", Toast.LENGTH_SHORT).show() }
}
