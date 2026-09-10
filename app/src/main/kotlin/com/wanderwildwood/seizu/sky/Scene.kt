package com.wanderwildwood.seizu.sky

/**
 * The sky as a list of things to draw, with no drawing in it.
 *
 * Upstream computes a position and paints it in the same object. Splitting them means the
 * whole sky can be built and checked with no screen present, and means the canvas code
 * has no astronomy in it at all — it takes angles and puts marks on a disc.
 */

/** Anything with a place in the sky. */
sealed interface SkyObject {
    val azimuth: Double
    val elevation: Double
    val label: String?

    val isAboveHorizon: Boolean get() = elevation >= 0
}

data class SkyStar(
    override val azimuth: Double,
    override val elevation: Double,
    val magnitude: Double,
    override val label: String?,
    /** Yale Bright Star number, for looking the object up when it is tapped. */
    val hr: Int,
    /** Greek-letter designation within its constellation, where it has one. */
    val bayerFlamsteed: String,
) : SkyObject {
    /**
     * The designation with a space where the catalogue has none.
     *
     * The Bright Star Catalogue keeps the Flamsteed number and the Bayer letter in
     * adjacent fixed-width columns, so a star like 91 Theta Herculis arrives as
     * "91The     Her" and, once the runs of spaces are collapsed, prints as "91The Her".
     * Splitting the leading digits back off is the difference between a designation and
     * a typo.
     */
    val designation: String
        get() = bayerFlamsteed.trim().let { raw ->
            val digits = raw.takeWhile { it.isDigit() }
            when {
                digits.isEmpty() -> raw
                digits.length == raw.length -> raw
                raw[digits.length] == ' ' -> raw
                else -> digits + " " + raw.substring(digits.length)
            }
        }
}

/** Sun, Moon and planets, which get a ring rather than a disc so they read as not-stars. */
data class SkyBody(
    override val azimuth: Double,
    override val elevation: Double,
    val magnitude: Double,
    override val label: String,
    val kind: BodyKind,
) : SkyObject

enum class BodyKind { SUN, MOON, PLANET }

/** A run of points to be joined up: a constellation, the horizon, the ecliptic, a grid line. */
data class SkyLine(
    val points: List<DoubleArray>,
    val kind: LineKind,
    override val label: String? = null,
) : SkyObject {
    override val azimuth: Double get() = points.firstOrNull()?.get(0) ?: 0.0
    override val elevation: Double get() = points.firstOrNull()?.get(1) ?: 0.0
}

enum class LineKind { CONSTELLATION, ECLIPTIC, EQUATOR, GRID }

/**
 * Everything to be drawn for one moment at one place.
 *
 * Rebuilt whenever the time, the place or the settings change, and never otherwise. On
 * E Ink there is no reason to rebuild it on a clock tick: the sky moves a quarter of a
 * degree a minute, which is less than the width of the marks used to draw it.
 */
data class Scene(
    val stars: List<SkyStar> = emptyList(),
    val bodies: List<SkyBody> = emptyList(),
    val lines: List<SkyLine> = emptyList(),
    val whenText: String = "",
    val whereText: String = "",
) {
    val isEmpty: Boolean get() = stars.isEmpty() && bodies.isEmpty()
}

/** What the chart is showing, all of it optional except the stars. */
data class Layers(
    val constellationLines: Boolean = true,
    val starNames: Boolean = false,
    val solarSystem: Boolean = true,
    val solarNames: Boolean = true,
    val ecliptic: Boolean = false,
    val equator: Boolean = false,
    val grid: Boolean = false,
    /** Faintest star drawn. 6 is roughly the naked-eye limit under a dark sky. */
    val magnitudeLimit: Double = 5.0,
)
