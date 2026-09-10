package com.wanderwildwood.seizu.sky

import kotlin.math.cos
import kotlin.math.sin

/**
 * Flattening the sky onto a disc.
 *
 * Azimuthal equidistant, the projection every paper planisphere uses: the zenith — the
 * point directly overhead — sits at the centre, the horizon is the rim, and distance from
 * the centre is proportional to angle down from overhead. It distorts shapes near the rim,
 * which is the price of a chart you can hold up over your head and match against the sky.
 *
 * Kept separate from the drawing so it can be checked against known positions without a
 * screen. The maths is Timo Engel's, from `DrawArea.horizontal2area`.
 */
data class Projection(
    /** Half the width of the chart disc, in pixels, before zoom. */
    val radiusPx: Float,
    /** Where the middle of the disc sits on the canvas. */
    val centreX: Float,
    val centreY: Float,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /**
     * Which compass bearing is drawn at the bottom of the screen.
     *
     * Turning the chart to face the way you are looking is the whole point of a
     * planisphere; holding one up the wrong way round is how everyone gets Orion backwards.
     */
    val facing: Int = 0,
) {
    /**
     * A point in the sky to a point on the canvas.
     *
     * [azimuth] is degrees clockwise from north, [elevation] degrees above the horizon.
     * An elevation below zero is below the horizon and lands outside the rim, which is
     * deliberate: the caller decides whether to draw it, and clipping it here would make
     * the horizon impossible to draw at all.
     */
    fun project(azimuth: Double, elevation: Double): Pair<Float, Float> {
        val perDegree = (radiusPx * zoom) / 90.0
        val radians = Math.toRadians(azimuth + facing)
        val distance = (90.0 - elevation) * perDegree
        val x = centreX + sin(radians) * distance - panX
        val y = centreY + cos(radians) * distance - panY
        return x.toFloat() to y.toFloat()
    }

    fun project(azimuthElevation: DoubleArray): Pair<Float, Float> =
        project(azimuthElevation[0], azimuthElevation[1])

    /** The radius of the horizon circle as drawn, for the rim and for clipping. */
    val horizonRadiusPx: Float get() = radiusPx * zoom
}

/**
 * How big a star is drawn, from its apparent magnitude.
 *
 * Magnitude runs backwards — brighter is smaller, and negative is brighter still — which
 * is why this reads upside down. The steps are Timo Engel's; what changed is the reason
 * for them. On a screen with sixteen greys a star cannot be dimmer, only smaller, so size
 * is carrying the whole of the brightness information rather than half of it.
 */
fun starRadius(apparentMagnitude: Double, base: Float): Float = base * when {
    apparentMagnitude <= -20 -> 9f
    apparentMagnitude <= -10 -> 7f
    apparentMagnitude <= -3 -> 5f
    apparentMagnitude <= -1 -> 4f
    apparentMagnitude <= 1 -> 3f
    apparentMagnitude <= 3 -> 2f
    apparentMagnitude <= 5 -> 1f
    else -> 0.5f
}

/**
 * The four cardinal points, and where each sits on the rim.
 *
 * North is 0 and the compass runs clockwise through east, which is the convention every
 * azimuth in this app uses.
 */
val CARDINALS: List<Pair<String, Double>> = listOf(
    "N" to 0.0,
    "E" to 90.0,
    "S" to 180.0,
    "W" to 270.0,
)

/** Bearings the chart can be turned to, in the order the button cycles them. */
val FACINGS: List<Pair<String, Int>> = listOf(
    "North up" to 0,
    "East up" to 90,
    "South up" to 180,
    "West up" to 270,
)

/** Wrap any angle into 0 up to but not including 360. */
fun normaliseAzimuth(degrees: Double): Double {
    var value = degrees % 360.0
    if (value < 0) value += 360.0
    return value
}
