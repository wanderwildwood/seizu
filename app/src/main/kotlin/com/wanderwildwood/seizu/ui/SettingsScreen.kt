package com.wanderwildwood.seizu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.seizu.R
import com.wanderwildwood.seizu.sky.ConstellationNaming
import com.wanderwildwood.seizu.sky.Layers

/**
 * What is drawn on the chart, on one page.
 *
 * Every row is one line, its value at the right, and the short ones go two to a row, so the
 * whole screen fits the Kompakt's panel without a page to turn. Where two rows used to say
 * one thing (a layer, then what to call it) they are now one row that steps through the
 * choices, off included. The groups are told apart by a gap rather than a rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    layers: Layers,
    turnWithPhone: Boolean,
    onClose: () -> Unit,
    onLayers: (Layers) -> Unit,
    onTurnWithPhone: (Boolean) -> Unit,
) {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val on = stringResource(R.string.settings_on)
    val off = stringResource(R.string.settings_off)
    fun onOff(value: Boolean) = if (value) on else off

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.settings_title)) },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.settings_cd_close), onClose) },
                actions = { BarButton(Icons.Info, stringResource(R.string.settings_cd_about), { aboutOpen = true }) },
            )
        },
    ) { contentPadding ->
        // A paged list, not a scrolling Column: it fits one page upright, and on its side,
        // where it cannot, it turns a page on whole rows with no scrollbar beside it.
        PagedColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            item {
                Cells {
                    Cell(
                        title = stringResource(R.string.settings_faintest_star),
                        value = stringResource(
                            R.string.settings_magnitude_value,
                            layers.magnitudeLimit,
                            stringResource(
                                when {
                                    layers.magnitudeLimit <= 3 -> R.string.settings_magnitude_note_bright
                                    layers.magnitudeLimit <= 5 -> R.string.settings_magnitude_note_town
                                    else -> R.string.settings_magnitude_note_dark
                                },
                            ),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        // 3, 4, 5, 6 and round again. Below 3 the sky is nearly empty and
                        // above 6 the panel cannot separate the dots.
                        val next = if (layers.magnitudeLimit >= 6.0) 3.0 else layers.magnitudeLimit + 1.0
                        onLayers(layers.copy(magnitudeLimit = next))
                    }
                    Cell(
                        title = stringResource(R.string.settings_mark_weight),
                        value = stringResource(layers.markWeight.labelRes),
                        modifier = Modifier.weight(1f),
                    ) { onLayers(layers.copy(markWeight = layers.markWeight.next())) }
                }
            }
            item { Gap() }
            item {
                Line(stringResource(R.string.settings_constellation_figures), onOff(layers.constellationLines)) {
                    onLayers(layers.copy(constellationLines = !layers.constellationLines))
                }
            }
            item {
                // Off, then each way of naming them, then off again.
                Line(
                    stringResource(R.string.settings_constellation_names),
                    if (layers.constellationNames) stringResource(layers.naming.labelRes) else off,
                ) {
                    onLayers(
                        when {
                            !layers.constellationNames ->
                                layers.copy(constellationNames = true, naming = ConstellationNaming.entries.first())
                            layers.naming == ConstellationNaming.entries.last() ->
                                layers.copy(constellationNames = false)
                            else -> layers.copy(naming = layers.naming.next())
                        },
                    )
                }
            }
            item {
                Line(stringResource(R.string.settings_constellation_boundaries), onOff(layers.constellationBoundaries)) {
                    onLayers(layers.copy(constellationBoundaries = !layers.constellationBoundaries))
                }
            }
            item {
                Line(stringResource(R.string.settings_star_names), onOff(layers.starNames)) {
                    onLayers(layers.copy(starNames = !layers.starNames))
                }
            }
            item {
                // Named, then unnamed, then off.
                Line(
                    stringResource(R.string.settings_solar_system),
                    when {
                        !layers.solarSystem -> off
                        layers.solarNames -> stringResource(R.string.settings_solar_named)
                        else -> stringResource(R.string.settings_solar_unnamed)
                    },
                ) {
                    onLayers(
                        when {
                            !layers.solarSystem -> layers.copy(solarSystem = true, solarNames = true)
                            layers.solarNames -> layers.copy(solarNames = false)
                            else -> layers.copy(solarSystem = false)
                        },
                    )
                }
            }
            item { Gap() }
            item {
                Cells {
                    Cell(stringResource(R.string.settings_ecliptic), onOff(layers.ecliptic), Modifier.weight(1f)) {
                        onLayers(layers.copy(ecliptic = !layers.ecliptic))
                    }
                    Cell(stringResource(R.string.settings_equator), onOff(layers.equator), Modifier.weight(1f)) {
                        onLayers(layers.copy(equator = !layers.equator))
                    }
                }
            }
            item {
                Cells {
                    Cell(stringResource(R.string.settings_grid), onOff(layers.grid), Modifier.weight(1f)) {
                        onLayers(layers.copy(grid = !layers.grid))
                    }
                    Cell(stringResource(R.string.settings_altitude_circles), onOff(layers.altitudeGrid), Modifier.weight(1f)) {
                        onLayers(layers.copy(altitudeGrid = !layers.altitudeGrid))
                    }
                }
            }
            item { Gap() }
            item {
                Line(stringResource(R.string.settings_turn), onOff(turnWithPhone)) { onTurnWithPhone(!turnWithPhone) }
            }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
}

/** Space between groups, where there used to be a rule. */
@Composable
private fun Gap() {
    Spacer(Modifier.height(10.dp))
}

/** A whole-width row: what it is on the left, its value on the right, on one line. */
@Composable
private fun Line(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
    }
}

/** Two settings side by side. */
@Composable
private fun Cells(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Half a row: what it is, with its value under it. */
@Composable
private fun Cell(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        TextMMD(text = value, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
