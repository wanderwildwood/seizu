package com.wanderwildwood.seizu.sky

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.GregorianCalendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class DeclinationTest {
    private val nov2026 = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, 10, 3, 12, 0)
    }.timeInMillis

    @Test
    fun bostonIsAboutFourteenWest() {
        // NOAA's figure for Boston in 2026 is about 14 degrees west.
        assertEquals(-14.0, magneticDeclination(42.36, -71.06, nov2026), 1.0)
    }

    @Test
    fun greenwichIsNearlyNone() {
        assertEquals(0.5, magneticDeclination(51.48, 0.0, nov2026), 1.5)
    }

    @Test
    fun westDeclinationTurnsTheBearingBack() {
        assertEquals(76.0, trueAim(Aim(90.0, 30.0), -14.0).azimuth, 1e-9)
        assertEquals(30.0, trueAim(Aim(90.0, 30.0), -14.0).altitude, 1e-9)
    }

    @Test
    fun wrapsPastNorth() {
        assertEquals(351.0, trueAim(Aim(5.0, 10.0), -14.0).azimuth, 1e-9)
        assertEquals(4.0, trueAim(Aim(354.0, 10.0), 10.0).azimuth, 1e-9)
    }
}
