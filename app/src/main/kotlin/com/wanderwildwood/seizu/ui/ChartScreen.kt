package com.wanderwildwood.seizu.ui

import androidx.annotation.StringRes
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.seizu.R
import com.wanderwildwood.seizu.sky.ChartState
import com.wanderwildwood.seizu.sky.MIN_ZOOM
import com.wanderwildwood.seizu.sky.Place
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
                title = { TextMMD(text = stringResource(R.string.chart_title)) },
                actions = { BarButton(Icons.Settings, stringResource(R.string.chart_cd_settings), onSettings) },
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
                        TextMMD(text = stringResource(R.string.chart_loading), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        TextMMD(
                            text = stringResource(R.string.chart_loading_note),
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
                val nowLabel = stringResource(R.string.chart_when_now_label)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Field(
                        label = state.scene.moment?.let { whenText(it) } ?: nowLabel,
                        sub = if (state.fixedTime == null) stringResource(R.string.chart_when_sub_now) else stringResource(R.string.chart_when_sub_set),
                        onClick = onWhen,
                        modifier = Modifier.weight(1f),
                    )
                    Field(
                        label = state.scene.place?.let { whereText(it) } ?: "—",
                        sub = if (state.usingGps) stringResource(R.string.chart_where_sub_gps) else stringResource(R.string.chart_where_sub_by_hand),
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
                        val facing = stringResource(facingName(state.facing))
                        TextMMD(
                            // The zoom is shown only when there is some, and it is shown
                            // here rather than on the chart: a number floating over the
                            // sky is one more mark to read past.
                            text = if (state.zoom > MIN_ZOOM * 1.05f) {
                                stringResource(R.string.chart_facing_zoomed, facing, state.zoom)
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

/** Which way up the chart is, from the bearing at the bottom of it. */
@StringRes
private fun facingName(bearing: Int): Int = when (bearing) {
    90 -> R.string.chart_facing_east
    180 -> R.string.chart_facing_south
    270 -> R.string.chart_facing_west
    else -> R.string.chart_facing_north
}

/** The moment a chart is drawn for, as a date and a time on this phone's clock. */
@Composable
private fun whenText(moment: Date): String {
    val pattern = stringResource(R.string.chart_when_pattern)
    val format = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    return format.format(moment)
}

/** The place a chart is drawn from, to two decimals, with the hemispheres as letters. */
@Composable
private fun whereText(place: Place): String = stringResource(
    R.string.chart_where_value,
    abs(place.latitude),
    stringResource(if (place.north) R.string.compass_north else R.string.compass_south),
    abs(place.longitude),
    stringResource(if (place.east) R.string.compass_east else R.string.compass_west),
)

/**
 * One of the two facts, on one line, with what it is standing on after it.
 *
 * Two lines apiece was a third of the space under the chart spent on text that changes
 * twice an evening. The qualifier still has to be there -- "now" and "set" look identical
 * on the chart itself -- but it can share the line it qualifies.
 */
@Composable
private fun Field(label: String, sub: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        TextMMD(text = stringResource(R.string.chart_field, label, sub), style = MaterialTheme.typography.labelSmall)
    }
}
