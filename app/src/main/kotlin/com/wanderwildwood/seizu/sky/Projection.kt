package com.wanderwildwood.seizu.sky

import kotlin.math.cos
import kotlin.math.hypot
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
    /** Where the middle of the canvas is. */
    val centreX: Float,
    val centreY: Float,
    val zoom: Float = 1f,
    /**
     * How far the view has been dragged over the chart, in pixels.
     *
     * Positive is looking further right and further down the drawing, which is what a
     * finger dragging left and up does. Zoom without this would be a magnifying glass
     * nailed to the zenith.
     */
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
    /** Where the middle of the disc — the zenith — actually lands, once panned. */
    val zenithX: Float get() = centreX - panX
    val zenithY: Float get() = centreY - panY

    /**
     * A point in the sky to a point on the canvas.
     *
     * [azimuth] is degrees clockwise from north, [elevation] degrees above the horizon.
     * An elevation below zero is below the horizon and lands outside the rim, which is
     * deliberate: the caller decides whether to draw it, and clipping it here would make
     * the horizon impossible to draw at all.
     */
    fun project(azimuth: Double, elevation: Double): Pair<Float, Float> {
        val radians = Math.toRadians(azimuth + facing)
        val distance = distanceFor(elevation)
        val x = zenithX + sin(radians) * distance
        val y = zenithY + cos(radians) * distance
        return x.toFloat() to y.toFloat()
    }

    fun project(azimuthElevation: DoubleArray): Pair<Float, Float> =
        project(azimuthElevation[0], azimuthElevation[1])

    /**
     * How far out from the zenith a given elevation falls, in pixels.
     *
     * The rim and the circles of altitude are drawn straight from this rather than by
     * projecting a ring of points: they are true circles on this projection, and a
     * hundred-and-one-point polygon of a circle is a hundred chances to show a corner.
     */
    fun distanceFor(elevation: Double): Double = (90.0 - elevation) * (radiusPx * zoom) / 90.0

    /** The radius of the horizon circle as drawn, for the rim and for clipping. */
    val horizonRadiusPx: Float get() = radiusPx * zoom
}

/** The chart can be pushed in this far and no further. */
const val MIN_ZOOM = 1f
const val MAX_ZOOM = 8f

/** What one press of the zoom buttons does. */
const val ZOOM_STEP = 1.5f

/**
 * Keep the chart under the screen.
 *
 * Pan is held in radii of the unzoomed disc rather than in pixels, so it survives a
 * rotation or a different screen without meaning something else afterwards. The limit is
 * one radius per turn of zoom: at zoom 1 the whole disc is on the screen and there is
 * nowhere to go, and at any zoom above that the rim can be brought to the edge of the
 * screen but never past the middle of it. Dragging the sky clean off the screen and
 * being left with a white page is the one thing a chart you are holding up in the dark
 * must not do.
 */
fun clampPan(panX: Float, panY: Float, zoom: Float): Pair<Float, Float> {
    val limit = (zoom - 1f).coerceAtLeast(0f)
    val distance = hypot(panX, panY)
    if (distance <= limit || distance == 0f) return panX to panY
    val scale = limit / distance
    return panX * scale to panY * scale
}

/**
 * How big a star is drawn, from its apparent magnitude.
 *
 * Magnitude runs backwards — brighter is smaller, and negative is brighter still — which
 * is why this reads upside down. On a screen with sixteen greys a star cannot be dimmer,
 * only smaller, so size is carrying the whole of the brightness information rather than
 * half of it.
 *
 * The ladder is upstream's, re-cut. His has eight rungs spread over forty magnitudes
 * because the Sun climbs it too; here the Sun and the planets are drawn by
 * [bodyRadius] instead, which leaves the whole ladder for the six magnitudes of star the
 * chart actually shows. The rungs are a magnitude apart and the steps between them are
 * small, so the brightest star is about four times the width of the faintest rather than
 * six times, and nothing on the chart is a blob.
 */
fun starRadius(apparentMagnitude: Double, base: Float): Float = base * when {
    apparentMagnitude <= -1.5 -> 3.0f
    apparentMagnitude <= -0.5 -> 2.6f
    apparentMagnitude <= 0.5 -> 2.25f
    apparentMagnitude <= 1.5 -> 1.9f
    apparentMagnitude <= 2.5 -> 1.6f
    apparentMagnitude <= 3.5 -> 1.35f
    apparentMagnitude <= 4.5 -> 1.1f
    apparentMagnitude <= 5.5 -> 0.9f
    else -> 0.75f
}

/**
 * How big a ring the Sun, the Moon or a planet gets.
 *
 * Not from magnitude, which for the Sun is -26.7 and would draw a coin over the middle of
 * the chart. These are the objects you are looking for by name rather than by brightness,
 * so they are drawn at a size that says what they are: the Sun and Moon large enough to
 * read as discs at a glance, a planet a little larger than the brightest star so the ring
 * around it has somewhere to be.
 */
fun bodyRadius(kind: BodyKind, apparentMagnitude: Double, base: Float): Float = base * when (kind) {
    BodyKind.SUN -> 5.5f
    BodyKind.MOON -> 5f
    // Every planet is drawn larger than the brightest star, however faint it is, because
    // the ring has to be legible as a ring: a circle only a little bigger than a dot is a
    // dot with a smudge round it on a panel with no greys to draw the difference in.
    BodyKind.PLANET -> when {
        apparentMagnitude <= -3 -> 4f
        apparentMagnitude <= 0 -> 3.6f
        else -> 3.2f
    }
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

/**
 * Upstream's azimuthal grid: circles of altitude, and spokes of bearing between them.
 *
 * Thirty and sixty degrees up, and a spoke every forty-five, which is as much of it as is
 * worth the ink — the rim is nought degrees and the middle is ninety, so two circles
 * quarter the sky from overhead to the ground, and the spokes name the halfway bearings
 * the four letters on the rim do not.
 */
val ALTITUDE_CIRCLES: List<Double> = listOf(30.0, 60.0)
val AZIMUTH_SPOKES: List<Double> = (0..315 step 45).map { it.toDouble() }

/** Wrap any angle into 0 up to but not including 360. */
fun normaliseAzimuth(degrees: Double): Double {
    var value = degrees % 360.0
    if (value < 0) value += 360.0
    return value
}
