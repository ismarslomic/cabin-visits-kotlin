package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import no.slomic.smarthytte.common.datesUntil
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.reservations.Reservation
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

    fun computeOccupiedDaysInWindow(
        reservations: List<Reservation>,
        startInclusive: LocalDate,
        endExclusive: LocalDate,
    ): Int {
        if (startInclusive >= endExclusive) return 0
        return reservations
            .asSequence()
            .map { r ->
                val start = maxOf(r.startDate, startInclusive)
                val endEx = minOf(r.endDate, endExclusive)
                if (start < endEx) {
                    start.datesUntil(endEx).toList()
                } else {
                    emptyList()
                }
            }
            .flatten()
            .toSet()
            .size
    }

    fun formatMinutes(totalMinutes: Int?): String? = totalMinutes?.let {
        val abs = abs(it)
        val h = abs / MINUTES_PER_HOUR
        val m = abs % MINUTES_PER_HOUR
        "%02d:%02d".format(h, m)
    }

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

    fun formatClock(minutesOfDay: Int?): String? = minutesOfDay?.let {
        val h = (it / MINUTES_PER_HOUR) % HOURS_PER_DAY
        val m = it % MINUTES_PER_HOUR
        "%02d:%02d".format(h, m)
    }

    fun List<Int>.averageRounded1OrNull(): Double? = if (isEmpty()) null else (sum().toDouble() / size).round1()

    /**
     * Computes average integer value or null if empty
     */
    fun List<Int>.averageOrNullInt(): Int? = if (isEmpty()) null else (sum().toDouble() / size.toDouble()).toInt()

    fun guestStatsComparator(): Comparator<GuestVisitStats> = compareByDescending<GuestVisitStats> { it.totalStayDays }
        .thenByDescending { it.totalVisits }
        .thenBy { it.lastName }
        .thenBy { it.firstName }
}
