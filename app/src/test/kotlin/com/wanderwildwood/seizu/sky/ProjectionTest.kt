package com.wanderwildwood.seizu.sky

import com.wanderwildwood.seizu.ui.cardinalName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.hypot

/**
 * The projection is the one piece of this app's own maths that can be wrong quietly.
 *
 * A star in the wrong place still looks exactly like a star, so nothing about the screen
 * would say so. These pin it to points whose answer is known without doing any astronomy.
 */
class ProjectionTest {

    private val projection = Projection(radiusPx = 100f, centreX = 200f, centreY = 300f)

    private fun distanceFromCentre(azimuth: Double, elevation: Double): Float {
        val (x, y) = projection.project(azimuth, elevation)
        return hypot(x - projection.centreX, y - projection.centreY)
    }

    @Test
    fun `the zenith is the centre of the disc`() {
        val (x, y) = projection.project(0.0, 90.0)
        assertEquals(200f, x, 1e-3f)
        assertEquals(300f, y, 1e-3f)
    }

    @Test
    fun `the zenith is the centre whichever way you face`() {
        listOf(0.0, 45.0, 180.0, 271.0).forEach { azimuth ->
            assertEquals(0f, distanceFromCentre(azimuth, 90.0), 1e-3f)
        }
    }

    @Test
    fun `the horizon lands on the rim`() {
        assertEquals(100f, distanceFromCentre(0.0, 0.0), 1e-3f)
        assertEquals(100f, distanceFromCentre(123.0, 0.0), 1e-3f)
    }

    /** Equidistant is the whole point: halfway down the sky is halfway out. */
    @Test
    fun `forty five degrees up is half way out`() {
        assertEquals(50f, distanceFromCentre(0.0, 45.0), 1e-3f)
    }

    @Test
    fun `below the horizon lands outside the rim`() {
        assertTrue(distanceFromCentre(0.0, -10.0) > 100f)
    }

    /**
     * North is drawn at the bottom, not the top.
     *
     * A planisphere is held up overhead and read from underneath, which mirrors it: to
     * match the sky, the direction you are facing goes at the bottom of the chart. Getting
     * this backwards is how everyone ends up with Orion the wrong way round.
     */
    @Test
    fun `north sits below the centre`() {
        val (x, y) = projection.project(0.0, 0.0)
        assertEquals(200f, x, 1e-3f)
        assertTrue("north should be below the centre", y > projection.centreY)
    }

    @Test
    fun `east sits to the right`() {
        val (x, y) = projection.project(90.0, 0.0)
        assertTrue("east should be right of centre", x > projection.centreX)
        assertEquals(300f, y, 1e-3f)
    }

    @Test
    fun `turning the chart moves the compass round with it`() {
        val turned = projection.copy(facing = 180)
        val (_, y) = turned.project(0.0, 0.0)
        assertTrue("with south up, north goes to the top", y < turned.centreY)
    }

    /**
     * Panning moves the whole drawing under the window, zenith included.
     *
     * The zenith is the one point the projection would otherwise nail to the middle of
     * the canvas, which is what makes a zoomed chart impossible to look around.
     */
    @Test
    fun `panning moves the middle of the disc`() {
        val panned = projection.copy(zoom = 2f, panX = 30f, panY = -20f)
        val (x, y) = panned.project(0.0, 90.0)
        assertEquals(170f, x, 1e-3f)
        assertEquals(320f, y, 1e-3f)
    }

    @Test
    fun `panning does not change how far apart two stars are`() {
        val panned = projection.copy(zoom = 2f, panX = 30f, panY = -20f)
        val plain = projection.copy(zoom = 2f)
        val first = plain.project(40.0, 20.0)
        val second = plain.project(200.0, 50.0)
        val firstPanned = panned.project(40.0, 20.0)
        val secondPanned = panned.project(200.0, 50.0)
        assertEquals(
            hypot(first.first - second.first, first.second - second.second),
            hypot(firstPanned.first - secondPanned.first, firstPanned.second - secondPanned.second),
            1e-3f,
        )
    }

    @Test
    fun `the rim is a whole disc away from the zenith`() {
        assertEquals(0.0, projection.distanceFor(90.0), 1e-9)
        assertEquals(100.0, projection.distanceFor(0.0), 1e-9)
        assertEquals(200.0, projection.copy(zoom = 2f).distanceFor(0.0), 1e-9)
    }

    @Test
    fun `zoom pushes the horizon outward but leaves the zenith alone`() {
        val zoomed = projection.copy(zoom = 2f)
        assertEquals(0f, hypot(
            zoomed.project(0.0, 90.0).first - 200f,
            zoomed.project(0.0, 90.0).second - 300f,
        ), 1e-3f)
        assertEquals(200f, zoomed.horizonRadiusPx, 1e-3f)
    }
}

/**
 * Pan is what keeps a zoomed chart usable, and what could lose it altogether.
 *
 * Measured in radii of the unzoomed disc, so the limits are the same on any screen.
 */
class PanTest {

    @Test
    fun `there is nowhere to pan when the whole sky is on the screen`() {
        val (x, y) = clampPan(4f, -3f, 1f)
        assertEquals(0f, x, 1e-6f)
        assertEquals(0f, y, 1e-6f)
    }

    @Test
    fun `a pan within the limit is left alone`() {
        val (x, y) = clampPan(0.5f, -0.5f, 3f)
        assertEquals(0.5f, x, 1e-6f)
        assertEquals(-0.5f, y, 1e-6f)
    }

    /** Clamped as a distance, not axis by axis: the chart is round. */
    @Test
    fun `a pan past the limit is pulled back along its own line`() {
        val (x, y) = clampPan(30f, 40f, 3f)
        assertEquals(2f, hypot(x, y), 1e-5f)
        assertEquals(x / y, 30f / 40f, 1e-5f)
    }

    @Test
    fun `the limit grows with the zoom`() {
        assertEquals(1f, hypot(clampPan(9f, 0f, 2f).first, clampPan(9f, 0f, 2f).second), 1e-5f)
        assertEquals(7f, hypot(clampPan(9f, 0f, 8f).first, clampPan(9f, 0f, 8f).second), 1e-5f)
    }
}

class StarSizeTest {

    /** Magnitude runs backwards: a smaller number is a brighter, larger star. */
    @Test
    fun `brighter stars are drawn bigger`() {
        val sirius = starRadius(-1.46, 1f)
        val polaris = starRadius(1.98, 1f)
        val faint = starRadius(5.5, 1f)
        assertTrue("Sirius should out-draw Polaris", sirius > polaris)
        assertTrue("Polaris should out-draw a magnitude 5.5 star", polaris > faint)
    }

    @Test
    fun `the ladder never goes back up`() {
        val magnitudes = listOf(-26.7, -1.46, -0.7, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0)
        magnitudes.zipWithNext().forEach { (brighter, fainter) ->
            assertTrue(
                "magnitude $fainter should not out-draw $brighter",
                starRadius(brighter, 1f) >= starRadius(fainter, 1f),
            )
        }
    }

    /**
     * The whole ladder spans the six magnitudes of star the chart shows, and no more.
     *
     * Upstream spends four rungs of its ladder above magnitude -1, where the only things
     * are the Sun and the Moon. Those are drawn by [bodyRadius] here, so a star four
     * times the width of the faintest one is as heavy as the chart ever gets.
     */
    @Test
    fun `the brightest star is not many times the faintest`() {
        assertTrue(starRadius(-1.5, 1f) <= 4f * starRadius(6.5, 1f))
    }

    @Test
    fun `nothing is drawn at zero size`() {
        assertTrue(starRadius(30.0, 1f) > 0f)
    }

    @Test
    fun `the sun and moon are drawn larger than any star`() {
        assertTrue(bodyRadius(BodyKind.SUN, -26.7, 1f) > starRadius(-1.46, 1f))
        assertTrue(bodyRadius(BodyKind.MOON, -12.7, 1f) > starRadius(-1.46, 1f))
    }

    /** A planet has to carry a ring round it, so it starts bigger than a bright star. */
    @Test
    fun `a planet is larger than the brightest star`() {
        assertTrue(bodyRadius(BodyKind.PLANET, 5.0, 1f) > starRadius(-1.46, 1f))
    }

    @Test
    fun `a brighter planet is drawn larger`() {
        assertTrue(
            bodyRadius(BodyKind.PLANET, -4.0, 1f) > bodyRadius(BodyKind.PLANET, 1.0, 1f),
        )
    }

    @Test
    fun `mark weight scales everything together`() {
        val fine = MarkWeight.FINE.scale
        val bold = MarkWeight.BOLD.scale
        assertTrue(fine < bold)
        assertEquals(
            starRadius(2.0, bold) / starRadius(2.0, fine),
            bodyRadius(BodyKind.SUN, -26.7, bold) / bodyRadius(BodyKind.SUN, -26.7, fine),
            1e-5f,
        )
    }
}

/**
 * Where a constellation's name goes, which is a question about the sphere.
 *
 * Averaging right ascension as a number is the trap: it is an angle that wraps, and a
 * constellation straddling 0h averages out to the far side of the sky, which is how a
 * name ends up under a different constellation with nothing on the screen looking wrong.
 */
class MeanDirectionTest {

    private fun point(raHours: Double, declination: Double) =
        doubleArrayOf(raHours, declination)

    @Test
    fun `an empty scatter has no middle`() {
        assertEquals(null, meanDirection(emptyList()))
    }

    @Test
    fun `one point is its own middle`() {
        val middle = meanDirection(listOf(point(6.0, 30.0)))!!
        assertEquals(6.0, middle[0], 1e-6)
        assertEquals(30.0, middle[1], 1e-6)
    }

    @Test
    fun `the middle of two hour angles is between them`() {
        val middle = meanDirection(listOf(point(4.0, 0.0), point(6.0, 0.0)))!!
        assertEquals(5.0, middle[0], 1e-6)
        assertEquals(0.0, middle[1], 1e-6)
    }

    @Test
    fun `a scatter across zero hours averages to zero hours and not to noon`() {
        val middle = meanDirection(listOf(point(23.0, 0.0), point(1.0, 0.0)))!!
        assertEquals(0.0, middle[0], 1e-6)
    }

    @Test
    fun `declination is averaged too`() {
        val middle = meanDirection(listOf(point(12.0, 20.0), point(12.0, 40.0)))!!
        assertEquals(12.0, middle[0], 1e-6)
        assertEquals(30.0, middle[1], 1e-6)
    }

    @Test
    fun `a scatter that cancels itself out has no middle`() {
        assertEquals(null, meanDirection(listOf(point(0.0, 90.0), point(0.0, -90.0))))
    }
}

class AzimuthTest {

    @Test
    fun `negative bearings wrap round`() {
        assertEquals(350.0, normaliseAzimuth(-10.0), 1e-9)
    }

    @Test
    fun `a full turn is zero`() {
        assertEquals(0.0, normaliseAzimuth(360.0), 1e-9)
    }

    @Test
    fun `more than a full turn wraps`() {
        assertEquals(10.0, normaliseAzimuth(370.0), 1e-9)
    }

    @Test
    fun `the four cardinals are ninety degrees apart and start at north`() {
        assertEquals(4, CARDINALS.size)
        assertEquals(0.0, CARDINALS.first { it.first == "N" }.second, 1e-9)
        assertEquals(90.0, CARDINALS.first { it.first == "E" }.second, 1e-9)
        assertEquals(180.0, CARDINALS.first { it.first == "S" }.second, 1e-9)
        assertEquals(270.0, CARDINALS.first { it.first == "W" }.second, 1e-9)
    }

    @Test
    fun `every cardinal key has a word to draw`() {
        val words = CARDINALS.map { cardinalName(it.first) }
        assertEquals("four different words", 4, words.toSet().size)
    }
}

/**
 * The Bright Star Catalogue's fixed-width columns run the Flamsteed number straight into
 * the Bayer letter. These pin the split back apart.
 */
class DesignationTest {

    private fun designation(raw: String) =
        SkyStar(0.0, 0.0, 0.0, null, 1, raw).designation

    @Test
    fun `a number running into a letter is separated`() {
        assertEquals("91 The Her", designation("91The Her"))
    }

    @Test
    fun `a designation that already has its space is left alone`() {
        assertEquals("61 Cyg", designation("61 Cyg"))
    }

    @Test
    fun `a bare Bayer letter is left alone`() {
        assertEquals("Alp CMa", designation("Alp CMa"))
    }

    @Test
    fun `a bare number is left alone`() {
        assertEquals("61", designation("61"))
    }

    @Test
    fun `an empty designation stays empty`() {
        assertEquals("", designation(""))
    }
}

/**
 * Stepping the charted moment.
 *
 * The thing worth pinning is that a day step keeps the hour of the clock. Adding
 * 24 * 3600 * 1000 milliseconds does not, on the two nights a year the clocks move, and
 * "the same time tomorrow" is the whole question this control exists to answer.
 */
class TimeStepTest {

    @Test
    fun `an hour forward is an hour later`() {
        val from = GregorianCalendar(2026, Calendar.SEPTEMBER, 22, 21, 0)
        val next = steppedTime(from, hours = 1, days = 0)
        assertEquals(22, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(22, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `stepping back over midnight lands on the day before`() {
        val from = GregorianCalendar(2026, Calendar.SEPTEMBER, 22, 0, 30)
        val next = steppedTime(from, hours = -1, days = 0)
        assertEquals(23, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(21, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `a day forward over the end of a month rolls the month`() {
        val from = GregorianCalendar(2026, Calendar.SEPTEMBER, 30, 21, 0)
        val next = steppedTime(from, hours = 0, days = 1)
        assertEquals(Calendar.OCTOBER, next.get(Calendar.MONTH))
        assertEquals(1, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `a day forward over a daylight-saving change keeps the hour`() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        // The US clocks go back on 1 November 2026, so this day is 25 hours long.
        val from = GregorianCalendar(newYork).apply { set(2026, Calendar.OCTOBER, 31, 21, 0, 0) }
        val next = steppedTime(from, hours = 0, days = 1)
        assertEquals(21, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(1, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, next.get(Calendar.MONTH))
    }

    @Test
    fun `the original is not moved`() {
        val from = GregorianCalendar(2026, Calendar.SEPTEMBER, 22, 21, 0)
        steppedTime(from, hours = 5, days = 3)
        assertEquals(21, from.get(Calendar.HOUR_OF_DAY))
        assertEquals(22, from.get(Calendar.DAY_OF_MONTH))
    }
}

/**
 * The Moon's phase.
 *
 * All four quarters are fixed points with an answer known without any astronomy: the lit
 * fraction is a function of one angle, and the four values of that angle that matter are
 * the ones the words new, first quarter, full and last quarter name.
 */
class MoonPhaseTest {

    @Test
    fun `together in the sky is new moon`() {
        val phase = moonPhase(sunLongitude = 100.0, moonLongitude = 100.0, moonLatitude = 0.0)
        assertEquals(0.0, phase.illuminated, 1e-9)
    }

    @Test
    fun `opposite is full moon`() {
        val phase = moonPhase(sunLongitude = 100.0, moonLongitude = 280.0, moonLatitude = 0.0)
        assertEquals(1.0, phase.illuminated, 1e-9)
    }

    @Test
    fun `a quarter turn ahead is half lit and waxing`() {
        val phase = moonPhase(sunLongitude = 100.0, moonLongitude = 190.0, moonLatitude = 0.0)
        assertEquals(0.5, phase.illuminated, 1e-9)
        assertTrue(phase.waxing)
    }

    @Test
    fun `a quarter turn behind is half lit and waning`() {
        val phase = moonPhase(sunLongitude = 100.0, moonLongitude = 10.0, moonLatitude = 0.0)
        assertEquals(0.5, phase.illuminated, 1e-9)
        assertTrue(!phase.waxing)
    }

    @Test
    fun `the wrap at zero degrees does not flip waxing`() {
        // Moon at 10 degrees, Sun at 350: the Moon is 20 degrees AHEAD, not 340 behind.
        val phase = moonPhase(sunLongitude = 350.0, moonLongitude = 10.0, moonLatitude = 0.0)
        assertTrue(phase.waxing)
        assertTrue("a young crescent is barely lit", phase.illuminated < 0.05)
    }

    @Test
    fun `each phase gets the name an almanac would print`() {
        fun name(sun: Double, moon: Double) = phaseName(moonPhase(sun, moon, 0.0))
        assertEquals(PhaseName.NEW, name(0.0, 0.0))
        assertEquals(PhaseName.WAXING_CRESCENT, name(0.0, 45.0))
        assertEquals(PhaseName.FIRST_QUARTER, name(0.0, 90.0))
        assertEquals(PhaseName.WAXING_GIBBOUS, name(0.0, 135.0))
        assertEquals(PhaseName.FULL, name(0.0, 180.0))
        assertEquals(PhaseName.WANING_GIBBOUS, name(0.0, 225.0))
        assertEquals(PhaseName.LAST_QUARTER, name(0.0, 270.0))
        assertEquals(PhaseName.WANING_CRESCENT, name(0.0, 315.0))
    }

    @Test
    fun `the name and the drawing agree about new and full`() {
        // The canvas draws an empty ring below one threshold and a solid disc above the
        // other. If the words used different numbers, a solid disc could be called
        // gibbous, which is the one way this can be wrong without looking wrong.
        assertEquals(PhaseName.NEW, phaseName(MoonPhase(MOON_NEW_BELOW - 0.001, waxing = true)))
        assertEquals(PhaseName.FULL, phaseName(MoonPhase(MOON_FULL_ABOVE + 0.001, waxing = true)))
        assertTrue(phaseName(MoonPhase(MOON_NEW_BELOW + 0.001, waxing = true)) != PhaseName.NEW)
        assertTrue(phaseName(MoonPhase(MOON_FULL_ABOVE - 0.001, waxing = true)) != PhaseName.FULL)
    }

    @Test
    fun `the Moon's own latitude never lights more than the angle allows`() {
        // At full, five degrees off the ecliptic leaves it a shade short of wholly lit.
        val phase = moonPhase(sunLongitude = 0.0, moonLongitude = 180.0, moonLatitude = 5.0)
        assertTrue(phase.illuminated < 1.0)
        assertTrue(phase.illuminated > 0.99)
    }
}

class PlaceTest {

    @Test
    fun `the hemispheres follow the signs`() {
        val greenwich = Place(51.4778, -0.0014)
        assertTrue(greenwich.north)
        assertTrue(!greenwich.east)
        val sydney = Place(-33.87, 151.21)
        assertTrue(!sydney.north)
        assertTrue(sydney.east)
    }

    @Test
    fun `the equator and the prime meridian read north and east`() {
        assertTrue(Place(0.0, 0.0).north)
        assertTrue(Place(0.0, 0.0).east)
    }
}
