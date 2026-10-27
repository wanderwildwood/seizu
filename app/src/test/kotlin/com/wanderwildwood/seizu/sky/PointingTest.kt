package com.wanderwildwood.seizu.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pointing, checked with made-up orientations rather than a phone.
 *
 * Each test builds the rotation matrix a phone would report for a pose described in plain
 * words (which way the back points, which way the screen's top leans, how the display is
 * turned) and checks that the aim read back out of it is the pose that went in.
 */
class PointingTest {

    // ---- building poses --------------------------------------------------------------

    private fun dir(azimuth: Double, altitude: Double): DoubleArray {
        val a = Math.toRadians(azimuth)
        val h = Math.toRadians(altitude)
        return doubleArrayOf(cos(h) * sin(a), cos(h) * cos(a), sin(h))
    }

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun scale(a: DoubleArray, k: Double) = DoubleArray(3) { a[it] * k }
    private fun plus(a: DoubleArray, b: DoubleArray) = DoubleArray(3) { a[it] + b[it] }

    /**
     * The matrix for a phone whose back points at ([azimuth], [altitude]), with the screen's
     * top edge tipped [roll] degrees clockwise from straight up (as seen by the holder), in a
     * window drawn at display [rotation].
     *
     * World vectors of the screen's right, the screen's up and the screen's normal are worked
     * out first; the matrix is then whatever takes the phone's axes there, given which phone
     * axis is the screen's right and up at that display rotation.
     */
    private fun pose(azimuth: Double, altitude: Double, roll: Double = 0.0, rotation: Int = 0): FloatArray {
        val back = dir(azimuth, altitude)
        // Upright on the screen with no roll: in the vertical plane through the back, at
        // right angles to it, leaning towards the zenith.
        val plainUp = dir(azimuth + 180.0, 90.0 - altitude)
        val plainRight = cross(plainUp, scale(back, -1.0))
        val r = Math.toRadians(roll)
        val up = plus(scale(plainUp, cos(r)), scale(plainRight, sin(r)))
        val right = plus(scale(plainRight, cos(r)), scale(plainUp, -sin(r)))
        val out = scale(back, -1.0)

        val (rightDev, upDev) = screenAxes(rotation)
        val zDev = doubleArrayOf(0.0, 0.0, 1.0)
        // R * M = W, where M's columns are the screen axes in phone axes and W's are the
        // same axes in the world. M is a signed permutation, so R = W * M^T.
        val w = arrayOf(right, up, out)
        val m = arrayOf(rightDev, upDev, zDev)
        return FloatArray(9) { index ->
            val row = index / 3
            val col = index % 3
            (0 until 3).sumOf { k -> w[k][row] * m[k][col] }.toFloat()
        }
    }

    /** Android's own `getRotationMatrixFromVector`, for checking the frame against it. */
    private fun fromRotationVector(x: Double, y: Double, z: Double): FloatArray {
        val w = sqrt((1 - x * x - y * y - z * z).coerceAtLeast(0.0))
        val sqX = 2 * x * x
        val sqY = 2 * y * y
        val sqZ = 2 * z * z
        val xy = 2 * x * y
        val zw = 2 * z * w
        val xz = 2 * x * z
        val yw = 2 * y * w
        val yz = 2 * y * z
        val xw = 2 * x * w
        return floatArrayOf(
            (1 - sqY - sqZ).toFloat(), (xy - zw).toFloat(), (xz + yw).toFloat(),
            (xy + zw).toFloat(), (1 - sqX - sqZ).toFloat(), (yz - xw).toFloat(),
            (xz - yw).toFloat(), (yz + xw).toFloat(), (1 - sqX - sqY).toFloat(),
        )
    }

    private fun bearingGap(a: Double, b: Double): Double {
        val d = abs(normaliseAzimuth(a) - normaliseAzimuth(b))
        return minOf(d, 360.0 - d)
    }

    private fun assertAim(expectedAzimuth: Double, expectedAltitude: Double, aim: Aim?, tolerance: Double = 0.05) {
        assertNotNull(aim)
        aim!!
        assertEquals("altitude", expectedAltitude, aim.altitude, tolerance)
        assertTrue(
            "azimuth ${aim.azimuth}, wanted $expectedAzimuth",
            bearingGap(expectedAzimuth, aim.azimuth) <= tolerance,
        )
    }

    // ---- the frame itself ------------------------------------------------------------

    /** The rotation vector's zero is a phone lying face up, top to the north. */
    @Test
    fun `a phone lying face up points its back at the ground`() {
        val aim = aimFrom(fromRotationVector(0.0, 0.0, 0.0), 0)
        assertNotNull(aim)
        assertEquals(-90.0, aim!!.altitude, 1e-4)
    }

    /** Stood upright from face-up by a quarter turn about its x axis: screen south, back north. */
    @Test
    fun `a phone held upright facing north points north at the horizon`() {
        val s = sin(Math.toRadians(45.0))
        assertAim(0.0, 0.0, aimFrom(fromRotationVector(s, 0.0, 0.0), 0), 1e-3)
    }

    /** The same, then turned a quarter clockwise about the vertical: now facing east. */
    @Test
    fun `turning on the spot turns the bearing`() {
        // Upright (90 degrees about x), then 90 degrees about world z, clockwise from above,
        // which is minus ninety about +z. Quaternion product q = qz * qx.
        val c = cos(Math.toRadians(45.0))
        val s = sin(Math.toRadians(45.0))
        // qz = (0, 0, -s, c) and qx = (s, 0, 0, c) multiply out to (cs, -s², -cs, c²).
        val x = c * s
        val y = -s * s
        val z = -s * c
        assertAim(90.0, 0.0, aimFrom(fromRotationVector(x, y, z), 0), 1e-3)
    }

    @Test
    fun `the pose builder agrees with the frame`() {
        assertAim(0.0, 0.0, aimFrom(pose(0.0, 0.0), 0), 1e-3)
        val s = sin(Math.toRadians(45.0))
        val built = pose(0.0, 0.0)
        val android = fromRotationVector(s, 0.0, 0.0)
        built.indices.forEach { assertEquals("element $it", android[it], built[it], 1e-5f) }
    }

    // ---- reading the aim -------------------------------------------------------------

    @Test
    fun `every bearing and every ordinary altitude reads back exactly`() {
        for (azimuth in 0 until 360 step 15) {
            for (altitude in listOf(-30, -5, 0, 10, 25, 40, 55, 60)) {
                assertAim(azimuth.toDouble(), altitude.toDouble(), aimFrom(pose(azimuth.toDouble(), altitude.toDouble()), 0))
            }
        }
    }

    @Test
    fun `every display rotation reads the same aim`() {
        for (rotation in 0..3) {
            for (azimuth in listOf(0.0, 90.0, 200.0, 315.0)) {
                for (altitude in listOf(0.0, 30.0, 70.0, 88.0, 90.0)) {
                    assertAim(azimuth, altitude, aimFrom(pose(azimuth, altitude, rotation = rotation), rotation))
                }
            }
        }
    }

    /**
     * Turning the phone on its side without the window turning (the window stays portrait)
     * moves the screen's top edge but not the back, and below the steep zone only the back
     * counts.
     */
    @Test
    fun `a sideways phone in an upright window still reads true below sixty degrees`() {
        for (roll in listOf(-90.0, -30.0, 45.0, 90.0, 180.0)) {
            assertAim(123.0, 35.0, aimFrom(pose(123.0, 35.0, roll = roll), 0))
            assertAim(300.0, 60.0, aimFrom(pose(300.0, 60.0, roll = roll), 0))
        }
    }

    /** Straight overhead the back has no bearing; the screen's top edge gives one. */
    @Test
    fun `straight up the bearing is the way the screen's top leans away from`() {
        for (azimuth in listOf(0.0, 45.0, 180.0, 271.0)) {
            for (rotation in 0..3) {
                assertAim(azimuth, 90.0, aimFrom(pose(azimuth, 90.0, rotation = rotation), rotation), 0.01)
            }
        }
    }

    /** Through the steep zone the bearing moves smoothly, never by a jump. */
    @Test
    fun `the bearing does not jump on the way up to the zenith`() {
        var previous = aimFrom(pose(150.0, 50.0, roll = 10.0), 0)!!
        var altitude = 50.0
        while (altitude <= 90.0) {
            val aim = aimFrom(pose(150.0, altitude, roll = 10.0), 0)!!
            assertTrue("jump at $altitude", bearingGap(aim.azimuth, previous.azimuth) < 3.0)
            previous = aim
            altitude += 0.5
        }
    }

    @Test
    fun `an empty matrix is no aim`() {
        assertNull(aimFrom(FloatArray(9), 0))
        assertNull(aimFrom(FloatArray(3), 0))
    }

    @Test
    fun `angles between directions`() {
        assertEquals(0.0, angleBetween(Aim(10.0, 20.0), Aim(10.0, 20.0)), 1e-6)
        assertEquals(90.0, angleBetween(Aim(0.0, 0.0), Aim(90.0, 0.0)), 1e-6)
        assertEquals(45.0, angleBetween(Aim(0.0, 0.0), Aim(0.0, 45.0)), 1e-6)
        // Near the zenith a big change of bearing is a small change of direction.
        assertTrue(angleBetween(Aim(0.0, 89.0), Aim(180.0, 89.0)) < 2.1)
        assertEquals(2.0, angleBetween(Aim(359.0, 0.0), Aim(1.0, 0.0)), 1e-6)
    }

    // ---- redraw when it settles ------------------------------------------------------

    @Test
    fun `the first reading is drawn once it has been still for the hold time`() {
        val settler = Settler()
        val aim = Aim(100.0, 30.0)
        assertNull(settler.offer(aim, 0))
        assertNull(settler.offer(aim, 300))
        assertEquals(aim, settler.offer(aim, 600))
        // And not again while it stays there.
        assertNull(settler.offer(aim, 1300))
        assertNull(settler.offer(aim, 5000))
    }

    @Test
    fun `the shake of a held phone never redraws`() {
        val settler = Settler()
        settler.offer(Aim(100.0, 30.0), 0)
        assertNotNull(settler.offer(Aim(100.0, 30.0), 700))
        var t = 700L
        repeat(200) { i ->
            t += 50
            val wobble = if (i % 2 == 0) 1.5 else -1.5
            assertNull(settler.offer(Aim(100.0 + wobble, 30.0 - wobble), t))
        }
    }

    /** Hysteresis: a move of three degrees that comes to rest is not worth a refresh. */
    @Test
    fun `a small move that settles is left alone`() {
        val settler = Settler()
        settler.offer(Aim(100.0, 30.0), 0)
        settler.offer(Aim(100.0, 30.0), 700)
        assertNull(settler.offer(Aim(103.0, 30.0), 800))
        assertNull(settler.offer(Aim(103.0, 30.0), 1500))
        assertNull(settler.offer(Aim(103.0, 30.0), 3000))
    }

    @Test
    fun `a real move is drawn only after it settles`() {
        val settler = Settler()
        settler.offer(Aim(100.0, 30.0), 0)
        settler.offer(Aim(100.0, 30.0), 700)
        // Swinging round: every reading far from the last, so nothing is ever still.
        var t = 700L
        for (azimuth in 103..157 step 3) {
            t += 50
            assertNull(settler.offer(Aim(azimuth.toDouble(), 30.0), t))
        }
        // Comes to rest at 160, a step on from the last reading.
        val rest = Aim(160.0, 30.0)
        t += 50
        val startOfRest = t
        assertNull(settler.offer(rest, t))
        while (t - startOfRest < 550) {
            t += 50
            assertNull("too early at ${t - startOfRest} ms", settler.offer(Aim(160.5, 30.2), t))
        }
        t += 100
        val drawn = settler.offer(Aim(160.2, 29.9), t)
        assertNotNull(drawn)
        assertEquals(160.2, drawn!!.azimuth, 1e-9)
        assertEquals(drawn, settler.shown)
    }

    @Test
    fun `a slow drift is drawn once it stops`() {
        val settler = Settler()
        settler.offer(Aim(0.0, 40.0), 0)
        settler.offer(Aim(0.0, 40.0), 700)
        var t = 700L
        var azimuth = 0.0
        // Half a degree every 100 ms: always moving more than "still" over the hold time.
        repeat(40) {
            t += 100
            azimuth += 0.5
            assertNull(settler.offer(Aim(azimuth, 40.0), t))
        }
        t += 100
        settler.offer(Aim(azimuth, 40.0), t)
        t += 700
        assertNotNull(settler.offer(Aim(azimuth, 40.0), t))
    }

    @Test
    fun `reset draws again wherever the phone is, resume does not`() {
        val settler = Settler()
        val aim = Aim(10.0, 10.0)
        settler.offer(aim, 0)
        assertNotNull(settler.offer(aim, 700))

        settler.resume()
        settler.offer(aim, 800)
        assertNull(settler.offer(aim, 1600))

        settler.reset()
        assertNull(settler.shown)
        settler.offer(aim, 1700)
        assertNotNull(settler.offer(aim, 2400))
    }

    @Test
    fun `a clock that goes backwards starts the stillness again`() {
        val settler = Settler()
        val aim = Aim(10.0, 10.0)
        settler.offer(aim, 5000)
        assertNull(settler.offer(aim, 100))
        assertNotNull(settler.offer(aim, 800))
    }

    // ---- the patch of chart ----------------------------------------------------------

    private val canvas = Projection(radiusPx = 100f, centreX = 200f, centreY = 300f)

    private fun projectionFor(view: PointView) = canvas.copy(
        zoom = view.zoom,
        panX = view.panX * canvas.radiusPx,
        panY = view.panY * canvas.radiusPx,
        facing = view.facing,
    )

    @Test
    fun `the aim lands in the middle of the screen`() {
        for (azimuth in listOf(0.0, 77.0, 180.0, 359.0)) {
            for (altitude in listOf(0.0, 20.0, 45.0, 80.0, 90.0)) {
                val view = pointView(Aim(azimuth, altitude), POINT_ZOOM)
                val (x, y) = projectionFor(view).project(azimuth, altitude)
                assertEquals("x for $azimuth/$altitude", 200f, x, 0.6f)
                assertEquals("y for $azimuth/$altitude", 300f, y, 0.6f)
            }
        }
    }

    @Test
    fun `the zenith is straight above the aim`() {
        val view = pointView(Aim(250.0, 40.0), POINT_ZOOM)
        val projection = projectionFor(view)
        assertEquals(200f, projection.zenithX, 1e-3f)
        assertTrue(projection.zenithY < 300f)
    }

    /** Facing north and looking up at the sky, east is on your right. */
    @Test
    fun `east is on the right when pointing north`() {
        val projection = projectionFor(pointView(Aim(0.0, 40.0), POINT_ZOOM))
        val (x, _) = projection.project(10.0, 40.0)
        assertTrue(x > 200f)
        val (_, higher) = projection.project(0.0, 50.0)
        assertTrue("higher is further up the screen", higher < 300f)
    }

    @Test
    fun `below the horizon is shown as the horizon`() {
        assertEquals(pointView(Aim(30.0, 0.0), 2f), pointView(Aim(30.0, -25.0), 2f))
    }

    // ---- what the cross names --------------------------------------------------------

    private fun star(name: String?, azimuth: Double, altitude: Double) =
        SkyStar(azimuth, altitude, magnitude = 2.0, label = name, hr = 1, bayerFlamsteed = "")

    @Test
    fun `the cross names the nearest named thing, not the nearest dot`() {
        val faint = star(null, 100.2, 30.0)
        val vega = star("Vega", 104.0, 31.0)
        val far = star("Deneb", 112.0, 30.0)
        val scene = Scene(stars = listOf(faint, far, vega))
        assertSame(vega, nearestNamed(scene, Aim(100.0, 30.0)))
    }

    @Test
    fun `a planet counts as named`() {
        val jupiter = SkyBody(50.0, 20.0, -2.0, "Jupiter", BodyKind.PLANET)
        val scene = Scene(stars = listOf(star("Aldebaran", 58.0, 20.0)), bodies = listOf(jupiter))
        assertSame(jupiter, nearestNamed(scene, Aim(51.0, 21.0)))
    }

    @Test
    fun `nothing named nearby is nothing`() {
        val scene = Scene(stars = listOf(star("Vega", 200.0, 30.0)))
        assertNull(nearestNamed(scene, Aim(100.0, 30.0)))
    }
}
