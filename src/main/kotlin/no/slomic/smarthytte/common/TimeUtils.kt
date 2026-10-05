package no.slomic.smarthytte.common

import kotlin.math.abs

/** Number of minutes in an hour. */
const val MINUTES_PER_HOUR: Int = 60

/** Number of hours in a day. */
const val HOURS_PER_DAY: Int = 24

/**
 * Formats a total duration in minutes as "HH:MM", or "±HH:MM" when [showSign] is true (zero gets no sign).
 * Hours are not capped at 24. Returns null when [totalMinutes] is null.
 */
fun formatMinutes(totalMinutes: Int?, showSign: Boolean = false): String? = totalMinutes?.let {
    val sign = if (showSign) {
        when {
            it > 0 -> "+"
            it < 0 -> "-"
            else -> ""
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
 * Formats minutes since midnight as a "HH:MM" clock time, wrapping around after 24 hours.
 * Returns null when [minutesOfDay] is null.
 */
fun formatClock(minutesOfDay: Int?): String? = minutesOfDay?.let {
    val h = (it / MINUTES_PER_HOUR) % HOURS_PER_DAY
    val m = it % MINUTES_PER_HOUR
    "%02d:%02d".format(h, m)
}

/** Formats a duration in minutes in Norwegian: "N min" below one hour, otherwise "H t M min". */
fun norwegianDuration(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    return if (hours > 0) "$hours t ${minutes % MINUTES_PER_HOUR} min" else "$minutes min"
}
