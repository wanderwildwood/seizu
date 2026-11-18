package com.wanderwildwood.seizu.sky

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * How bright a planet is tonight, from where it is.
 *
 * The planets underneath carry one fixed magnitude each, their brightest ever, which is no
 * use for saying how bright Mars is this week: it ranges from nearly -3 to nearly +2. These
 * are the Astronomical Almanac's formulas as Meeus gives them (Astronomical Algorithms,
 * chapter 41): a magnitude at one astronomical unit from the Sun and the Earth, made fainter
 * by the real distances and by how much of the lit face is turned away. Saturn's rings add
 * their own light, by how far they are tilted toward us. The small term for the angle
 * between the Sun's and the Earth's view of the rings is left out: it is a few hundredths.
 *
 * [id] is the planet's Wikidata id, the one name the planets underneath carry that does not
 * depend on the language. Distances are in astronomical units: [sunDistance] the planet's
 * from the Sun, [earthDistance] its from the Earth, [earthSunDistance] the Earth's from the
 * Sun. [eclipticLongitude] and [eclipticLatitude] are its geocentric position in degrees,
 * needed only for Saturn's rings. An id not known here gets [fallback] back.
 */
fun planetMagnitude(
    id: String,
    sunDistance: Double,
    earthDistance: Double,
    earthSunDistance: Double,
    eclipticLongitude: Double,
    eclipticLatitude: Double,
    julianDay: Double,
    fallback: Double,
): Double {
    val r = sunDistance
    val delta = earthDistance
    if (!(r > 0) || !(delta > 0)) return fallback
    val distance = 5 * log10(r * delta)
    // The phase angle: Sun and Earth as seen from the planet.
    val cosine = ((r * r + delta * delta - earthSunDistance * earthSunDistance) / (2 * r * delta)).coerceIn(-1.0, 1.0)
    val i = Math.toDegrees(acos(cosine))
    val magnitude = when (id) {
        MERCURY -> -0.42 + distance + 0.0380 * i - 0.000273 * i * i + 0.000002 * i * i * i
        VENUS -> -4.40 + distance + 0.0009 * i + 0.000239 * i * i - 0.00000065 * i * i * i
        MARS -> -1.52 + distance + 0.016 * i
        JUPITER -> -9.40 + distance + 0.005 * i
        SATURN -> {
            val sinB = sin(saturnRingTilt(eclipticLongitude, eclipticLatitude, julianDay))
            -8.88 + distance - 2.60 * abs(sinB) + 1.25 * sinB * sinB
        }
        URANUS -> -7.19 + distance
        NEPTUNE -> -6.87 + distance
        else -> return fallback
    }
    return if (magnitude.isFinite()) magnitude else fallback
}

/**
 * The tilt of Saturn's rings toward the Earth, in radians (Meeus, chapter 45): nought when
 * they are edge on and the planet is at its faintest, about 27 degrees at the widest.
 */
internal fun saturnRingTilt(eclipticLongitude: Double, eclipticLatitude: Double, julianDay: Double): Double {
    val t = (julianDay - 2451545.0) / 36525.0
    val inclination = Math.toRadians(28.075216 - 0.012998 * t + 0.000004 * t * t)
    val node = Math.toRadians(169.508470 + 1.394681 * t + 0.000412 * t * t)
    val lambda = Math.toRadians(eclipticLongitude)
    val beta = Math.toRadians(eclipticLatitude)
    val sinB = sin(inclination) * cos(beta) * sin(lambda - node) - cos(inclination) * sin(beta)
    return kotlin.math.asin(sinB.coerceIn(-1.0, 1.0))
}

private const val MERCURY = "Q308"
private const val VENUS = "Q313"
private const val MARS = "Q111"
private const val JUPITER = "Q319"
private const val SATURN = "Q193"
private const val URANUS = "Q324"
private const val NEPTUNE = "Q332"
