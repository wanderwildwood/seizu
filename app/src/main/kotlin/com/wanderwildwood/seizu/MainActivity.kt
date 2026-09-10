package com.wanderwildwood.seizu

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.seizu.sky.ChartViewModel
import com.wanderwildwood.seizu.ui.ChartScreen
import com.wanderwildwood.seizu.ui.LocationDialog
import com.wanderwildwood.seizu.ui.ObjectDialog
import com.wanderwildwood.seizu.ui.SettingsScreen
import com.wanderwildwood.seizu.ui.TimeDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD {
                StarChart()
            }
        }
    }
}

@Composable
private fun StarChart(viewModel: ChartViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var settingsOpen by remember { mutableStateOf(false) }
    var whenOpen by remember { mutableStateOf(false) }
    var whereOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) lastKnownLocation(context)?.let { viewModel.setLocation(it.first, it.second, true) }
        whereOpen = false
    }

    when {
        settingsOpen -> SettingsScreen(
            layers = state.layers,
            onClose = { settingsOpen = false },
            onLayers = viewModel::setLayers,
        )

        else -> ChartScreen(
            state = state,
            onSettings = { settingsOpen = true },
            onFacing = viewModel::nextFacing,
            onWhen = { whenOpen = true },
            onWhere = { whereOpen = true },
            onSelect = viewModel::select,
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
            onSet = { la, lo -> viewModel.setLocation(la, lo); whereOpen = false },
            onUseGps = {
                if (hasLocation(context)) {
                    lastKnownLocation(context)?.let { viewModel.setLocation(it.first, it.second, true) }
                    whereOpen = false
                } else {
                    ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
