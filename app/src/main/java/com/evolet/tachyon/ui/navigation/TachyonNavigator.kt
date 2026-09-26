package com.evolet.tachyon.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import kotlinx.serialization.serializer

/** One saveable back stack per bottom tab, following Android's official multiple-stacks recipe. */
class TachyonNavigator internal constructor(
    private val selectedName: MutableState<String>,
    val stacks: Map<TopLevelTab, NavBackStack<AppRoute>>,
) {
    var selectedTab: TopLevelTab
        get() = TopLevelTab.valueOf(selectedName.value)
        private set(value) { selectedName.value = value.name }

    val activeStack: NavBackStack<AppRoute> get() = stacks.getValue(selectedTab)
    val currentRoute: AppRoute get() = activeStack.last()

    fun select(tab: TopLevelTab) {
        selectedTab = tab
    }

    fun navigate(route: AppRoute) {
        if (activeStack.lastOrNull() != route) activeStack.add(route)
    }

    fun navigateIn(tab: TopLevelTab, route: AppRoute, select: Boolean = true) {
        val stack = stacks.getValue(tab)
        if (stack.lastOrNull() != route) stack.add(route)
        if (select) selectedTab = tab
    }

    fun back(): Boolean {
        if (activeStack.size > 1) {
            activeStack.removeLastOrNull()
            return true
        }
        if (selectedTab != TopLevelTab.CAPTURE) {
            selectedTab = TopLevelTab.CAPTURE
            return true
        }
        return false
    }

    fun reset(tab: TopLevelTab) {
        val stack = stacks.getValue(tab)
        while (stack.size > 1) stack.removeLastOrNull()
    }
}

@Composable
fun rememberTachyonNavigator(): TachyonNavigator {
    val selected = rememberSaveable { mutableStateOf(TopLevelTab.CAPTURE.name) }
    val capture = rememberAppNavBackStack(CaptureRoute)
    val sessions = rememberAppNavBackStack(SessionsRoute)
    val tasks = rememberAppNavBackStack(TasksRoute)
    val you = rememberAppNavBackStack(YouRoute)
    return remember(selected, capture, sessions, tasks, you) {
        TachyonNavigator(
            selected,
            mapOf(
                TopLevelTab.CAPTURE to capture,
                TopLevelTab.SESSIONS to sessions,
                TopLevelTab.TASKS to tasks,
                TopLevelTab.YOU to you,
            ),
        )
    }
}

@Composable
private fun rememberAppNavBackStack(vararg routes: AppRoute): NavBackStack<AppRoute> =
    rememberSerializable(serializer = serializer()) { NavBackStack(*routes) }
