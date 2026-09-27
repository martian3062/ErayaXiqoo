package com.evolet.tachyon.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Saveable Navigation 3 keys. IDs, rather than database objects, are navigation arguments. */
@Serializable
sealed interface AppRoute : NavKey

@Serializable data object CaptureRoute : AppRoute
@Serializable data object SessionsRoute : AppRoute
@Serializable data object TasksRoute : AppRoute
@Serializable data object YouRoute : AppRoute

@Serializable data class ReviewRoute(val sessionId: String) : AppRoute
@Serializable data class SessionDetailRoute(val sessionId: String) : AppRoute
@Serializable data class TaskDetailRoute(val commitmentId: String) : AppRoute

@Serializable data object ProfileRoute : AppRoute
@Serializable data object PeopleRoute : AppRoute
@Serializable data object ReplicaRoute : AppRoute
@Serializable data object VoiceRoute : AppRoute
@Serializable data object EyesRoute : AppRoute
@Serializable data object HandshakeRoute : AppRoute
@Serializable data class InterviewRoute(val demo: Boolean) : AppRoute
@Serializable data object PrivacyRoute : AppRoute

@Serializable data object SettingsRoute : AppRoute
@Serializable data object EnginesRoute : AppRoute
@Serializable data object LanguageRoute : AppRoute
@Serializable data object RemindersRoute : AppRoute
@Serializable data object DemoToolsRoute : AppRoute
@Serializable data object AboutRoute : AppRoute

enum class TopLevelTab(val label: String, val root: AppRoute) {
    CAPTURE("Room", CaptureRoute),
    SESSIONS("Sessions", SessionsRoute),
    TASKS("Tasks", TasksRoute),
    YOU("You", YouRoute),
}

fun AppRoute.title(): String = when (this) {
    CaptureRoute -> "Room"
    SessionsRoute -> "Sessions"
    TasksRoute -> "Tasks"
    YouRoute -> "You"
    is ReviewRoute -> "Review proposals"
    is SessionDetailRoute -> "Session"
    is TaskDetailRoute -> "Commitment"
    ProfileRoute -> "My profile"
    PeopleRoute -> "People"
    ReplicaRoute -> "Portrait Studio"
    VoiceRoute -> "My voice"
    EyesRoute -> "Eyes"
    HandshakeRoute -> "Trust handshake"
    is InterviewRoute -> "Get to know me"
    PrivacyRoute -> "Privacy & export"
    SettingsRoute -> "Settings"
    EnginesRoute -> "Engines"
    LanguageRoute -> "Language"
    RemindersRoute -> "Reminders"
    DemoToolsRoute -> "Demo tools"
    AboutRoute -> "About"
}

fun AppRoute.isTopLevel(): Boolean = TopLevelTab.entries.any { it.root == this }
