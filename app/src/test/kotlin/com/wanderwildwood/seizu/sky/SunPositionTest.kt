package com.wanderwildwood.seizu.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tengel.planisphere.Astro
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Where the Sun is, checked against answers known without doing any astronomy.
 *
 * This is the test that matters most in the whole app, and the reason is that a star chart
 * cannot be proofread. Swap a right ascension for a declination, or feed degrees where
 * hours were wanted, and every object moves to somewhere else entirely — but the result is
 * still a plausible sky full of plausible constellations, and nothing on the screen looks
 * wrong. The Sun is the one object whose position anybody can state from first principles,
 * so it is the one that can catch the mistake.
 *
 * These recompute the same conversion chain the chart uses, rather than calling into the
 * builder, because the builder needs a 1.7 MB catalogue that a unit test has no business
 * loading.
 */
class SunPositionTest {

    private fun sunAt(
        year: Int, month: Int, day: Int, utcHour: Int, utcMinute: Int,
        latitude: Double, longitude: Double,
    ): DoubleArray {
        val time = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, utcHour, utcMinute, 0)
        }

        val hour = time.get(Calendar.HOUR_OF_DAY) +
            time.get(Calendar.MINUTE) / 60.0 +
            time.get(Calendar.SECOND) / 3600.0

        val sidereal = Astro.sidereal_time(year, month, day, hour) + (longitude / 15.0)

        val sun = Astro.calcPositionSun(Astro.julian_date(time))
        // sun is (right ascension, declination) in DEGREES; the hour angle wants hours.
        val hourAngle = (sidereal - sun[0] / 15.0) * 15.0
        val horizontal = Astro.geoEqua2geoHori(hourAngle, latitude, sun[1])
        // Same south-to-north azimuth conversion the builder does. Asserting against the
        // raw library convention would test the library; asserting against this tests what
        // the chart actually draws.
        return doubleArrayOf(normaliseAzimuth(horizontal[0] + 180.0), horizontal[1])
    }

    /**
     * Greenwich, equinox, local noon.
     *
     * At an equinox the Sun's declination is about zero, so its noon altitude is simply
     * 90 minus the latitude — 38.5° at Greenwich — and it stands due south. Neither figure
     * needs a computer, which is exactly why they are worth asserting.
     */
    @Test
    fun `at the equinox the noon sun stands at ninety minus the latitude`() {
        val position = sunAt(2026, 3, 20, 12, 0, latitude = 51.4778, longitude = 0.0)
        val azimuth = position[0]
        val altitude = position[1]

        assertEquals("altitude should be 90 - 51.48", 38.52, altitude, 1.5)
        assertEquals("the noon sun is due south", 180.0, azimuth, 3.0)
    }

    /** Midnight at the same place: the Sun is as far below the horizon as it was above. */
    @Test
    fun `at the equinox the midnight sun is below the horizon`() {
        val position = sunAt(2026, 3, 21, 0, 0, latitude = 51.4778, longitude = 0.0)
        assertTrue("the Sun must be below the horizon at midnight", position[1] < 0)
        assertEquals(-38.52, position[1], 2.0)
    }

    /**
     * Midsummer is higher than midwinter by twice the tilt of the Earth — 47 degrees.
     * The tilt is a fact about the planet, not about this code.
     */
    @Test
    fun `midsummer noon is forty seven degrees higher than midwinter noon`() {
        val summer = sunAt(2026, 6, 21, 12, 0, 51.4778, 0.0)[1]
        val winter = sunAt(2026, 12, 21, 12, 0, 51.4778, 0.0)[1]
        assertEquals(46.8, summer - winter, 1.5)
    }

    /** North of the Arctic Circle at midsummer, the Sun does not set. */
    @Test
    fun `the midnight sun really does not set inside the arctic circle`() {
        val position = sunAt(2026, 6, 21, 0, 0, latitude = 78.2, longitude = 15.6)
        assertTrue("at 78 N in June the Sun is up at midnight", position[1] > 0)
    }

    /**
     * The same instant seen from ninety degrees of longitude away is six hours of sky
     * away. This is the check on the longitude term, which a chart drawn only ever at
     * home would never exercise.
     */
    @Test
    fun `longitude moves the sun the way the clock does`() {
        val atGreenwich = sunAt(2026, 3, 20, 12, 0, 0.0, 0.0)[1]
        val atNinetyEast = sunAt(2026, 3, 20, 6, 0, 0.0, 90.0)[1]
        assertEquals(
            "noon at 90 E is 06:00 UTC, so the Sun should be at the same height",
            atGreenwich, atNinetyEast, 2.0,
        )
    }

    /**
     * The southern hemisphere puts the noon Sun in the north. A sign error in the
     * latitude term would leave every other test here passing.
     */
    @Test
    fun `south of the equator the noon sun stands in the north`() {
        val position = sunAt(2026, 3, 20, 12, 0, latitude = -33.9, longitude = 0.0)
        assertTrue("altitude should still be well up", position[1] > 40.0)
        val azimuth = normaliseAzimuth(position[0])
        assertTrue(
            "expected a northerly azimuth, got $azimuth",
            azimuth < 30.0 || azimuth > 330.0,
        )
    }

    /**
     * A moment with no round numbers in it, checked against an independent implementation.
     *
     * Every other test here leans on a symmetry — noon, midnight, an equinox — and a chain
     * of conversions can be wrong in a way that survives all of them, because symmetric
     * cases hide sign and offset errors. These figures come from a separate NOAA-style
     * solar position calculation written from scratch, not from this code, and they are
     * the reason the chart's behaviour just after a sunset can be trusted rather than
     * merely believed.
     *
     * 2026-09-10 18:46 UTC at Greenwich is about a quarter of an hour after sunset, which
     * is why the chart draws Venus low in the west and does not draw the Sun at all.
     */
    @Test
    fun `just after sunset the sun is a little below the horizon and in the west`() {
        val position = sunAt(2026, 9, 10, 18, 46, latitude = 51.4778, longitude = 0.0)
        assertEquals("altitude, from an independent calculation", -3.9, position[1], 0.5)
        assertEquals("azimuth, from an independent calculation", 282.6, position[0], 1.5)
        assertTrue("so it must not be drawn", position[1] < 0)
    }

    /** And a quarter of an hour the other way, it is still up. */
    @Test
    fun `just before sunset the sun is still above the horizon`() {
        val position = sunAt(2026, 9, 10, 18, 0, latitude = 51.4778, longitude = 0.0)
        assertTrue("expected the Sun still up at 18:00 UTC", position[1] > 0)
    }
}
