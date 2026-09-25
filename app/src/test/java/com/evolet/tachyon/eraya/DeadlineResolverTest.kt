package com.evolet.tachyon.eraya

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DeadlineResolverTest {

    private val r = DeadlineResolver()
    private val sat = LocalDate.of(2026, 9, 26) // event day 1 is a Saturday

    @Test fun `friday evening resolves to next friday 18h`() =
        assertEquals("2026-10-02T18:00", r.resolve("Friday evening", null, sat))

    @Test fun `hindi kal is tomorrow`() =
        assertEquals("2026-09-27", r.resolve("kal tak", null, sat))

    @Test fun `devanagari kal is tomorrow`() =
        assertEquals("2026-09-27", r.resolve("कल तक", null, sat))

    @Test fun `today itself beats monday in the same phrase`() =
        assertEquals("2026-09-26T09:00", r.resolve("today itself, Monday morning", null, sat))

    @Test fun `same weekday means next week`() =
        assertEquals("2026-10-03", r.resolve("Saturday", null, sat))

    @Test fun `rules override a wrong model date`() =
        assertEquals("2026-09-27", r.resolve("tomorrow", "2026-09-30", sat))

    @Test fun `model time kept when dates agree`() =
        assertEquals("2026-09-27T15:00", r.resolve("tomorrow", "2026-09-27T15:00", sat))

    @Test fun `explicit pm time`() =
        assertEquals("2026-09-28T17:30", r.resolve("Monday 5:30 pm", null, sat))

    @Test fun `unknown text falls back to a sane model date`() =
        assertEquals("2026-10-15", r.resolve("mid October", "2026-10-15", sat))

    @Test fun `past or absurd model dates are dropped`() {
        assertNull(r.resolve("mid October", "2025-10-15", sat))
        assertNull(r.resolve("someday", "2031-01-01", sat))
    }

    @Test fun `none means no deadline`() {
        assertNull(r.resolve("none", "2026-09-27", sat))
        assertNull(r.resolve("", null, sat))
    }
}
