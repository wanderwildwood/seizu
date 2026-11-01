package com.wanderwildwood.seizu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.seizu.R

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/seizu"

/** What the APK carries that is not this app's own code, and the licence each project declares. */
val LICENCE_COMPONENTS: List<LicenceComponent> = listOf(
    LicenceComponent("AndroidPlanisphere (the astronomy, kept in its own package)", "Timo Engel", "GPL-3.0-or-later", "https://github.com/tengel/AndroidPlanisphere"),
    LicenceComponent("Constellation lines, boundaries, names and star names, carried with AndroidPlanisphere", "Timo Engel and the sources he credits", "GPL-3.0-or-later", "https://github.com/tengel/AndroidPlanisphere"),
    LicenceComponent("Bright Star Catalogue, 5th Revised Edition", "Hoffleit and Warren, Astronomical Data Center", "Freely distributed by the Astronomical Data Center", "https://cdsarc.cds.unistra.fr/viz-bin/cat/V/50"),
    LicenceComponent("JPL Horizons ephemeris tables, 1979 to 2050", "NASA Jet Propulsion Laboratory", "Public data", "https://ssd.jpl.nasa.gov/horizons/"),
    LicenceComponent("Mudita Mindful Design (MMD)", "Mudita", "Apache-2.0", "https://github.com/mudita/MMD"),
    LicenceComponent("Lato typeface, supplied with MMD", "\u0141ukasz Dziedzic", "OFL-1.1", "https://www.latofonts.com"),
    LicenceComponent("AndroidX and Jetpack Compose", "Google", "Apache-2.0", "https://developer.android.com/jetpack"),
    LicenceComponent("Kotlin and kotlinx.coroutines", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
    LicenceComponent("Material Symbols icons", "Google", "Apache-2.0", "https://fonts.google.com/icons"),
)

/** Each distinct licence text once: family id and the raw resource. */
val LICENCE_RAW: List<Pair<String, Int>> = listOf(
    "gpl" to R.raw.gpl_3_0,
    "apache" to R.raw.apache_2_0,
    "ofl" to R.raw.ofl_1_1,
)

@Composable
fun rememberLicencesData(): LicencesData = LicencesData(
    appName = "Star Chart",
    sourceUrl = SOURCE_URL,
    licenceLine = stringResource(R.string.licences_licence),
    components = LICENCE_COMPONENTS,
    texts = LICENCE_RAW.map { (id, raw) ->
        LicenceText(id, stringResource(when (id) {
            "gpl" -> R.string.licences_full_gpl
            "apache" -> R.string.licences_full_apache
            "ofl" -> R.string.licences_full_ofl
            else -> R.string.licences_full_head
        }), raw)
    },
)

@Composable
fun rememberLicencesLabels(): LicencesLabels {
    val none = stringResource(R.string.about_no_browser_address, "@@")
    return LicencesLabels(
        title = stringResource(R.string.licences_title),
        back = stringResource(R.string.licences_back),
        sourceHeading = stringResource(R.string.licences_source_head),
        licenceHeading = stringResource(R.string.licences_licence_head),
        builtWithHeading = stringResource(R.string.licences_built_head),
        fullTextsHeading = stringResource(R.string.licences_full_head),
        noBrowser = { address -> none.replace("@@", address) },
    )
}
