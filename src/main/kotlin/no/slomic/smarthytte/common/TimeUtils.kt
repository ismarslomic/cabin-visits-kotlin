package no.slomic.smarthytte.common

import kotlin.math.abs

const val MINUTES_PER_HOUR: Int = 60
const val HOURS_PER_DAY: Int = 24

/**
 * Converts a given total number of minutes (representing a duration) into a formatted time string
 * in the format "HH:MM" or "±HH:MM".
 *
 * This function calculates hours by dividing total minutes by 60 without any upper limit,
 * making it suitable for representing total durations that may exceed 24 hours.
 * Example: 1500 minutes will be formatted as "25:00" or "+25:00" if [showSign] is true.
 *
 * @param totalMinutes The total number of minutes to format. If null, the function will return null.
 * @param showSign If true, the result will be prefixed with '+' for positive values and '-' for negative values.
 * @return A formatted string representing the duration, or null if the input is null.
 */
fun formatMinutes(totalMinutes: Int?, showSign: Boolean = false): String? = totalMinutes?.let {
    val sign = if (showSign) {
        if (it > 0) {
            "+"
        } else if (it < 0) {
            "-"
        } else {
            ""
        }
    } else {
        ""
    }
    val abs = abs(it)
    val h = abs / MINUTES_PER_HOUR
    val m = abs % MINUTES_PER_HOUR
    sign + "%02d:%02d".format(h, m)
}

/**
 * Formats the given time in minutes since midnight into a string representation of the clock time
 * in "HH:MM" format.
 *
 * Unlike [formatMinutes], this function applies a modulo 24 to the hour calculation,
 * ensuring the result is always a valid time of day between "00:00" and "23:59".
 * Example: 1500 minutes (25 hours) will be formatted as "01:00".
 *
 * @param minutesOfDay The time in minutes since midnight, or null.
 * @return A string representing the time of day in "HH:MM" format, or null if the input is null.
 */
fun formatClock(minutesOfDay: Int?): String? = minutesOfDay?.let {
    val h = (it / MINUTES_PER_HOUR) % HOURS_PER_DAY
    val m = it % MINUTES_PER_HOUR
    "%02d:%02d".format(h, m)
}
