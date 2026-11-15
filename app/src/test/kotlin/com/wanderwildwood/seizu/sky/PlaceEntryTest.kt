package com.wanderwildwood.seizu.sky

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceEntryTest {
    @Test fun `a fix from the phone is shown to four decimals`() {
        assertEquals("50.0541", PlaceEntry.degreesText(50.054054054054056))
        assertEquals("19.9245", PlaceEntry.degreesText(19.92446300765718))
    }

    @Test fun `the sign is left to the button beside the field`() {
        assertEquals("0.0014", PlaceEntry.degreesText(-0.0014))
        assertEquals("33.8688", PlaceEntry.degreesText(-33.8688))
    }

    @Test fun `no zeros are added or left trailing`() {
        assertEquals("51.4778", PlaceEntry.degreesText(51.4778))
        assertEquals("50", PlaceEntry.degreesText(50.0))
        assertEquals("100", PlaceEntry.degreesText(100.0))
        assertEquals("12.5", PlaceEntry.degreesText(12.5))
        assertEquals("0", PlaceEntry.degreesText(0.0))
    }

    @Test fun `the wait runs to the turn of the minute`() {
        assertEquals(60_000L, millisToNextMinute(1_800_000_000_000L))
        assertEquals(1L, millisToNextMinute(1_800_000_059_999L))
        assertEquals(45_000L, millisToNextMinute(1_800_000_015_000L))
    }
}
