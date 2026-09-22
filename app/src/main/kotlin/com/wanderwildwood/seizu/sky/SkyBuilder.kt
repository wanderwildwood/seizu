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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
            // Boundaries first so that everything else is drawn over them: they are the
            // faintest thing on the chart and the only one that covers the whole sky.
            if (layers.constellationBoundaries) {
                addAll(constellationBoundaries(::toHorizontal))
            }
            if (layers.constellationLines) {
                addAll(constellationLines(layers.naming, ::toHorizontal))
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

        val names = if (layers.constellationNames) {
            constellationNames(layers.naming, ::toHorizontal)
        } else {
            emptyList()
        }

        return Scene(
            stars = stars,
            bodies = bodies,
            lines = lines,
            names = names,
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
        naming: ConstellationNaming,
        toHorizontal: (Double, Double) -> DoubleArray,
    ): List<SkyLine> = constellations.get().mapNotNull { constellation ->
        val points = constellation.mLine.map { entry ->
            toHorizontal(entry.rightAscension, entry.declination)
        }
        // A constellation with every star below the horizon is not drawn at all. One with
        // some above is drawn whole, so a figure rising over the horizon is not chopped
        // into disconnected fragments that read as a different shape.
        if (points.none { it[1] >= 0 }) null
        else SkyLine(
            points,
            LineKind.CONSTELLATION,
            constellations.getName(constellation.mName, naming.column),
        )
    }

    /**
     * The outlines the IAU drew round the constellations, closed back to their first point.
     *
     * Upstream's layer, and upstream's data file. Where the figure lines say what a
     * constellation looks like, these say where it ends, which is the only way to answer
     * "which constellation is that in" from a chart. Off by default: eighty-eight
     * polygons is a great deal of ink for a 4.3" panel.
     */
    private fun constellationBoundaries(
        toHorizontal: (Double, Double) -> DoubleArray,
    ): List<SkyLine> = constellations.get().mapNotNull { constellation ->
        val vertices = constellations.getBoundary(constellation.mName).orEmpty()
        if (vertices.isEmpty()) return@mapNotNull null
        val points = buildList {
            vertices.forEach { add(toHorizontal(it[0], it[1])) }
            // Closed back to where it started: the last side of the polygon is a boundary
            // like every other, and leaving it out opens a gap in the one layer whose
            // whole job is to say where one constellation stops and the next begins.
            val first = vertices.first()
            add(toHorizontal(first[0], first[1]))
        }
        if (points.none { it[1] >= 0 }) null else SkyLine(points, LineKind.BOUNDARY)
    }

    /**
     * Where each constellation's name goes.
     *
     * In the middle of its boundary, as upstream does it, but averaged on the sphere
     * rather than in screen pixels: a constellation whose boundary crosses 0h right
     * ascension averages out to the opposite side of the sky if the numbers are simply
     * added up, and a constellation halfway over the horizon would drag its own name down
     * to the rim if the middle were taken of the part that happens to be showing.
     *
     * A constellation with no boundary in the file falls back to the middle of its figure,
     * so it is still named rather than silently left out.
     */
    private fun constellationNames(
        naming: ConstellationNaming,
        toHorizontal: (Double, Double) -> DoubleArray,
    ): List<SkyLabel> = constellations.get().mapNotNull { constellation ->
        val boundary = constellations.getBoundary(constellation.mName)
            ?.map { doubleArrayOf(it[0], it[1]) }
            .orEmpty()
        val figure = constellation.mLine.map {
            doubleArrayOf(it.rightAscension, it.declination)
        }
        val centre = meanDirection(boundary.ifEmpty { figure }) ?: return@mapNotNull null
        val position = toHorizontal(centre[0], centre[1])
        // Named only while the middle of it is properly up. A name sitting on the rim
        // belongs to a constellation that is three-quarters below the horizon.
        if (position[1] < NAME_FLOOR) return@mapNotNull null
        SkyLabel(position[0], position[1], constellations.getName(constellation.mName, naming.column))
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
        // The phase wants both of them back on the ecliptic, where the angle between them
        // is the whole of it. The Sun is asked a second time, in those terms.
        val sunEcliptic = Astro.calcPositionSunEcliptic(julianDay)
        val phase = moonPhase(
            sunLongitude = sunEcliptic[1],
            moonLongitude = moon[1],
            moonLatitude = moon[0],
        )
        toHorizontal(moonEquatorial[0] / 15.0, moonEquatorial[1]).let {
            add(SkyBody(it[0], it[1], -12.7, "Moon", BodyKind.MOON, phase))
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
        /** Degrees above the horizon the middle of a constellation must be, to be named. */
        const val NAME_FLOOR = 4.0

        val WHEN_FORMAT = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

        fun placeText(latitude: Double, longitude: Double): String {
            val ns = if (latitude >= 0) "N" else "S"
            val ew = if (longitude >= 0) "E" else "W"
            return "%.2f°%s %.2f°%s".format(abs(latitude), ns, abs(longitude), ew)
        }
    }
}

/**
 * The middle of a scatter of sky positions, as a direction rather than as two averages.
 *
 * Each (right ascension in hours, declination in degrees) is turned into a unit vector,
 * the vectors are added, and the sum is turned back. Averaging the two numbers instead
 * works everywhere except across 0h and near the poles, which is to say it works
 * everywhere except where it matters: the mean of 23h and 1h is noon, on the far side of
 * the sky from both.
 *
 * Null for an empty list, and for the vanishingly unlikely scatter that cancels itself
 * out exactly and so has no middle to point at.
 */
fun meanDirection(raDecDegrees: List<DoubleArray>): DoubleArray? {
    if (raDecDegrees.isEmpty()) return null
    var x = 0.0
    var y = 0.0
    var z = 0.0
    raDecDegrees.forEach { point ->
        val ra = Math.toRadians(point[0] * 15.0)
        val dec = Math.toRadians(point[1])
        x += cos(dec) * cos(ra)
        y += cos(dec) * sin(ra)
        z += sin(dec)
    }
    val length = hypot(hypot(x, y), z)
    if (length < 1e-9) return null
    val declination = Math.toDegrees(asin(z / length))
    val rightAscension = Math.toDegrees(atan2(y, x)) / 15.0
    return doubleArrayOf((rightAscension + 24.0) % 24.0, declination)
}
