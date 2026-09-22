package com.wanderwildwood.seizu.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.seizu.BuildConfig
import com.wanderwildwood.seizu.sky.BodyKind
import com.wanderwildwood.seizu.sky.SkyBody
import com.wanderwildwood.seizu.sky.SkyObject
import com.wanderwildwood.seizu.sky.phaseName
import com.wanderwildwood.seizu.sky.steppedTime
import com.wanderwildwood.seizu.sky.SkyStar
import java.util.GregorianCalendar
import android.content.Intent
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
import com.wanderwildwood.seizu.R
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
    onSet: (Double, Double) -> Unit,
    onUseGps: () -> Unit,
    onDismiss: () -> Unit,
) {
    var lat by remember { mutableStateOf(latitude.toString()) }
    var lon by remember { mutableStateOf(longitude.toString()) }
    var bad by remember { mutableStateOf(false) }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = "Where", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        TextMMD(text = "Latitude, degrees north", style = MaterialTheme.typography.labelSmall)
        TextFieldMMD(value = lat, onValueChange = { lat = it; bad = false })
        Spacer(Modifier.height(10.dp))
        TextMMD(text = "Longitude, degrees east", style = MaterialTheme.typography.labelSmall)
        TextFieldMMD(value = lon, onValueChange = { lon = it; bad = false })

        if (bad) {
            Spacer(Modifier.height(8.dp))
            TextMMD(
                text = "Latitude runs -90 to 90, longitude -180 to 180.",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButtonMMD(
            onClick = {
                val la = lat.trim().toDoubleOrNull()
                val lo = lon.trim().toDoubleOrNull()
                if (la == null || lo == null || la < -90 || la > 90 || lo < -180 || lo > 180) {
                    bad = true
                } else {
                    onSet(la, lo)
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Use this", style = MaterialTheme.typography.bodySmall) }

        if (canUseGps) {
            Spacer(Modifier.height(8.dp))
            OutlinedButtonMMD(
                onClick = onUseGps,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = "Ask the GPS", style = MaterialTheme.typography.bodySmall) }

            if (noFix) {
                Spacer(Modifier.height(8.dp))
                TextMMD(
                    text = "The phone has no recent fix. Step outside and try again, " +
                        "or set it by hand \u2014 a chart is not fussy about a mile.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Cancel", style = MaterialTheme.typography.bodySmall) }
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
    var year by remember { mutableStateOf(start.get(java.util.Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((start.get(java.util.Calendar.MONTH) + 1).toString()) }
    var day by remember { mutableStateOf(start.get(java.util.Calendar.DAY_OF_MONTH).toString()) }
    var hour by remember { mutableStateOf(start.get(java.util.Calendar.HOUR_OF_DAY).toString()) }
    var minute by remember { mutableStateOf(start.get(java.util.Calendar.MINUTE).toString()) }
    var bad by remember { mutableStateOf(false) }

    // Stepping writes back into the same five fields, so the buttons and the typing are
    // the one answer rather than two ways in. Nothing is charted until "Chart this
    // moment", which is what lets you step four days and look before committing to it.
    fun step(hours: Int, days: Int) {
        val from = GregorianCalendar(
            year.trim().toIntOrNull() ?: start.get(java.util.Calendar.YEAR),
            (month.trim().toIntOrNull() ?: (start.get(java.util.Calendar.MONTH) + 1)) - 1,
            day.trim().toIntOrNull() ?: start.get(java.util.Calendar.DAY_OF_MONTH),
            hour.trim().toIntOrNull() ?: start.get(java.util.Calendar.HOUR_OF_DAY),
            minute.trim().toIntOrNull() ?: start.get(java.util.Calendar.MINUTE),
        )
        val next = steppedTime(from, hours, days)
        year = next.get(java.util.Calendar.YEAR).toString()
        month = (next.get(java.util.Calendar.MONTH) + 1).toString()
        day = next.get(java.util.Calendar.DAY_OF_MONTH).toString()
        hour = next.get(java.util.Calendar.HOUR_OF_DAY).toString()
        minute = next.get(java.util.Calendar.MINUTE).toString()
        bad = false
    }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = "When", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        // Three fields on the line their label already describes, rather than stacked: a
        // date is one fact, and stacked it took two thirds of the dialog's height for it.
        TextMMD(text = "Year, month, day", style = MaterialTheme.typography.labelSmall)
        Row(modifier = Modifier.fillMaxWidth()) {
            TextFieldMMD(value = year, onValueChange = { year = it; bad = false }, modifier = Modifier.weight(1.4f))
            Spacer(Modifier.width(6.dp))
            TextFieldMMD(value = month, onValueChange = { month = it; bad = false }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            TextFieldMMD(value = day, onValueChange = { day = it; bad = false }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        TextMMD(text = "Hour and minute, on this phone's clock", style = MaterialTheme.typography.labelSmall)
        Row(modifier = Modifier.fillMaxWidth()) {
            TextFieldMMD(value = hour, onValueChange = { hour = it; bad = false }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            TextFieldMMD(value = minute, onValueChange = { minute = it; bad = false }, modifier = Modifier.weight(1f))
        }

        if (bad) {
            Spacer(Modifier.height(8.dp))
            TextMMD(text = "That is not a date this can chart.", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StepButton("\u2212 day", Modifier.weight(1f)) { step(0, -1) }
            Spacer(Modifier.width(6.dp))
            StepButton("\u2212 hr", Modifier.weight(1f)) { step(-1, 0) }
            Spacer(Modifier.width(6.dp))
            StepButton("+ hr", Modifier.weight(1f)) { step(1, 0) }
            Spacer(Modifier.width(6.dp))
            StepButton("+ day", Modifier.weight(1f)) { step(0, 1) }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButtonMMD(
            onClick = {
                val y = year.trim().toIntOrNull()
                val mo = month.trim().toIntOrNull()
                val d = day.trim().toIntOrNull()
                val h = hour.trim().toIntOrNull()
                val mi = minute.trim().toIntOrNull()
                if (y == null || mo == null || d == null || h == null || mi == null ||
                    mo !in 1..12 || d !in 1..31 || h !in 0..23 || mi !in 0..59
                ) {
                    bad = true
                } else {
                    onSet(GregorianCalendar(y, mo - 1, d, h, mi))
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Chart this moment", style = MaterialTheme.typography.bodySmall) }

        Spacer(Modifier.height(8.dp))
        OutlinedButtonMMD(
            onClick = { onSet(null) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Back to now", style = MaterialTheme.typography.bodySmall) }

        Spacer(Modifier.height(8.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Cancel", style = MaterialTheme.typography.bodySmall) }
    }
}

/** What you just tapped. */
@Composable
fun ObjectDialog(objectAt: SkyObject, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        val title = when (objectAt) {
            is SkyStar -> objectAt.label ?: "HR ${objectAt.hr}"
            is SkyBody -> objectAt.label
            else -> "Object"
        }
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        when (objectAt) {
            is SkyStar -> {
                if (objectAt.designation.isNotBlank()) {
                    Line("Designation", objectAt.designation)
                }
                Line("Catalogue", "HR ${objectAt.hr}")
                Line("Magnitude", "%.2f".format(objectAt.magnitude))
            }

            is SkyBody -> {
                Line(
                    "Kind", when (objectAt.kind) {
                        BodyKind.SUN -> "The Sun"
                        BodyKind.MOON -> "The Moon"
                        BodyKind.PLANET -> "Planet"
                    }
                )
                Line("Magnitude", "%.2f".format(objectAt.magnitude))
                // Only the Moon has one, and it is the thing worth knowing about it: the
                // mark can show roughly how much is lit, but not how much to the percent.
                objectAt.phase?.let { phase ->
                    Line(
                        "Phase",
                        "%s, %d%% lit".format(phaseName(phase), (phase.illuminated * 100).roundToInt()),
                    )
                }
            }

            else -> Unit
        }

        Line("Altitude", "%.1f° above the horizon".format(objectAt.elevation))
        Line("Azimuth", "%.1f° from north".format(objectAt.azimuth))

        Spacer(Modifier.height(16.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Close", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Line(label: String, value: String) {
    TextMMD(text = "$label: $value", style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = "Star Chart ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "Black on white, like a paper chart. A screen full of black would be " +
                "unreadable outdoors and slow to redraw here.",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "Stars from the Yale Bright Star Catalogue. Positions are computed on " +
                "the phone; nothing is fetched and nothing is sent.",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "After AndroidPlanisphere by Timo Engel, whose astronomy this runs on " +
                "unchanged.",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(text = "GNU General Public License v3 or later", style = MaterialTheme.typography.labelSmall)
        TextMMD(text = "Icons from Material Symbols, Apache 2.0", style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(14.dp))
        TextMMD(text = "github.com/wanderwildwood/seizu", style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(14.dp))
        Llama()

        Spacer(Modifier.height(18.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Close", style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Straight to the checkout. The Donate button on the site only leads
                // here anyway, so the page in between is a press the reader does not need.
                // The short square.link form, not the long checkout.square.site address it
                // redirects to -- the short one is what the site itself links to, so a
                // regenerated checkout follows it and a published app does not break.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://square.link/u/AGu8oT10")),
                    )
                }.onFailure {
                    Toast.makeText(context, "There is no browser on this phone to open that with.", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 4.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.llama),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        TextMMD(text = "Feed the llamas", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StepButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButtonMMD(
        onClick = onClick,
        modifier = modifier.height(40.dp),
    ) { TextMMD(text = label, style = MaterialTheme.typography.labelSmall) }
}
