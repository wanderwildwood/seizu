package com.wanderwildwood.seizu.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeEntryTest {
    @Test fun `a day the month does not have is refused`() {
        assertFalse(TimeEntry.dateOk(2026, 2, 31))
        assertFalse(TimeEntry.dateOk(2026, 4, 31))
        assertFalse(TimeEntry.dateOk(2025, 2, 29))
        assertTrue(TimeEntry.dateOk(2028, 2, 29))
        assertTrue(TimeEntry.dateOk(2026, 12, 31))
    }

    @Test fun `years outside the ephemeris tables are refused`() {
        assertFalse(TimeEntry.dateOk(1978, 6, 1))
        assertFalse(TimeEntry.dateOk(2051, 6, 1))
        assertTrue(TimeEntry.dateOk(1979, 5, 7))
        assertTrue(TimeEntry.dateOk(2050, 7, 13))
    }

    @Test fun `twelve hour clock converts both ways`() {
        assertEquals(0, TimeEntry.to24(12, false))
        assertEquals(12, TimeEntry.to24(12, true))
        assertEquals(22, TimeEntry.to24(10, true))
        assertEquals(1, TimeEntry.to24(1, false))
        assertEquals(12 to false, TimeEntry.from24(0))
        assertEquals(12 to true, TimeEntry.from24(12))
        assertEquals(10 to true, TimeEntry.from24(22))
        for (h in 0..23) { val (h12, pm) = TimeEntry.from24(h); assertEquals(h, TimeEntry.to24(h12, pm)) }
    }
}
