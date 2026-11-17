package com.wanderwildwood.seizu.sky

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** The rules of the Where dialog's fields, kept apart from the dialog so they can be tested. */
object PlaceEntry {
    /**
     * A latitude or longitude as its field shows it: without its sign (the side is a button
     * beside the field), to four decimals at most, which is about ten meters. A fix from the
     * phone is a double with fifteen digits, and nobody types or reads those.
     */
    fun degreesText(degrees: Double): String =
        BigDecimal(abs(degrees)).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /**
     * A typed field read back as signed degrees: a comma counts as a decimal point, the side
     * button gives the sign, and a minus typed anyway turns the side over rather than being
     * lost. Null for anything that is not a finite number up to [limit]
     * (90 for latitude, 180 for longitude); "NaN" and "Infinity" parse as doubles but are no place.
     */
    fun degrees(text: String, positiveSide: Boolean, limit: Double): Double? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!value.isFinite() || abs(value) > limit) return null
        return if (positiveSide) value else -value
    }
}
