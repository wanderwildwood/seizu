package com.wanderwildwood.seizu.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.seizu.BuildConfig
import com.wanderwildwood.seizu.sky.BodyKind
import com.wanderwildwood.seizu.sky.SkyBody
import com.wanderwildwood.seizu.sky.SkyObject
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
    onSet: (Double, Double) -> Unit,
    onUseGps: () -> Unit,
    onDismiss: () -> Unit,
) {
    var lat by remember { mutableStateOf(latitude.toString()) }
    var lon by remember { mutableStateOf(longitude.toString()) }
    var bad by remember { mutableStateOf(false) }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = "Where", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        TextMMD(text = "Latitude, degrees north", fontSize = 13.sp)
        TextFieldMMD(value = lat, onValueChange = { lat = it; bad = false })
        Spacer(Modifier.height(10.dp))
        TextMMD(text = "Longitude, degrees east", fontSize = 13.sp)
        TextFieldMMD(value = lon, onValueChange = { lon = it; bad = false })

        if (bad) {
            Spacer(Modifier.height(8.dp))
            TextMMD(
                text = "Latitude runs -90 to 90, longitude -180 to 180.",
                fontSize = 13.sp,
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
        ) { TextMMD(text = "Use this", fontSize = 15.sp) }

        if (canUseGps) {
            Spacer(Modifier.height(8.dp))
            OutlinedButtonMMD(
                onClick = onUseGps,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = "Ask the GPS", fontSize = 15.sp) }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Cancel", fontSize = 15.sp) }
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

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = "When", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        TextMMD(text = "Year, month, day", fontSize = 13.sp)
        TextFieldMMD(value = year, onValueChange = { year = it; bad = false })
        TextFieldMMD(value = month, onValueChange = { month = it; bad = false })
        TextFieldMMD(value = day, onValueChange = { day = it; bad = false })
        Spacer(Modifier.height(8.dp))
        TextMMD(text = "Hour and minute, on this phone's clock", fontSize = 13.sp)
        TextFieldMMD(value = hour, onValueChange = { hour = it; bad = false })
        TextFieldMMD(value = minute, onValueChange = { minute = it; bad = false })

        if (bad) {
            Spacer(Modifier.height(8.dp))
            TextMMD(text = "That is not a date this can chart.", fontSize = 13.sp)
        }

        Spacer(Modifier.height(14.dp))
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
        ) { TextMMD(text = "Chart this moment", fontSize = 15.sp) }

        Spacer(Modifier.height(8.dp))
        OutlinedButtonMMD(
            onClick = { onSet(null) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Back to now", fontSize = 15.sp) }

        Spacer(Modifier.height(8.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Cancel", fontSize = 15.sp) }
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
        TextMMD(text = title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
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
            }

            else -> Unit
        }

        Line("Altitude", "%.1f° above the horizon".format(objectAt.elevation))
        Line("Azimuth", "%.1f° from north".format(objectAt.azimuth))

        Spacer(Modifier.height(16.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Close", fontSize = 15.sp) }
    }
}

@Composable
private fun Line(label: String, value: String) {
    TextMMD(text = "$label: $value", fontSize = 14.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = "Star Chart ${BuildConfig.VERSION_NAME}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "Black on white, like a paper chart. A screen full of black would be " +
                "unreadable outdoors and slow to redraw here.",
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "Stars from the Yale Bright Star Catalogue. Positions are computed on " +
                "the phone; nothing is fetched and nothing is sent.",
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "After AndroidPlanisphere by Timo Engel, whose astronomy this runs on " +
                "unchanged.",
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(text = "GNU General Public License v3 or later", fontSize = 14.sp)
        TextMMD(text = "Icons from Material Symbols, Apache 2.0", fontSize = 14.sp)

        Spacer(Modifier.height(14.dp))
        TextMMD(text = "github.com/wanderwildwood/seizu", fontSize = 14.sp)

        Spacer(Modifier.height(14.dp))
        Llama()

        Spacer(Modifier.height(18.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Close", fontSize = 15.sp) }
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
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://hotspringsllamas.org/donate/")),
                    )
                }
            }
            .padding(vertical = 4.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.llama),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        TextMMD(text = "Feed the llamas", fontSize = 14.sp)
    }
}
