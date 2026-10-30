package com.wanderwildwood.seizu.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.seizu.BuildConfig
import com.wanderwildwood.seizu.sky.BodyKind
import com.wanderwildwood.seizu.sky.TimeEntry
import com.wanderwildwood.seizu.sky.SkyBody
import com.wanderwildwood.seizu.sky.SkyObject
import com.wanderwildwood.seizu.sky.phaseName
import com.wanderwildwood.seizu.sky.steppedTime
import com.wanderwildwood.seizu.sky.SkyStar
import java.util.GregorianCalendar
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalConfiguration
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.seizu.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where you are.
 *
 * Typed in, or taken from the GPS. Typed in matters more than it looks: a planisphere is
 * often wanted for somewhere you are not — checking what will be up at a campsite next
 * week — and an app that can only ever chart the spot it is standing on cannot do that.
 */
@Composable
fun LocationDialog(
    latitude: Double,
    longitude: Double,
    canUseGps: Boolean,
    /** The GPS was asked and had nothing to give. Said out loud, or the button reads as broken. */
    noFix: Boolean,
    /** Location was refused. Said with the way back, never by silently closing. */
    denied: Boolean,
    onOpenSettings: () -> Unit,
    onSet: (Double, Double) -> Unit,
    onUseGps: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The numbers without their signs, and the side of the equator and of Greenwich as a
    // button beside each: a number keyboard does not always have a minus on it, and a
    // longitude west of Greenwich typed without one is the other side of the world.
    var lat by rememberSaveable { mutableStateOf(abs(latitude).toString()) }
    var lon by rememberSaveable { mutableStateOf(abs(longitude).toString()) }
    var north by rememberSaveable { mutableStateOf(latitude >= 0) }
    var east by rememberSaveable { mutableStateOf(longitude >= 0) }
    var bad by rememberSaveable { mutableStateOf(false) }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.where_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        TwoSides(first = {
            TextMMD(text = stringResource(R.string.where_latitude), style = MaterialTheme.typography.labelSmall)
            DegreesField(
                value = lat,
                onValue = { lat = it; bad = false },
                side = stringResource(if (north) R.string.compass_north else R.string.compass_south),
                sideDescription = stringResource(R.string.where_cd_north_south),
                onSide = { north = !north },
            )
            Spacer(Modifier.height(10.dp))
            TextMMD(text = stringResource(R.string.where_longitude), style = MaterialTheme.typography.labelSmall)
            DegreesField(
                value = lon,
                onValue = { lon = it; bad = false },
                side = stringResource(if (east) R.string.compass_east else R.string.compass_west),
                sideDescription = stringResource(R.string.where_cd_east_west),
                onSide = { east = !east },
            )

            if (bad) {
                Spacer(Modifier.height(8.dp))
                TextMMD(
                    text = stringResource(R.string.where_out_of_range),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }, second = {
            OutlinedButtonMMD(
                onClick = {
                    val la = signed(lat, north)
                    val lo = signed(lon, east)
                    if (la == null || lo == null || la < -90 || la > 90 || lo < -180 || lo > 180) {
                        bad = true
                    } else {
                        onSet(la, lo)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = stringResource(R.string.where_use_this), style = MaterialTheme.typography.bodySmall) }

            if (canUseGps) {
                Spacer(Modifier.height(8.dp))
                OutlinedButtonMMD(
                    onClick = onUseGps,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { TextMMD(text = stringResource(R.string.where_ask_gps), style = MaterialTheme.typography.bodySmall) }

                if (denied) {
                    Spacer(Modifier.height(8.dp))
                    TextMMD(
                        text = stringResource(R.string.where_denied),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedButtonMMD(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { TextMMD(text = stringResource(R.string.open_app_settings), style = MaterialTheme.typography.bodySmall) }
                }

                if (noFix) {
                    Spacer(Modifier.height(8.dp))
                    TextMMD(
                        text = stringResource(R.string.where_no_fix),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButtonMMD(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = stringResource(R.string.where_cancel), style = MaterialTheme.typography.bodySmall) }
        })
    }
}

/**
 * When.
 *
 * Either the clock, or a moment you choose. Choosing is what makes it a planisphere
 * rather than a window: the useful question is usually what the sky will look like at ten
 * tonight, not what it looks like indoors at four in the afternoon.
 */
@Composable
fun TimeDialog(
    current: GregorianCalendar?,
    onSet: (GregorianCalendar?) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = current ?: GregorianCalendar()
    var year by rememberSaveable { mutableStateOf(start.get(java.util.Calendar.YEAR).toString()) }
    var month by rememberSaveable { mutableStateOf((start.get(java.util.Calendar.MONTH) + 1).toString()) }
    var day by rememberSaveable { mutableStateOf(start.get(java.util.Calendar.DAY_OF_MONTH).toString()) }
    // The phone's own clock: on a 12 hour phone the hour is typed 1 to 12 with an AM / PM button.
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val startHour = start.get(java.util.Calendar.HOUR_OF_DAY)
    var hour by rememberSaveable { mutableStateOf((if (is24) startHour else TimeEntry.from24(startHour).first).toString()) }
    var pm by rememberSaveable { mutableStateOf(startHour >= 12) }
    /** The hour field as 0..23, or null when it is not a valid hour for this clock. */
    fun hour24(): Int? {
        val h = hour.trim().toIntOrNull() ?: return null
        return if (is24) h.takeIf { it in 0..23 } else h.takeIf { it in 1..12 }?.let { TimeEntry.to24(it, pm) }
    }
    var minute by rememberSaveable { mutableStateOf(start.get(java.util.Calendar.MINUTE).toString()) }
    var bad by rememberSaveable { mutableStateOf(false) }

    // Stepping writes back into the same five fields, so the buttons and the typing are
    // the one answer rather than two ways in. Nothing is charted until "Chart this
    // moment", which is what lets you step four days and look before committing to it.
    fun step(hours: Int, days: Int) {
        val from = GregorianCalendar(
            year.trim().toIntOrNull() ?: start.get(java.util.Calendar.YEAR),
            (month.trim().toIntOrNull() ?: (start.get(java.util.Calendar.MONTH) + 1)) - 1,
            day.trim().toIntOrNull() ?: start.get(java.util.Calendar.DAY_OF_MONTH),
            hour24() ?: start.get(java.util.Calendar.HOUR_OF_DAY),
            minute.trim().toIntOrNull() ?: start.get(java.util.Calendar.MINUTE),
        )
        val next = steppedTime(from, hours, days)
        year = next.get(java.util.Calendar.YEAR).toString()
        month = (next.get(java.util.Calendar.MONTH) + 1).toString()
        day = next.get(java.util.Calendar.DAY_OF_MONTH).toString()
        val nextHour = next.get(java.util.Calendar.HOUR_OF_DAY)
        hour = (if (is24) nextHour else TimeEntry.from24(nextHour).first).toString()
        pm = nextHour >= 12
        minute = next.get(java.util.Calendar.MINUTE).toString()
        bad = false
    }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.when_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        TwoSides(first = {
            // Three fields on the line their label already describes, rather than stacked: a
            // date is one fact, and stacked it took two thirds of the dialog's height for it.
            TextMMD(text = stringResource(R.string.when_date), style = MaterialTheme.typography.labelSmall)
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberField(value = year, onValue = { year = it; bad = false }, modifier = Modifier.weight(1.4f))
                Spacer(Modifier.width(6.dp))
                NumberField(value = month, onValue = { month = it; bad = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                NumberField(value = day, onValue = { day = it; bad = false }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            TextMMD(text = stringResource(R.string.when_time), style = MaterialTheme.typography.labelSmall)
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberField(value = hour, onValue = { hour = it; bad = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                NumberField(value = minute, onValue = { minute = it; bad = false }, modifier = Modifier.weight(1f))
                if (!is24) {
                    Spacer(Modifier.width(6.dp))
                    StepButton(
                        stringResource(if (pm) R.string.when_pm else R.string.when_am),
                        Modifier.weight(1f),
                    ) { pm = !pm; bad = false }
                }
            }

            if (bad) {
                Spacer(Modifier.height(8.dp))
                TextMMD(text = stringResource(R.string.when_bad_date), style = MaterialTheme.typography.labelSmall)
            }
        }, second = {
            Row(modifier = Modifier.fillMaxWidth()) {
                StepButton(stringResource(R.string.when_step_back_day), Modifier.weight(1f)) { step(0, -1) }
                Spacer(Modifier.width(6.dp))
                StepButton(stringResource(R.string.when_step_back_hour), Modifier.weight(1f)) { step(-1, 0) }
                Spacer(Modifier.width(6.dp))
                StepButton(stringResource(R.string.when_step_forward_hour), Modifier.weight(1f)) { step(1, 0) }
                Spacer(Modifier.width(6.dp))
                StepButton(stringResource(R.string.when_step_forward_day), Modifier.weight(1f)) { step(0, 1) }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButtonMMD(
                onClick = {
                    val y = year.trim().toIntOrNull()
                    val mo = month.trim().toIntOrNull()
                    val d = day.trim().toIntOrNull()
                    val h = hour24()
                    val mi = minute.trim().toIntOrNull()
                    if (y == null || mo == null || d == null || h == null || mi == null ||
                        !TimeEntry.dateOk(y, mo, d) || mi !in 0..59
                    ) {
                        bad = true
                    } else {
                        onSet(GregorianCalendar(y, mo - 1, d, h, mi))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = stringResource(R.string.when_chart_this_moment), style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(8.dp))
            OutlinedButtonMMD(
                onClick = { onSet(null) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = stringResource(R.string.when_back_to_now), style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(8.dp))
            OutlinedButtonMMD(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = stringResource(R.string.when_cancel), style = MaterialTheme.typography.bodySmall) }
        })
    }
}

/** What you just tapped. */
@Composable
fun ObjectDialog(objectAt: SkyObject, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        val title = when (objectAt) {
            is SkyStar -> objectAt.label ?: "HR ${objectAt.hr}"
            is SkyBody -> bodyName(objectAt)
            else -> stringResource(R.string.object_title_fallback)
        }
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        when (objectAt) {
            is SkyStar -> {
                if (objectAt.designation.isNotBlank()) {
                    Line(stringResource(R.string.object_designation), objectAt.designation)
                }
                Line(stringResource(R.string.object_catalogue), "HR ${objectAt.hr}")
                Line(stringResource(R.string.object_magnitude), "%.2f".format(objectAt.magnitude))
            }

            is SkyBody -> {
                Line(
                    stringResource(R.string.object_kind), when (objectAt.kind) {
                        BodyKind.SUN -> stringResource(R.string.object_kind_sun)
                        BodyKind.MOON -> stringResource(R.string.object_kind_moon)
                        BodyKind.PLANET -> stringResource(R.string.object_kind_planet)
                    }
                )
                Line(stringResource(R.string.object_magnitude), "%.2f".format(objectAt.magnitude))
                // Only the Moon has one, and it is the thing worth knowing about it: the
                // mark can show roughly how much is lit, but not how much to the percent.
                objectAt.phase?.let { phase ->
                    Line(
                        stringResource(R.string.object_phase),
                        stringResource(R.string.object_phase_value, stringResource(phaseName(phase).labelRes), (phase.illuminated * 100).roundToInt()),
                    )
                }
            }

            else -> Unit
        }

        Line(stringResource(R.string.object_altitude), stringResource(R.string.object_altitude_value, objectAt.elevation))
        Line(stringResource(R.string.object_azimuth), stringResource(R.string.object_azimuth_value, objectAt.azimuth))

        Spacer(Modifier.height(16.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.object_close), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Line(label: String, value: String) {
    TextMMD(text = stringResource(R.string.object_line, label, value), style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(4.dp))
}

/**
 * What this is, what it does with what you tell it, and the terms.
 *
 * The words run on a [PagedColumn] inside the dialog, with Close below it: with Privacy and
 * Terms added the About is longer than the panel is tall when the phone is on its side, and
 * a dialog cut off at the foot hides exactly the part that was added to be read.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    var licences by rememberSaveable { mutableStateOf(false) }
    val room = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.62f
    // Back in the Licences page returns to the About rather than leaving both.
    EInkDialog(onDismiss = { if (licences) licences = false else onDismiss() }) {
        if (licences) {
            LicencesPage(onBack = { licences = false }, room = room)
            return@EInkDialog
        }
        PagedColumn(modifier = Modifier.weight(1f, fill = false)) {
            item {
                Column {
                    TextMMD(
                        text = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
            item { Paragraph(stringResource(R.string.about_black_on_white)) }
            item { Paragraph(stringResource(R.string.about_catalogue)) }
            item { Paragraph(stringResource(R.string.about_after)) }
            item { Section(stringResource(R.string.about_privacy_heading), stringResource(R.string.about_privacy_body)) }
            item { Section(stringResource(R.string.about_terms_heading), stringResource(R.string.about_terms_body)) }
            item { Paragraph(stringResource(R.string.about_icons)) }
            item {
                val context = LocalContext.current
                Column {
                    TextMMD(
                        text = stringResource(R.string.about_licences),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().clickable { licences = true }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                    )
                    TextMMD(
                        text = SOURCE_URL.removePrefix("https://"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openLink(context, SOURCE_URL, context.getString(R.string.about_no_browser_address, SOURCE_URL.removePrefix("https://"))) }
                            .heightIn(min = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
            item { Llama() }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.about_close), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Paragraph(text: String) {
    Column {
        TextMMD(text = text, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
    }
}

/** A heading and its words as one row, so a page never ends on a heading alone. */
@Composable
private fun Section(heading: String, body: String) {
    Column {
        TextMMD(
            text = heading,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        TextMMD(text = body, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
 * The site's address sits at the start of the same line and opens the site; the llama and its words open the page.
 *
 * Three words rather than an address: a verb and an object, so what happens when you press
 * them is not a surprise even though the page is not named. The drawing is his own, and it is
 * ink rather than an emoji, which is a colour glyph and reaches the panel as a pale smudge.
 *
 * The Kompakt may have nothing registered for a web address, so the intent is allowed to fail
 * quietly rather than take the dialog down with it.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextMMD(
            text = "wanderthe.dev",
            style = MaterialTheme.typography.labelSmall,
            // The site's address opens the site, the way the llama beside it opens its page.
            modifier = Modifier
                .clickable {
                    openLink(context, "https://wanderthe.dev", context.getString(R.string.about_no_browser_address, "wanderthe.dev"))
                }
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        )
        Spacer(Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    // Straight to the checkout. The Donate button on the site only leads
                    // here anyway, so the page in between is a press the reader does not need.
                    // The short square.link form, not the long checkout.square.site address it
                    // redirects to -- the short one is what the site itself links to, so a
                    // regenerated checkout follows it and a published app does not break.
                    openLink(context, "https://square.link/u/AGu8oT10", context.getString(R.string.about_no_browser_address, "square.link/u/AGu8oT10"))
                }
                .heightIn(min = 48.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = stringResource(R.string.about_feed_the_llamas), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * A number typed without its sign, given the sign of the side chosen beside it. A minus
 * typed anyway, on a keyboard that has one, turns the side over rather than being lost.
 */
private fun signed(text: String, positiveSide: Boolean): Double? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return if (positiveSide) value else -value
}

/** A number of degrees, with the side it lies on as a button after it. */
@Composable
private fun DegreesField(
    value: String,
    onValue: (String) -> Unit,
    side: String,
    sideDescription: String,
    onSide: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextFieldMMD(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButtonMMD(
            onClick = onSide,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = sideDescription },
            contentPadding = PaddingValues(0.dp),
        ) { TextMMD(text = side, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** A short whole number, typed on the number keyboard. */
@Composable
private fun NumberField(value: String, onValue: (String) -> Unit, modifier: Modifier) {
    TextFieldMMD(
        value = value,
        onValueChange = onValue,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * A dialog's fields and its buttons: one above the other, or side by side when the phone is
 * on its side and the panel is too short to stack them.
 */
@Composable
private fun TwoSides(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) { first() }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) { second() }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            first()
            Spacer(Modifier.height(12.dp))
            second()
        }
    }
}

@Composable
private fun StepButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButtonMMD(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        // Four to a row: the default padding left "− day" no room and folded it in two.
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) { TextMMD(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
}
