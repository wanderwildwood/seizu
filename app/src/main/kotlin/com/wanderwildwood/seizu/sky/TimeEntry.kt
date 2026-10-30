package com.wanderwildwood.seizu.sky

/**
 * The rules of the When dialog's fields, kept apart from the dialog so they can be tested.
 *
 * The outer planets come from tables that run from 1979 to 2050; a date outside them would chart
 * Jupiter to Neptune frozen at the last row, so those years are refused rather than drawn wrong.
 */
object TimeEntry {
    const val FIRST_YEAR = 1979
    const val LAST_YEAR = 2050

    private fun leap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun daysIn(year: Int, month: Int): Int = when (month) {
        2 -> if (leap(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** A real day of a real month, in the years the ephemeris covers. */
    fun dateOk(year: Int, month: Int, day: Int): Boolean =
        year in FIRST_YEAR..LAST_YEAR && month in 1..12 && day in 1..daysIn(year, month)

    /** The hour as typed on a 12 hour clock (1 to 12) and its half of the day, to 0..23. */
    fun to24(hour12: Int, pm: Boolean): Int = (hour12 % 12) + if (pm) 12 else 0

    /** 0..23 as the 12 hour clock shows it: the hour 1..12, and whether it is pm. */
    fun from24(hour: Int): Pair<Int, Boolean> = (if (hour % 12 == 0) 12 else hour % 12) to (hour >= 12)
}
