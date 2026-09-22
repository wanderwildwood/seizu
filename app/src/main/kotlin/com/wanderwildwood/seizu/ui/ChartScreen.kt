package com.wanderwildwood.seizu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.seizu.sky.ChartState
import com.wanderwildwood.seizu.sky.FACINGS
import com.wanderwildwood.seizu.sky.MIN_ZOOM

/**
 * The chart, and underneath it the two facts that decide what is on it.
 *
 * When and where are on the face of the app rather than behind settings, because a star
 * chart showing the wrong time or the wrong place is not wrong in a way you can see — it
 * is a perfectly plausible sky that does not match the one overhead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    state: ChartState,
    onSettings: () -> Unit,
    onFacing: () -> Unit,
    onWhen: () -> Unit,
    onWhere: () -> Unit,
    onSelect: (com.wanderwildwood.seizu.sky.SkyObject?) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoom: (Float) -> Unit,
    onPan: (Float, Float) -> Unit,
    onResetView: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = "Star Chart") },
                actions = { BarButton(Icons.Settings, "Settings", onSettings) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (state.loading) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TextMMD(text = "Reading the catalogue…", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        TextMMD(
                            text = "Nine thousand stars. This happens once.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    ChartCanvas(
                        scene = state.scene,
                        layers = state.layers,
                        facing = state.facing,
                        zoom = state.zoom,
                        panX = state.panX,
                        panY = state.panY,
                        onSelect = onSelect,
                        onZoom = onZoom,
                        onPan = onPan,
                        onResetView = onResetView,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Field(
                        label = state.scene.whenText.ifEmpty { "Now" },
                        sub = if (state.fixedTime == null) "now" else "set",
                        onClick = onWhen,
                        modifier = Modifier.weight(1f),
                    )
                    Field(
                        label = state.scene.whereText.ifEmpty { "—" },
                        sub = if (state.usingGps) "from GPS" else "set by hand",
                        onClick = onWhere,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Which way the chart is turned, with the zoom either side of it: the
                // three things you change while standing outside holding the phone up,
                // on one row under your thumb. Pinching works too, but a pinch on a panel
                // this size is a two-handed job in the dark.
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButtonMMD(
                        onClick = onZoomOut,
                        modifier = Modifier.width(56.dp).height(44.dp),
                    ) { TextMMD(text = "\u2212", style = MaterialTheme.typography.bodyMedium) }

                    Spacer(Modifier.width(6.dp))

                    OutlinedButtonMMD(
                        onClick = onFacing,
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        val facing = FACINGS.firstOrNull { it.second == state.facing }?.first
                            ?: "North up"
                        TextMMD(
                            // The zoom is shown only when there is some, and it is shown
                            // here rather than on the chart: a number floating over the
                            // sky is one more mark to read past.
                            text = if (state.zoom > MIN_ZOOM * 1.05f) {
                                "%s  ×%.1f".format(facing, state.zoom)
                            } else {
                                facing
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    OutlinedButtonMMD(
                        onClick = onZoomIn,
                        modifier = Modifier.width(56.dp).height(44.dp),
                    ) { TextMMD(text = "+", style = MaterialTheme.typography.bodyMedium) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun Field(label: String, sub: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        TextMMD(text = label, style = MaterialTheme.typography.labelSmall)
        TextMMD(text = sub, style = MaterialTheme.typography.labelSmall)
    }
}
