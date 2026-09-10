package com.wanderwildwood.seizu.sky

import org.tengel.planisphere.Astro
import org.tengel.planisphere.Catalog
import org.tengel.planisphere.ConstellationDb
import org.tengel.planisphere.Planet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.abs

/**
 * Turns a time and a place into a [Scene].
 *
 * All the hard sums are Timo Engel's Java, called rather than reimplemented. What is here
 * is the assembly: which objects to include, where they are in the observer's sky, and
 * what each is called.
 */
class SkyBuilder(
    private val catalog: Catalog,
    private val constellations: ConstellationDb,
) {

    fun build(
        time: GregorianCalendar,
        latitude: Double,
        longitude: Double,
        layers: Layers,
    ): Scene {
        val siderealTime = localSiderealTime(time, longitude)

        // geoEqua2geoHori answers an azimuth measured from SOUTH, increasing westward —
        // the old astronomical convention. Everything above this line works in the
        // navigator's convention instead: zero at north, increasing eastward, which is
        // what a compass reads and what the object details claim to be showing. Converting
        // once here is the only place the two can meet without one of them being a lie.
        fun toHorizontal(rightAscension: Double, declination: Double): DoubleArray {
            val hourAngle = (siderealTime - rightAscension) * 15.0
            val horizontal = Astro.geoEqua2geoHori(hourAngle, latitude, declination)
            return doubleArrayOf(normaliseAzimuth(horizontal[0] + 180.0), horizontal[1])
        }

        val stars = catalog.get()
            .asSequence()
            .filter { it.apparentMagnitude <= layers.magnitudeLimit }
            .map { entry ->
                val position = toHorizontal(entry.rightAscension, entry.declination)
                SkyStar(
                    azimuth = position[0],
                    elevation = position[1],
                    magnitude = entry.apparentMagnitude,
                    label = entry.name,
                    hr = entry.hr,
                    bayerFlamsteed = entry.bayerFlamsteed ?: "",
                )
            }
            .filter { it.isAboveHorizon }
            .toList()

        val lines = buildList {
            if (layers.constellationLines) {
                addAll(constellationLines(::toHorizontal))
            }
            if (layers.equator) {
                add(circleOfDeclination(0.0, LineKind.EQUATOR, ::toHorizontal))
            }
            if (layers.ecliptic) {
                add(eclipticLine(::toHorizontal))
            }
            if (layers.grid) {
                addAll(declinationGrid(::toHorizontal))
            }
        }

        // Filtered the same way the stars are. Anything below the horizon projects to
        // outside the rim, and drawing it there puts the Sun on the page next to the
        // chart in the middle of the night.
        val bodies = if (layers.solarSystem) {
            solarSystem(time, layers, ::toHorizontal).filter { it.isAboveHorizon }
        } else {
            emptyList()
        }

        return Scene(
            stars = stars,
            bodies = bodies,
            lines = lines,
            whenText = WHEN_FORMAT.format(time.time),
            whereText = placeText(latitude, longitude),
        )
    }

    /**
     * Local sidereal time in hours: which right ascension is currently on the meridian.
     *
     * The calendar's own offsets are subtracted rather than the time converted, because
     * `Calendar` reports DST and zone separately and a phone that has just crossed a
     * boundary can disagree with itself about which applies.
     */
    private fun localSiderealTime(time: GregorianCalendar, longitude: Double): Double {
        val utcHour = time.get(Calendar.HOUR_OF_DAY) +
            (time.get(Calendar.MINUTE) / 60.0) +
            (time.get(Calendar.SECOND) / 3600.0) -
            (time.get(Calendar.DST_OFFSET) / 3_600_000.0) -
            (time.get(Calendar.ZONE_OFFSET) / 3_600_000.0)

        val sidereal = Astro.sidereal_time(
            time.get(Calendar.YEAR),
            time.get(Calendar.MONTH) + 1,
            time.get(Calendar.DAY_OF_MONTH),
            utcHour,
        )
        return sidereal + (longitude / 15.0)
    }

    private fun constellationLines(
        toHorizontal: (Double, Double) -> DoubleArray,
    ): List<SkyLine> = constellations.get().mapNotNull { constellation ->
        val points = constellation.mLine.map { entry ->
            toHorizontal(entry.rightAscension, entry.declination)
        }
        // A constellation with every star below the horizon is not drawn at all. One with
        // some above is drawn whole, so a figure rising over the horizon is not chopped
        // into disconnected fragments that read as a different shape.
        if (points.none { it[1] >= 0 }) null
        else SkyLine(points, LineKind.CONSTELLATION, constellations.getName(constellation.mName, 2))
    }

    /** A circle of constant declination, traced right around the sky. */
    private fun circleOfDeclination(
        declination: Double,
        kind: LineKind,
        toHorizontal: (Double, Double) -> DoubleArray,
    ): SkyLine {
        val points = (0..72).map { step ->
            toHorizontal(step * 24.0 / 72.0, declination)
        }
        return SkyLine(points, kind)
    }

    private fun declinationGrid(
        toHorizontal: (Double, Double) -> DoubleArray,
    ): List<SkyLine> = listOf(-60.0, -30.0, 0.0, 30.0, 60.0).map {
        circleOfDeclination(it, LineKind.GRID, toHorizontal)
    }

    /**
     * The ecliptic: the path the Sun takes, and near enough the path of everything else
     * in the solar system. Traced as ecliptic longitude converted to equatorial.
     */
    private fun eclipticLine(
        toHorizontal: (Double, Double) -> DoubleArray,
    ): SkyLine {
        val points = (0..72).map { step ->
            // geoEcl2geoEqua answers (right ascension, declination) in DEGREES, and the
            // horizontal conversion wants right ascension in HOURS. Getting either of
            // those the wrong way round puts everything somewhere entirely plausible and
            // entirely wrong, which is the whole difficulty of checking a star chart.
            val equatorial = Astro.geoEcl2geoEqua(0.0, step * 5.0)
            toHorizontal(equatorial[0] / 15.0, equatorial[1])
        }
        return SkyLine(points, LineKind.ECLIPTIC)
    }

    private fun solarSystem(
        time: GregorianCalendar,
        layers: Layers,
        toHorizontal: (Double, Double) -> DoubleArray,
    ): List<SkyBody> = buildList {
        val julianDay = Astro.julian_date(time)

        // (right ascension, declination) in degrees, already equatorial.
        val sun = Astro.calcPositionSun(julianDay)
        toHorizontal(sun[0] / 15.0, sun[1]).let {
            add(SkyBody(it[0], it[1], -26.7, "Sun", BodyKind.SUN))
        }

        // The Moon comes back ECLIPTIC — (beta, lambda, distance) — unlike the Sun, so it
        // has to be converted before it means anything in the observer's sky.
        val moon = Astro.calcPositionMoon(julianDay)
        val moonEquatorial = Astro.geoEcl2geoEqua(moon[0], moon[1])
        toHorizontal(moonEquatorial[0] / 15.0, moonEquatorial[1]).let {
            add(SkyBody(it[0], it[1], -12.7, "Moon", BodyKind.MOON))
        }

        val earth = Planet.sEarth ?: return@buildList
        earth.calcHeliocentric(time)
        Planet.sPlanets?.forEach { planet ->
            planet.calcHeliocentric(time)
            planet.calcGeocentric(earth)
            if (planet.mApparentMagnitude <= layers.magnitudeLimit) {
                // mRa is in degrees; the conversion wants hours.
                val position = toHorizontal(planet.mRa / 15.0, planet.mDeclination)
                add(
                    SkyBody(
                        azimuth = position[0],
                        elevation = position[1],
                        magnitude = planet.mApparentMagnitude,
                        label = planet.mName,
                        kind = BodyKind.PLANET,
                    )
                )
            }
        }
    }

    private companion object {
        val WHEN_FORMAT = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

        fun placeText(latitude: Double, longitude: Double): String {
            val ns = if (latitude >= 0) "N" else "S"
            val ew = if (longitude >= 0) "E" else "W"
            return "%.2f°%s %.2f°%s".format(abs(latitude), ns, abs(longitude), ew)
        }
    }
}
