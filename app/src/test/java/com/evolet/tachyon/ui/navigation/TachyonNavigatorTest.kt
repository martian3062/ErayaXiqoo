package com.evolet.tachyon.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TachyonNavigatorTest {

    @Test
    fun eachTopLevelTabKeepsItsOwnBackStack() {
        val navigator = navigator()
        navigator.navigate(ReviewRoute("session-1"))

        navigator.select(TopLevelTab.TASKS)
        navigator.navigate(TaskDetailRoute("task-1"))
        navigator.select(TopLevelTab.CAPTURE)

        assertEquals(ReviewRoute("session-1"), navigator.currentRoute)
        navigator.select(TopLevelTab.TASKS)
        assertEquals(TaskDetailRoute("task-1"), navigator.currentRoute)
    }

    @Test
    fun backPopsDetailThenReturnsToCaptureRoot() {
        val navigator = navigator()
        navigator.select(TopLevelTab.SESSIONS)
        navigator.navigate(SessionDetailRoute("session-1"))

        assertTrue(navigator.back())
        assertEquals(SessionsRoute, navigator.currentRoute)
        assertTrue(navigator.back())
        assertEquals(TopLevelTab.CAPTURE, navigator.selectedTab)
        assertEquals(CaptureRoute, navigator.currentRoute)
    }

    @Test
    fun reselectResetRemovesOnlyThatTabsDetails() {
        val navigator = navigator()
        navigator.navigate(ReviewRoute("session-1"))
        navigator.select(TopLevelTab.TASKS)
        navigator.navigate(TaskDetailRoute("task-1"))

        navigator.reset(TopLevelTab.TASKS)

        assertEquals(TasksRoute, navigator.currentRoute)
        navigator.select(TopLevelTab.CAPTURE)
        assertEquals(ReviewRoute("session-1"), navigator.currentRoute)
    }

    private fun navigator() = TachyonNavigator(
        mutableStateOf(TopLevelTab.CAPTURE.name),
        mapOf(
            TopLevelTab.CAPTURE to NavBackStack(CaptureRoute),
            TopLevelTab.SESSIONS to NavBackStack(SessionsRoute),
            TopLevelTab.TASKS to NavBackStack(TasksRoute),
            TopLevelTab.YOU to NavBackStack(YouRoute),
        ),
    )
}
