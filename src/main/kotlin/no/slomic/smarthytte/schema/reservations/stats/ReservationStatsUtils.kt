package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import no.slomic.smarthytte.common.datesUntil
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.formatMinutes
import kotlin.math.abs

internal object ReservationStatsUtils {
    const val DAYS_IN_MONTHLY_COMPARE_WINDOW: Int = 30
    const val DAY_OFFSET_PREVIOUS: Int = 1
    const val PERCENT_FACTOR: Double = 100.0
    const val MINUTES_PER_HOUR: Int = 60
    const val HOURS_PER_DAY: Int = 24

    /**
     * Counts the number of reservations for each month.
     *
     * The method groups all reservations in the list by their starting month and
     * calculates a count for each month. It returns a map where the keys are
     * `Month` values (representing each month of the year) and the values are
     * integers representing the count of reservations starting in that month.
     *
     * @return a map of months to the count of reservations starting in each month
     */
    fun List<Reservation>.countByMonth(): Map<Month, Int> =
        Month.entries.associateWith { month -> count { it.startDate.month == month } }

    /**
     * Counts the total number of occupied days within the specified date window for a list of reservations.
     *
     * @param startInclusive the inclusive start date of the window
     * @param endExclusive the exclusive end date of the window
     * @return the count of unique occupied days within the specified date window
     */
    fun List<Reservation>.countOccupiedDaysInWindow(startInclusive: LocalDate, endExclusive: LocalDate): Int {
        if (startInclusive >= endExclusive) return 0
        return this
            .asSequence()
            .flatMap { r ->
                val start = maxOf(r.startDate, startInclusive)
                val endEx = minOf(r.endDate, endExclusive)
                if (start < endEx) {
                    start.datesUntil(endEx).toList()
                } else {
                    emptyList()
                }
            }
            .toSet()
            .size
    }

    /**
     * Converts a given total number of minutes (representing a duration) into a formatted time string
     * in the format "HH:MM".
     *
     * This function calculates hours by dividing total minutes by 60 without any upper limit,
     * making it suitable for representing total durations that may exceed 24 hours.
     * Example: 1500 minutes will be formatted as "25:00".
     *
     * @param totalMinutes The total number of minutes to format. If null, the function will return null.
     * @return A formatted string representing the duration in "HH:MM", or null if the input is null.
     */
    fun formatMinutes(totalMinutes: Int?): String? = totalMinutes?.let {
        val abs = abs(it)
        val h = abs / MINUTES_PER_HOUR
        val m = abs % MINUTES_PER_HOUR
        "%02d:%02d".format(h, m)
    }

    /**
     * Formats a difference in time (duration), given in minutes, into a signed string in the format ±HH:MM.
     *
     * Similar to [formatMinutes], this does not limit the number of hours to 24.
     *
     * @param diffMinutes The time difference in minutes to format. Can be null.
     * @return A formatted string representation of the time difference, prefixed with a '+' or '-'
     * if the value is positive or negative respectively, or null if the input is null.
     */
    fun formatSignedMinutes(diffMinutes: Int?): String? = diffMinutes?.let {
        val sign = if (it > 0) {
            "+"
        } else if (it < 0) {
            "-"
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

    /**
     * Computes the average of the integers in the list and rounds the result to one decimal place.
     * If the list is empty, returns null.
     *
     * @return the average value of the list rounded to one decimal place as a [Double], or null if the list is empty
     */
    fun List<Int>.averageRounded1OrNull(): Double? = if (isEmpty()) null else (sum().toDouble() / size).round1()

    /**
     * Calculates the average of the integers in the list and returns it as an integer.
     * If the list is empty, returns null.
     *
     * @return The average value of the integers in the list as an integer or null if the list is empty.
     */
    fun List<Int>.averageOrNullInt(): Int? = if (isEmpty()) null else (sum().toDouble() / size.toDouble()).toInt()

    /**
     * Creates a comparator for comparing instances of `GuestVisitStats`.
     *
     * The comparison is performed in the following order of priority:
     * - Descending order by the total number of stay days (`totalStayDays`).
     * - Descending order by the total number of visits (`totalVisits`).
     * - Ascending order by the guest's last name (`lastName`).
     * - Ascending order by the guest's first name (`firstName`).
     *
     * @return A comparator that applies the specified ordering rules for `GuestVisitStats` objects.
     */
    fun guestStatsComparator(): Comparator<GuestVisitStats> = compareByDescending<GuestVisitStats> { it.totalStayDays }
        .thenByDescending { it.totalVisits }
        .thenBy { it.lastName }
        .thenBy { it.firstName }
}
