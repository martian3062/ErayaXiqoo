package com.evolet.tachyon.reminders

import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.twin.Persona
import com.evolet.tachyon.twin.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderTest {
    private val sat10 = LocalDateTime.of(2026, 9, 26, 10, 0)
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, d, h, m)

    @Test fun `24h and 2h before the deadline`() {
        assertEquals(listOf(at(29, 18), at(30, 16)), ReminderPlanner.plan("2026-09-30T18:00", sat10, Persona()))
    }

    @Test fun `date-only deadline counts as 18_00 and past offsets collapse to now`() {
        val r = ReminderPlanner.plan("2026-09-26", sat10, Persona())
        assertEquals(listOf(sat10.plusMinutes(1), at(26, 16)), r)
    }

    @Test fun `quiet hours shift a reminder to when they end`() {
        val p = Persona(rhythm = Persona.Rhythm(quietHours = "10 pm to 8 am"))
        // due Tue 07:00 -> T-24h Mon 07:00 (quiet) -> Mon 08:00; T-2h Tue 05:00 (quiet) -> Tue 08:00 is after due -> dropped
        assertEquals(listOf(at(28, 8)), ReminderPlanner.plan("2026-09-29T07:00", sat10, p))
    }

    @Test fun `no weekend work reminders and tolerance caps the count`() {
        val noWeekend = Persona(rhythm = Persona.Rhythm(weekendPolicy = "no work reminders on weekends"))
        // due Mon 18:00 -> T-24h Sun 18:00 moves to Mon 09:00; T-2h Mon 16:00 stays
        assertEquals(listOf(at(28, 9), at(28, 16)), ReminderPlanner.plan("2026-09-28T18:00", sat10, noWeekend))
        val once = Persona(rhythm = Persona.Rhythm(reminderTolerance = 1))
        assertEquals(1, ReminderPlanner.plan("2026-09-30T18:00", sat10, once).size)
        assertTrue(ReminderPlanner.plan("2026-09-20", sat10, Persona()).isEmpty())
        assertTrue(ReminderPlanner.plan(null, sat10, Persona()).isEmpty())
    }

    @Test fun `parses quiet hour formats`() {
        assertEquals(ReminderPlanner.Quiet(LocalTime.of(22, 0), LocalTime.of(8, 0)), ReminderPlanner.parseQuiet("22:00-08:00"))
        assertEquals(ReminderPlanner.Quiet(LocalTime.of(23, 30), LocalTime.of(7, 0)), ReminderPlanner.parseQuiet("after 11:30 pm until 7 am"))
        assertEquals(null, ReminderPlanner.parseQuiet("whenever"))
    }

    @Test fun `reminder text uses the person and the owner's tone`() {
        val c = Commitment("c", "s", "Speaker", "Send the revised intake form", null, "Friday evening", "2026-10-02T18:00",
            "ev", 0.9, Status.ACCEPTED, 1, ownerIsUser = true, toPersonId = "p_alex")
        val alex = Person("p_alex", "Alex")
        val gentle = ReminderText.build(c, alex, Persona(), at(30, 10))
        assertTrue(gentle, gentle.startsWith("You promised Alex: send the revised intake form, due on Fri"))
        val blunt = ReminderText.build(c, alex, Persona(assistant = Persona.AssistantPrefs(tone = "direct, be blunt")), LocalDateTime.of(2026, 10, 2, 16, 30))
        assertTrue(blunt, blunt.contains("due in 90 min") && blunt.endsWith("Do it now or tell them."))
    }
}
