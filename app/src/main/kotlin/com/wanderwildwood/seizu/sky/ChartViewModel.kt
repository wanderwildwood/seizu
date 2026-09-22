package com.wanderwildwood.seizu.sky

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.GregorianCalendar

data class ChartState(
    val scene: Scene = Scene(),
    val layers: Layers = Layers(),
    val latitude: Double = 51.4778,
    val longitude: Double = -0.0014,
    val facing: Int = 0,
    val zoom: Float = 1f,
    /** How far the view has been dragged over the chart, in radii of the unzoomed disc. */
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** Null means now, and the chart follows the clock when it is redrawn. */
    val fixedTime: GregorianCalendar? = null,
    val loading: Boolean = true,
    val usingGps: Boolean = false,
    val selected: SkyObject? = null,
)

class ChartViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = Preferences(application)

    private val _state = MutableStateFlow(
        ChartState(
            layers = preferences.layers,
            latitude = preferences.latitude,
            longitude = preferences.longitude,
            facing = preferences.facing,
        )
    )
    val state: StateFlow<ChartState> = _state.asStateFlow()

    private var builder: SkyBuilder? = null

    /**
     * Parse the catalogues, then draw the sky.
     *
     * Off the main thread: 1.7 MB of fixed-width text is several seconds of parsing on
     * this phone, and doing it in `onCreate` would be a black screen with no explanation.
     */
    fun load() {
        if (builder != null) return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                SkyData.load(getApplication())
                builder = SkyData.builder()
            }
            rebuild()
        }
    }

    /** Rebuild the whole sky. Everything that changes the chart ends up here. */
    fun rebuild() {
        val current = builder ?: return
        viewModelScope.launch {
            val s = _state.value
            val time = s.fixedTime ?: GregorianCalendar()
            val scene = withContext(Dispatchers.Default) {
                current.build(time, s.latitude, s.longitude, s.layers)
            }
            _state.update { it.copy(scene = scene, loading = false) }
        }
    }

    fun setLocation(latitude: Double, longitude: Double, fromGps: Boolean = false) {
        preferences.latitude = latitude
        preferences.longitude = longitude
        _state.update { it.copy(latitude = latitude, longitude = longitude, usingGps = fromGps) }
        rebuild()
    }

    fun setTime(time: GregorianCalendar?) {
        _state.update { it.copy(fixedTime = time) }
        rebuild()
    }

    fun setLayers(layers: Layers) {
        preferences.layers = layers
        _state.update { it.copy(layers = layers) }
        rebuild()
    }

    /** Turn the chart so a different bearing is at the bottom. No rebuild: same sky. */
    fun nextFacing() {
        val index = FACINGS.indexOf(_state.value.facing)
        val next = FACINGS[(index + 1).mod(FACINGS.size)]
        preferences.facing = next
        _state.update { it.copy(facing = next) }
    }

    /**
     * Zoom is drawing, not astronomy, so it never rebuilds the scene either.
     *
     * Zooming happens about the middle of the screen, and the pan is scaled with it, so
     * whatever you had in the middle stays in the middle. Zooming about the zenith
     * instead — which is what leaving the pan alone would do — walks the thing you were
     * looking at off the edge on every press.
     */
    fun zoomBy(factor: Float) {
        _state.update { current ->
            val zoom = (current.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val scale = zoom / current.zoom
            val (panX, panY) = clampPan(current.panX * scale, current.panY * scale, zoom)
            current.copy(zoom = zoom, panX = panX, panY = panY)
        }
    }

    /** Drag the view over the chart, in radii of the unzoomed disc. */
    fun panBy(dx: Float, dy: Float) {
        _state.update { current ->
            val (panX, panY) = clampPan(current.panX + dx, current.panY + dy, current.zoom)
            current.copy(panX = panX, panY = panY)
        }
    }

    /** The whole sky on the screen again: the way out of being lost in a zoomed chart. */
    fun resetView() = _state.update { it.copy(zoom = MIN_ZOOM, panX = 0f, panY = 0f) }

    fun select(objectAt: SkyObject?) = _state.update { it.copy(selected = objectAt) }
}

/**
 * [from], moved by whole hours and whole days.
 *
 * Calendar rather than arithmetic on milliseconds, so a step of a day over the end of a
 * month or over a daylight-saving change lands on the same hour of the clock the next
 * day. On the nights that matters, an hour is exactly what you are asking about.
 */
fun steppedTime(from: GregorianCalendar, hours: Int, days: Int): GregorianCalendar {
    val next = from.clone() as GregorianCalendar
    next.add(Calendar.DAY_OF_MONTH, days)
    next.add(Calendar.HOUR_OF_DAY, hours)
    return next
}
