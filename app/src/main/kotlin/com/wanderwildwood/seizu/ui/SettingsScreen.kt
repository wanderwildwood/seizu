package com.wanderwildwood.seizu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.seizu.sky.Layers

/**
 * What is drawn on the chart.
 *
 * Every row is a layer that goes on or comes off, except the magnitude, which is the one
 * that changes what the sky looks like most: at 3 you get the figures of the
 * constellations and nothing else, at 6 you get roughly what a dark night gives the naked
 * eye and the chart fills up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    layers: Layers,
    onClose: () -> Unit,
    onLayers: (Layers) -> Unit,
) {
    var aboutOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = "Settings", fontSize = 24.sp) },
                navigationIcon = { BarButton(Icons.Close, "Close", onClose) },
                actions = { BarButton(Icons.Info, "About", { aboutOpen = true }) },
            )
        },
    ) { contentPadding ->
        // MMD's list, not a scrolling Column: it steps four rows to a swipe and stops, and it
        // brings the chevron rail at both ends. A settings screen that coasts was the one
        // screen in the app that did not behave like the phone it is on.
        LazyColumnMMD(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
            }
            item {
                Row(
                    title = "Faintest star",
                    value = "magnitude %.0f".format(layers.magnitudeLimit),
                    note = when {
                        layers.magnitudeLimit <= 3 -> "The bright ones only."
                        layers.magnitudeLimit <= 5 -> "About what a town sky gives you."
                        else -> "About what a dark night gives the naked eye."
                    },
                ) {
                    // 3, 4, 5, 6 and round again. Below 3 the sky is nearly empty and above 6
                    // the panel cannot separate the dots.
                    val next = if (layers.magnitudeLimit >= 6.0) 3.0 else layers.magnitudeLimit + 1.0
                    onLayers(layers.copy(magnitudeLimit = next))
                }
            }
            item {
                HorizontalDividerMMD()
            }
            item {
                Toggle("Constellation figures", layers.constellationLines) {
                    onLayers(layers.copy(constellationLines = it))
                }
            }
            item {
                Toggle("Star names", layers.starNames) {
                    onLayers(layers.copy(starNames = it))
                }
            }
            item {
                Toggle("Sun, Moon and planets", layers.solarSystem) {
                    onLayers(layers.copy(solarSystem = it))
                }
            }
            item {
                Toggle("Name them", layers.solarNames) {
                    onLayers(layers.copy(solarNames = it))
                }
            }
            item {
                HorizontalDividerMMD()
            }
            item {
                Toggle("Ecliptic", layers.ecliptic) { onLayers(layers.copy(ecliptic = it)) }
            }
            item {
                Toggle("Celestial equator", layers.equator) { onLayers(layers.copy(equator = it)) }
            }
            item {
                Toggle("Declination grid", layers.grid) { onLayers(layers.copy(grid = it)) }
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
}

@Composable
private fun Toggle(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(title = title, value = if (on) "On" else "Off") { onChange(!on) }
}

@Composable
private fun Row(title: String, value: String, note: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        TextMMD(text = title, fontSize = 18.sp)
        TextMMD(text = value, fontSize = 14.sp)
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            TextMMD(text = note, fontSize = 13.sp)
        }
    }
}
