package no.slomic.smarthytte.common

import kotlin.math.abs

const val MINUTES_PER_HOUR: Int = 60
const val HOURS_PER_DAY: Int = 24

// Formats a total duration in minutes as "HH:MM" or "±HH:MM". Hours are not capped at 24.
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

// Formats minutes-since-midnight as "HH:MM" clock time (modulo 24).
fun formatClock(minutesOfDay: Int?): String? = minutesOfDay?.let {
    val h = (it / MINUTES_PER_HOUR) % HOURS_PER_DAY
    val m = it % MINUTES_PER_HOUR
    "%02d:%02d".format(h, m)
}
