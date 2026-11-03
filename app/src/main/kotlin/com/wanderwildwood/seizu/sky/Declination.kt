package com.wanderwildwood.seizu.sky

import android.hardware.GeomagneticField

/**
 * Magnetic north is not true north.
 *
 * The phone's rotation vector takes its bearing from the compass, so every azimuth it
 * gives is measured from magnetic north, while the chart's azimuths are measured from true
 * north. The difference, the declination, is about fourteen degrees west in New England and
 * as much as twenty in places: more than the five degrees Point waits for before it
 * redraws, so without this the chart would be turned by a fixed, visible amount.
 *
 * East is positive, as Android and the charts have it: a bearing read off the compass plus
 * the declination is the true bearing.
 */
fun trueAim(magnetic: Aim, declination: Double): Aim =
    magnetic.copy(azimuth = normaliseAzimuth(magnetic.azimuth + declination))

/**
 * The declination at a place, from Android's own copy of the World Magnetic Model. Sea
 * level is close enough: a mountain changes it by a small fraction of a degree. It drifts
 * by a fraction of a degree a year, so the moment it is worked out for matters little.
 */
fun magneticDeclination(latitude: Double, longitude: Double, atMillis: Long): Double =
    GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, atMillis).declination.toDouble()
