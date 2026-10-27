package com.wanderwildwood.seizu

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.seizu.sky.ChartViewModel
import com.wanderwildwood.seizu.sky.ZOOM_STEP
import com.wanderwildwood.seizu.ui.ChartScreen
import com.wanderwildwood.seizu.ui.LocationDialog
import com.wanderwildwood.seizu.ui.ObjectDialog
import com.wanderwildwood.seizu.ui.SettingsScreen
import com.wanderwildwood.seizu.ui.TimeDialog
import com.wanderwildwood.seizu.ui.monochrome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The screen stays on while this app is in front.
        //
        // A star chart is held up towards the sky and looked back and forth from, outside,
        // in the dark, usually in gloves — the one situation where the ordinary screen
        // timeout is not a sensible default but a fault. Worse than the waking is what
        // waking costs you: a phone that has just lit its lock screen has taken your
        // night vision with it, and that takes twenty minutes to get back.
        //
        // This is the window flag, not a WAKE_LOCK: it needs no permission, and Android
        // drops it by itself the moment the window loses focus, so it cannot be left on
        // by accident or outlive the app.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val viewModel: ChartViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val pointing = state.pointing != null
            LaunchedEffect(state.turnWithPhone, pointing) { turnWithPhone(state.turnWithPhone, pointing) }
            // A steady cursor: a blinking one is a repaint twice a second on an E Ink panel
            // for as long as a field has focus.
            CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                ThemeMMD(colorScheme = monochrome) {
                    StarChart(viewModel)
                }
            }
        }
    }

    /**
     * Sideways with the phone by the sensor itself when the setting is on, since the Kompakt
     * gives no way to switch its own auto-rotate on; otherwise as the phone's setting says.
     *
     * While Point is on the window stays whichever way it was when Point was pressed. Tipping
     * a phone back towards the zenith is exactly the movement that makes Android's own
     * rotation guess flip the screen, and a flip would redraw the whole panel mid-look.
     */
    private fun turnWithPhone(on: Boolean, pointing: Boolean) {
        requestedOrientation = when {
            pointing -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
            on -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
private fun StarChart(viewModel: ChartViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Saveable, so a dialog that was open is still open after the phone turns sideways and
    // the window is built again.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var whenOpen by rememberSaveable { mutableStateOf(false) }
    var whereOpen by rememberSaveable { mutableStateOf(false) }
    // Set when the GPS was asked and had nothing cached. Cleared whenever the dialog is
    // opened again, so a message from last time is never the first thing read.
    var noFix by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    val sensor = remember { PointSensor(context.applicationContext) }
    val pointing = state.pointing
    // The sensors run only while Point is on, not held, and the app is in front: registered
    // on resume (or when Point comes on), unregistered on pause (or when it goes off).
    if (pointing != null && !pointing.held) {
        LifecycleResumeEffect(sensor) {
            sensor.start(
                displayRotation = context.display?.rotation ?: 0,
                onAim = viewModel::onAim,
                onUnsure = viewModel::setCompassUnsure,
            )
            onPauseOrDispose { sensor.stop() }
        }
    }

    // Back leaves Point for the whole-sky chart where it was left, and Settings for the
    // chart, before it leaves the app.
    BackHandler(enabled = pointing != null && !settingsOpen) { viewModel.stopPoint() }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            whereOpen = false
            return@rememberLauncherForActivityResult
        }
        // Permission is not a fix. Granting it and still having nothing cached is the
        // ordinary case on a phone that has been indoors, so say so and leave the dialog
        // up rather than closing it on a chart that did not move.
        val fix = lastKnownLocation(context)
        if (fix == null) {
            noFix = true
        } else {
            viewModel.setLocation(fix.first, fix.second, true)
            whereOpen = false
        }
    }

    when {
        settingsOpen -> SettingsScreen(
            layers = state.layers,
            turnWithPhone = state.turnWithPhone,
            onClose = { settingsOpen = false },
            onLayers = viewModel::setLayers,
            onTurnWithPhone = viewModel::setTurnWithPhone,
        )

        else -> ChartScreen(
            state = state,
            canPoint = sensor.available,
            onSettings = { settingsOpen = true },
            onFacing = viewModel::nextFacing,
            onWhen = { whenOpen = true },
            onWhere = { noFix = false; whereOpen = true },
            onSelect = viewModel::select,
            onZoomIn = { viewModel.zoomBy(ZOOM_STEP) },
            onZoomOut = { viewModel.zoomBy(1f / ZOOM_STEP) },
            onZoom = viewModel::zoomBy,
            onPan = viewModel::panBy,
            onResetView = viewModel::resetView,
            onPoint = viewModel::togglePoint,
            onHold = viewModel::toggleHold,
        )
    }

    if (whenOpen) {
        TimeDialog(
            current = state.fixedTime,
            onSet = { viewModel.setTime(it); whenOpen = false },
            onDismiss = { whenOpen = false },
        )
    }

    if (whereOpen) {
        LocationDialog(
            latitude = state.latitude,
            longitude = state.longitude,
            canUseGps = true,
            noFix = noFix,
            onSet = { la, lo -> viewModel.setLocation(la, lo); whereOpen = false },
            onUseGps = {
                if (!hasLocation(context)) {
                    ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                } else {
                    val fix = lastKnownLocation(context)
                    if (fix == null) {
                        noFix = true
                    } else {
                        viewModel.setLocation(fix.first, fix.second, true)
                        whereOpen = false
                    }
                }
            },
            onDismiss = { whereOpen = false },
        )
    }

    state.selected?.let {
        ObjectDialog(objectAt = it, onDismiss = { viewModel.select(null) })
    }
}

private fun hasLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

/**
 * The last fix the phone already had, rather than a live one.
 *
 * A star chart needs to know which hillside you are on, not which end of it. Coarse
 * location is plenty, the last known fix is plenty, and neither costs a GPS lock or a
 * stream of updates the app would then have to remember to stop.
 */
@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocation(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    providers.forEach { provider ->
        val fix = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        if (fix != null) return fix.latitude to fix.longitude
    }
    return null
}
