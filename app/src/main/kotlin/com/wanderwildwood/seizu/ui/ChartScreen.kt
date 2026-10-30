package com.wanderwildwood.seizu.ui

import androidx.annotation.StringRes
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.seizu.R
import com.wanderwildwood.seizu.sky.ChartState
import com.wanderwildwood.seizu.sky.MIN_ZOOM
import com.wanderwildwood.seizu.sky.Place
import com.wanderwildwood.seizu.sky.Pointing
import com.wanderwildwood.seizu.sky.SkyBody
import com.wanderwildwood.seizu.sky.SkyObject
import com.wanderwildwood.seizu.sky.SkyStar
import com.wanderwildwood.seizu.sky.nearestNamed
import com.wanderwildwood.seizu.sky.pointView
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
    /** Whether this phone has the sensors Point needs; without them there is no button. */
    canPoint: Boolean,
    onSettings: () -> Unit,
    onFacing: () -> Unit,
    onWhen: () -> Unit,
    onWhere: () -> Unit,
    onSelect: (SkyObject?) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoom: (Float) -> Unit,
    onPan: (Float, Float) -> Unit,
    onResetView: () -> Unit,
    onPoint: () -> Unit,
    onHold: () -> Unit,
) {
    val pointing = state.pointing
    val aim = pointing?.aim
    // What the cross is on, worked out again only when the picture changes.
    val target = remember(state.scene, aim) { aim?.let { nearestNamed(state.scene, it) } }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.chart_title)) },
                actions = { BarButton(Icons.Settings, stringResource(R.string.chart_cd_settings), onSettings) },
            )
        },
    ) { contentPadding ->
        val chart = @Composable { modifier: Modifier ->
            Box(modifier = modifier) {
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
                    // In Point the view comes from where the phone settled; until it first
                    // settles, and the rest of the time, it is the chart as it was left.
                    val view = if (pointing != null && aim != null) pointView(aim, pointing.zoom) else null
                    ChartCanvas(
                        scene = state.scene,
                        layers = state.layers,
                        facing = view?.facing ?: state.facing,
                        zoom = view?.zoom ?: state.zoom,
                        panX = view?.panX ?: state.panX,
                        panY = view?.panY ?: state.panY,
                        onSelect = onSelect,
                        onZoom = onZoom,
                        onPan = onPan,
                        onResetView = onResetView,
                        onCrossTap = if (view != null) ({ target?.let(onSelect) }) else null,
                    )
                }
            }
        }

        val controls = @Composable { modifier: Modifier ->
            Column(modifier = modifier) {
                if (pointing != null) {
                    PointLines(pointing = pointing, target = target, onOpen = { target?.let(onSelect) })
                } else if (landscape) {
                    // Sideways there is no width under the chart, but height beside it: the
                    // two facts go one above the other.
                    WhenField(state, onWhen, Modifier.fillMaxWidth())
                    WhereField(state, onWhere, Modifier.fillMaxWidth())
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        WhenField(state, onWhen, Modifier.weight(1f))
                        WhereField(state, onWhere, Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Which way the chart is turned, with the zoom either side of it: the
                // things you change while standing outside holding the phone up, on one
                // row under your thumb. Pinching works too, but a pinch on a panel this
                // size is a two-handed job in the dark.
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButtonMMD(
                        onClick = onZoomOut,
                        modifier = Modifier.width(48.dp).height(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { TextMMD(text = "\u2212", style = MaterialTheme.typography.bodyMedium) }

                    Spacer(Modifier.width(6.dp))

                    // In Point the facing comes from the phone, so the same place holds
                    // Hold, which freezes the view where it is.
                    OutlinedButtonMMD(
                        onClick = if (pointing != null) onHold else onFacing,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        val label = when {
                            pointing != null && pointing.held -> stringResource(R.string.point_follow)
                            pointing != null -> stringResource(R.string.point_hold)
                            else -> {
                                val facing = stringResource(facingName(state.facing))
                                // The zoom is shown only when there is some, and it is shown
                                // here rather than on the chart: a number floating over the
                                // sky is one more mark to read past.
                                if (state.zoom > MIN_ZOOM * 1.05f) {
                                    stringResource(R.string.chart_facing_zoomed, facing, state.zoom)
                                } else {
                                    facing
                                }
                            }
                        }
                        // Two lines where one will not fit: sideways, with the zoom shown, the button is
                        // narrower than its words.
                        TextMMD(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
                    }

                    if (canPoint) {
                        Spacer(Modifier.width(6.dp))
                        OutlinedButtonMMD(
                            onClick = onPoint,
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            TextMMD(
                                // The button says what pressing it does: point, or go back.
                                text = stringResource(if (pointing != null) R.string.point_whole_sky else R.string.point_point),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    OutlinedButtonMMD(
                        onClick = onZoomIn,
                        modifier = Modifier.width(48.dp).height(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { TextMMD(text = "+", style = MaterialTheme.typography.bodyMedium) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        if (landscape) {
            // Sideways the chart takes the height and the controls sit to its right, where a
            // thumb holding the phone by that end reaches them.
            Row(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                chart(Modifier.fillMaxHeight().weight(1f))
                controls(
                    Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .padding(horizontal = 12.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                chart(Modifier.fillMaxWidth().weight(1f))
                controls(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }
    }
}

/**
 * Under the chart while Point is on: what the cross is on, and what the compass says.
 *
 * The name is a press as well, the same as a tap on the cross, because the cross is small
 * and the line is the width of the screen.
 */
@Composable
private fun PointLines(pointing: Pointing, target: SkyObject?, onOpen: () -> Unit) {
    val text = when {
        pointing.aim == null -> stringResource(R.string.point_waiting)
        target == null -> stringResource(R.string.point_nothing_named)
        else -> targetName(target)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = target != null, onClick = onOpen),
        contentAlignment = Alignment.CenterStart,
    ) {
        TextMMD(text = text, style = MaterialTheme.typography.bodyMedium)
    }
    if (pointing.compassUnsure) {
        TextMMD(text = stringResource(R.string.point_compass_unsure), style = MaterialTheme.typography.labelSmall)
    }
}

/** "Jupiter", or "Vega, in Lyra". */
@Composable
private fun targetName(target: SkyObject): String = when (target) {
    is SkyBody -> bodyName(target)
    is SkyStar -> {
        val name = target.label.orEmpty()
        target.constellation?.let { stringResource(R.string.point_star_in, name, it) } ?: name
    }
    else -> target.label.orEmpty()
}

@Composable
private fun WhenField(state: ChartState, onWhen: () -> Unit, modifier: Modifier) {
    Field(
        label = state.scene.moment?.let { whenText(it) } ?: stringResource(R.string.chart_when_now_label),
        sub = if (state.fixedTime == null) stringResource(R.string.chart_when_sub_now) else stringResource(R.string.chart_when_sub_set),
        onClick = onWhen,
        modifier = modifier,
    )
}

@Composable
private fun WhereField(state: ChartState, onWhere: () -> Unit, modifier: Modifier) {
    Field(
        label = state.scene.place?.let { whereText(it) } ?: "—",
        sub = if (state.usingGps) stringResource(R.string.chart_where_sub_gps) else stringResource(R.string.chart_where_sub_by_hand),
        onClick = onWhere,
        modifier = modifier,
    )
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
    // The phone's own order and clock: 12 or 24 hour as chosen in Android's settings.
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val format = remember(configuration) {
        val locale = Locale.getDefault()
        val skeleton = if (android.text.format.DateFormat.is24HourFormat(context)) "dMMMyyyyHm" else "dMMMyyyyhm"
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
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
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        TextMMD(text = stringResource(R.string.chart_field, label, sub), style = MaterialTheme.typography.labelSmall)
    }
}
