package com.wanderwildwood.seizu.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun `the sun is the largest thing on the chart`() {
        assertTrue(starRadius(-26.7, 1f) > starRadius(-1.46, 1f))
    }

    @Test
    fun `nothing is drawn at zero size`() {
        assertTrue(starRadius(30.0, 1f) > 0f)
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
