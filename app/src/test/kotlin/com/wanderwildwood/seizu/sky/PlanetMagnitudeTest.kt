package com.wanderwildwood.seizu.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlanetMagnitudeTest {

    @Test
    fun marsAtTheCloseOppositionOf2003() {
        // 27 August 2003: Mars 1.381 au from the Sun, 0.373 from us; it shone at about -2.9.
        val m = planetMagnitude("Q111", 1.381, 0.373, 1.0102, 334.6, -6.0, 2452879.0, 99.0)
        assertEquals(-2.9, m, 0.15)
    }

    @Test
    fun marsFarAcrossTheSunIsFaint() {
        // Near conjunction Mars is around +1.5, nothing like its brightest.
        val m = planetMagnitude("Q111", 1.5, 2.4, 1.0, 0.0, 0.0, 2461000.0, 99.0)
        assertTrue("was $m", m > 1.0 && m < 2.0)
    }

    @Test
    fun jupiterAtOpposition2022() {
        // 26 September 2022: 4.95 au from the Sun, 3.95 from us, about -2.9.
        val m = planetMagnitude("Q319", 4.95, 3.95, 1.003, 1.0, -1.2, 2459849.0, 99.0)
        assertEquals(-2.9, m, 0.15)
    }

    @Test
    fun saturnsRingsEdgeOnAddNothing() {
        // The rings' node: looking along it they are edge on.
        val jd = 2451545.0
        val tilt = saturnRingTilt(169.508470, 0.0, jd)
        assertEquals(0.0, tilt, 1e-9)
        val wide = saturnRingTilt(169.508470 + 90.0, 0.0, jd)
        assertEquals(28.0, abs(Math.toDegrees(wide)), 0.2)
        // Wide open rings make the planet brighter.
        val edgeOn = planetMagnitude("Q193", 9.5, 8.5, 1.0, 169.508470, 0.0, jd, 99.0)
        val open = planetMagnitude("Q193", 9.5, 8.5, 1.0, 169.508470 + 90.0, 0.0, jd, 99.0)
        assertTrue("edge on $edgeOn, open $open", open < edgeOn - 0.8)
    }

    @Test
    fun anUnknownPlanetKeepsItsOwnFigure() {
        assertEquals(7.0, planetMagnitude("Q0", 1.0, 1.0, 1.0, 0.0, 0.0, 2451545.0, 7.0), 0.0)
        assertEquals(7.0, planetMagnitude("Q111", 0.0, 1.0, 1.0, 0.0, 0.0, 2451545.0, 7.0), 0.0)
    }
}
