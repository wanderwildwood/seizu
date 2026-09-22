package com.wanderwildwood.seizu.sky

import android.content.Context

/** What is set: where you are, which way the chart is turned, and what is drawn on it. */
class Preferences(context: Context) {

    private val store = context.getSharedPreferences("starchart", Context.MODE_PRIVATE)

    /** Greenwich until told otherwise — a real place, and obviously not yours. */
    var latitude: Double
        get() = Double.fromBits(store.getLong(LAT, DEFAULT_LAT.toRawBits()))
        set(value) = store.edit().putLong(LAT, value.toRawBits()).apply()

    var longitude: Double
        get() = Double.fromBits(store.getLong(LON, DEFAULT_LON.toRawBits()))
        set(value) = store.edit().putLong(LON, value.toRawBits()).apply()

    var facing: Int
        get() = store.getInt(FACING, 0)
        set(value) = store.edit().putInt(FACING, value).apply()

    var layers: Layers
        get() = Layers(
            constellationLines = store.getBoolean(CONST_LINES, true),
            constellationNames = store.getBoolean(CONST_NAMES, true),
            constellationBoundaries = store.getBoolean(CONST_BOUNDS, false),
            naming = named(store.getString(NAMING, null), ConstellationNaming.LATIN),
            starNames = store.getBoolean(STAR_NAMES, false),
            solarSystem = store.getBoolean(SOLAR, true),
            solarNames = store.getBoolean(SOLAR_NAMES, true),
            ecliptic = store.getBoolean(ECLIPTIC, false),
            equator = store.getBoolean(EQUATOR, false),
            grid = store.getBoolean(GRID, false),
            altitudeGrid = store.getBoolean(ALT_GRID, false),
            magnitudeLimit = store.getFloat(MAGNITUDE, 5.0f).toDouble(),
            markWeight = weighted(store.getString(MARK_WEIGHT, null), MarkWeight.MEDIUM),
        )
        set(value) {
            store.edit()
                .putBoolean(CONST_LINES, value.constellationLines)
                .putBoolean(CONST_NAMES, value.constellationNames)
                .putBoolean(CONST_BOUNDS, value.constellationBoundaries)
                .putString(NAMING, value.naming.name)
                .putBoolean(STAR_NAMES, value.starNames)
                .putBoolean(SOLAR, value.solarSystem)
                .putBoolean(SOLAR_NAMES, value.solarNames)
                .putBoolean(ECLIPTIC, value.ecliptic)
                .putBoolean(EQUATOR, value.equator)
                .putBoolean(GRID, value.grid)
                .putBoolean(ALT_GRID, value.altitudeGrid)
                .putFloat(MAGNITUDE, value.magnitudeLimit.toFloat())
                .putString(MARK_WEIGHT, value.markWeight.name)
                .apply()
        }

    private companion object {
        /**
         * A stored name that no longer exists falls back rather than throwing.
         *
         * Settings outlive the version that wrote them: an enum entry renamed in a later
         * build would otherwise crash the app on the first launch after the update, with
         * the only cure being to clear its data.
         */
        fun named(stored: String?, fallback: ConstellationNaming): ConstellationNaming =
            ConstellationNaming.entries.firstOrNull { it.name == stored } ?: fallback

        fun weighted(stored: String?, fallback: MarkWeight): MarkWeight =
            MarkWeight.entries.firstOrNull { it.name == stored } ?: fallback

        const val DEFAULT_LAT = 51.4778
        const val DEFAULT_LON = -0.0014
        const val LAT = "lat"
        const val LON = "lon"
        const val FACING = "facing"
        const val CONST_LINES = "constLines"
        const val CONST_NAMES = "constNames"
        const val CONST_BOUNDS = "constBounds"
        const val NAMING = "naming"
        const val STAR_NAMES = "starNames"
        const val SOLAR = "solar"
        const val SOLAR_NAMES = "solarNames"
        const val ECLIPTIC = "ecliptic"
        const val EQUATOR = "equator"
        const val GRID = "grid"
        const val ALT_GRID = "altGrid"
        const val MAGNITUDE = "magnitude"
        const val MARK_WEIGHT = "markWeight"
    }
}
