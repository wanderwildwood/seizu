package com.wanderwildwood.seizu.sky

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pointing the phone at the sky: from the phone's orientation to a patch of chart.
 *
 * All of it is plain arithmetic on numbers handed in, with no Android in it, so it can be
 * checked with made-up orientations. The sensor listener does nothing but turn a rotation
 * vector into the matrix this takes.
 *
 * **Which way is "pointing".** Out of the back of the phone: the camera side towards the
 * sky, the screen towards you, the way a phone is held up to photograph something. That is
 * the direction you are looking along when you look over the phone at the sky, and it does
 * not change when the phone is turned sideways. Pointing the top edge at a star was the
 * other choice; it means holding the phone flat and squinting along it, with the screen
 * facing the ground when the star is high.
 *
 * **Frames.** The matrix is Android's rotation matrix (`SensorManager.getRotationMatrix`
 * and friends): nine numbers, row-major, taking a vector in the phone's own axes (x to the
 * right of the screen in its natural portrait, y to its top edge, z out of the screen at
 * you) to the world's (x east, y north, z up). The back of the phone is device -z.
 */

/** A direction in the sky: degrees clockwise from north, degrees above the horizon. */
data class Aim(val azimuth: Double, val altitude: Double)

/**
 * The screen's right and up, in the phone's own axes, for a display rotation.
 *
 * [rotation] is `Display.getRotation()`: 0 to 3 for 0, 90, 180 and 270 degrees. Android
 * turns the drawing the opposite way to the phone, so ROTATION_90 is the phone turned a
 * quarter anticlockwise (its left edge down): the screen's up is then the phone's +x and
 * its right the phone's -y. This is the same mapping as the usual
 * `SensorManager.remapCoordinateSystem` table (90: AXIS_Y, AXIS_MINUS_X; 180: AXIS_MINUS_X,
 * AXIS_MINUS_Y; 270: AXIS_MINUS_Y, AXIS_X), written out so it can be tested without a phone.
 * The third axis, out of the screen, never changes, and so neither does the back.
 */
fun screenAxes(rotation: Int): Pair<DoubleArray, DoubleArray> = when (rotation.mod(4)) {
    1 -> doubleArrayOf(0.0, -1.0, 0.0) to doubleArrayOf(1.0, 0.0, 0.0)
    2 -> doubleArrayOf(-1.0, 0.0, 0.0) to doubleArrayOf(0.0, -1.0, 0.0)
    3 -> doubleArrayOf(0.0, 1.0, 0.0) to doubleArrayOf(-1.0, 0.0, 0.0)
    else -> doubleArrayOf(1.0, 0.0, 0.0) to doubleArrayOf(0.0, 1.0, 0.0)
}

/** [matrix] (row-major 3x3) times a phone-axis vector: the same vector in world axes. */
private fun toWorld(matrix: FloatArray, v: DoubleArray): DoubleArray = DoubleArray(3) { row ->
    matrix[row * 3] * v[0] + matrix[row * 3 + 1] * v[1] + matrix[row * 3 + 2] * v[2]
}

/**
 * Where the back of the phone points, from a rotation matrix and the display rotation.
 *
 * The altitude is the back's own. The azimuth is the back's too while it is within
 * [STEEP_FROM] degrees of the horizon; higher than that the back's bearing is a small
 * vector that swings wildly as it nears the zenith, so the bearing slides over to the one
 * the screen's top edge leans away from. Held up towards the sky, the top of a phone tips
 * back towards you, so "away from the top edge" is the way you are facing; straight
 * overhead it is the only bearing there is. Below [STEEP_FROM] the screen plays no part at
 * all, so a phone held sideways in a window that stayed upright still reads true there.
 *
 * Null for a matrix that is not a rotation (all zeros before the first reading).
 */
fun aimFrom(matrix: FloatArray, rotation: Int): Aim? {
    if (matrix.size < 9) return null
    val back = toWorld(matrix, doubleArrayOf(0.0, 0.0, -1.0))
    val length = hypot(hypot(back[0], back[1]), back[2])
    if (length < 0.5) return null
    val altitude = Math.toDegrees(asin((back[2] / length).coerceIn(-1.0, 1.0)))

    val up = toWorld(matrix, screenAxes(rotation).second)
    // How much of the bearing comes from the screen: none up to STEEP_FROM, all of it by
    // STEEP_TO, in a straight line between so the chart does not jump as you pass through.
    val lean = ((altitude - STEEP_FROM) / (STEEP_TO - STEEP_FROM)).coerceIn(0.0, 1.0)
    var east = back[0] * (1 - lean) - up[0] * lean
    var north = back[1] * (1 - lean) - up[1] * lean
    if (hypot(east, north) < 1e-6) {
        // Exactly on the zenith with the screen's top exactly vertical: no bearing at all.
        east = -up[0]
        north = -up[1]
    }
    val azimuth = normaliseAzimuth(Math.toDegrees(atan2(east, north)))
    return Aim(azimuth, altitude)
}

/** Above this altitude the screen's top edge starts to decide the bearing. */
const val STEEP_FROM = 60.0

/** Above this it decides it alone. */
const val STEEP_TO = 85.0

/** The angle between two directions in the sky, in degrees. */
fun angleBetween(a: Aim, b: Aim): Double {
    val alt1 = Math.toRadians(a.altitude)
    val alt2 = Math.toRadians(b.altitude)
    val dAz = Math.toRadians(a.azimuth - b.azimuth)
    val cosine = sin(alt1) * sin(alt2) + cos(alt1) * cos(alt2) * cos(dAz)
    return Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
}

/**
 * Redraw when it settles.
 *
 * An E Ink panel cannot follow a phone being swung about: every frame is a refresh, and a
 * chart redrawn twenty times a second is a grey smear that never finishes drawing. So the
 * picture stays as it is while the phone moves, and is redrawn once, when the phone has
 * come to rest somewhere at least [moveDegrees] from where the picture was drawn.
 *
 * "At rest" is every reading for [holdMillis] staying within [stillDegrees] of the first of
 * them. A hand never holds a phone perfectly still, and the sensor never reads perfectly
 * still either, which is why both thresholds are angles rather than zero; [moveDegrees] is
 * larger than [stillDegrees] so the shake of a held phone can never redraw on its own.
 *
 * The very first reading after [reset] is drawn as soon as it has settled, wherever it is.
 */
class Settler(
    private val moveDegrees: Double = 5.0,
    private val stillDegrees: Double = 2.0,
    private val holdMillis: Long = 600,
) {
    /** What the picture on the screen was drawn for; null before the first settle. */
    var shown: Aim? = null
        private set

    private var anchor: Aim? = null
    private var anchorAt: Long = 0

    /**
     * One reading, at [atMillis] on any steady clock. Returns the direction to redraw for,
     * or null to leave the picture alone.
     */
    fun offer(aim: Aim, atMillis: Long): Aim? {
        val start = anchor
        if (start == null || angleBetween(aim, start) > stillDegrees || atMillis < anchorAt) {
            anchor = aim
            anchorAt = atMillis
            return null
        }
        if (atMillis - anchorAt < holdMillis) return null
        val drawn = shown
        if (drawn != null && angleBetween(aim, drawn) <= moveDegrees) return null
        shown = aim
        // The next redraw needs a fresh stretch of stillness, not the tail of this one.
        anchor = null
        return aim
    }

    /** Forget where the phone was resting, keep what is drawn: after a hold is let go. */
    fun resume() {
        anchor = null
    }

    /** Start again from nothing: Point has just been switched on. */
    fun reset() {
        shown = null
        anchor = null
    }
}

/** How the chart is turned, zoomed and moved to show one patch of sky. */
data class PointView(val facing: Int, val zoom: Float, val panX: Float, val panY: Float)

/**
 * The chart turned and moved so that [aim] is in the middle of the screen, the zenith
 * straight above it, at [zoom].
 *
 * The bearing you face goes to the bottom of the chart, as the facing button does, which
 * puts the zenith above the middle and east on your right when you face north: the sky the
 * way it looks over the top of the phone. The pan is in the view model's units, radii of the
 * unzoomed disc, and moves the aim from below the zenith up to the middle of the canvas.
 *
 * Below the horizon is shown as the horizon: the chart has nothing under the rim to show.
 */
fun pointView(aim: Aim, zoom: Float): PointView {
    val altitude = aim.altitude.coerceIn(0.0, 90.0)
    val facing = normaliseAzimuth(aim.azimuth).roundToInt() % 360
    val distance = ((90.0 - altitude) / 90.0 * zoom).toFloat()
    return PointView(facing = facing, zoom = zoom, panX = 0f, panY = distance)
}

/** How far in Point zooms to start with: about sixty degrees of sky across the screen. */
const val POINT_ZOOM = 3f

/** Closer than this to the cross, in degrees, for something to be named under it. */
const val NAMED_WITHIN = 15.0

/**
 * The thing to name under the cross: the nearest Sun, Moon, planet or named star to [aim],
 * within [within] degrees, or null.
 *
 * Named things only. The cross is never exactly on anything, and the nearest dot to it is
 * nearly always a sixth-magnitude star with a catalogue number; "HR 7001" says nothing to
 * someone asking what that bright one is, which is the question being asked.
 */
fun nearestNamed(scene: Scene, aim: Aim, within: Double = NAMED_WITHIN): SkyObject? {
    var best: SkyObject? = null
    var bestAngle = within
    fun consider(candidate: SkyObject) {
        val angle = angleBetween(aim, Aim(candidate.azimuth, candidate.elevation))
        if (angle < bestAngle) {
            bestAngle = angle
            best = candidate
        }
    }
    scene.bodies.forEach(::consider)
    scene.stars.forEach { if (it.label != null) consider(it) }
    return best
}
