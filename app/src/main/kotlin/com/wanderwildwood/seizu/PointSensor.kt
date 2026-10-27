package com.wanderwildwood.seizu

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.wanderwildwood.seizu.sky.Aim
import com.wanderwildwood.seizu.sky.aimFrom

/**
 * The phone's compass and motion sensors, read only while Point is on and the app is in front.
 *
 * One fused rotation vector does the work: Android (or, on the Kompakt, MediaTek's sensor hub)
 * has already combined the accelerometer, the magnetometer and the gyroscope into an
 * orientation, which is better than anything this app could do with the three of them.
 * Where a phone has no fused one, the geomagnetic rotation vector, which leaves the gyroscope
 * out, is used instead; with neither, there is no Point button.
 *
 * The magnetometer is listened to as well, for its accuracy alone: it is the sensor that
 * knows when it has been confused by a car, a fence or a pocket of keys, and the one thing
 * that can be done about that is the figure of eight it asks for.
 *
 * Nothing needs a permission, and nothing is stored: each reading is turned into a direction
 * and handed on, and [stop] unregisters everything.
 */
class PointSensor(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val magnetometer: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** Whether this phone can point at all. */
    val available: Boolean get() = rotationSensor != null

    private val matrix = FloatArray(9)
    private var displayRotation = 0
    private var onAim: (Aim, Long) -> Unit = { _, _ -> }
    private var onUnsure: (Boolean) -> Unit = {}
    private var running = false

    /**
     * Start reading. [displayRotation] is the window's `Display.getRotation()`, which decides
     * which edge of the phone is the top of the screen.
     */
    fun start(displayRotation: Int, onAim: (Aim, Long) -> Unit, onUnsure: (Boolean) -> Unit) {
        val sensor = rotationSensor ?: return
        val sensors = manager ?: return
        this.displayRotation = displayRotation
        this.onAim = onAim
        this.onUnsure = onUnsure
        if (running) return
        running = true
        // SENSOR_DELAY_UI is about sixteen readings a second: plenty to tell when the phone
        // has come to rest, and nothing is drawn per reading anyway.
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        magnetometer?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        if (!running) return
        running = false
        manager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> onUnsure(isUnsure(event.accuracy))
            else -> {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                // The event's own clock, in nanoseconds since boot: steady, and it is the
                // moment the reading was taken rather than when it reached the app.
                aimFrom(matrix, displayRotation)?.let { onAim(it, event.timestamp / 1_000_000L) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) onUnsure(isUnsure(accuracy))
    }

    /** Low or unreliable; "no contact" is not about the compass and says nothing either way. */
    private fun isUnsure(accuracy: Int): Boolean =
        accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
}
