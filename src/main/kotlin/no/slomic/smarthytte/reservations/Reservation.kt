package no.slomic.smarthytte.reservations

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.datesUntil
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.lastYearInterval
import no.slomic.smarthytte.common.toUtcDate
import no.slomic.smarthytte.common.utcDateNow

data class Reservation(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val guestIds: List<String>,
    val summary: String? = null,
    val description: String? = null,
    val sourceCreatedTime: Instant? = null,
    val sourceUpdatedTime: Instant? = null,
    val notionId: String? = null,
    var checkIn: CheckIn? = null,
    var checkOut: CheckOut? = null,
) {
    val hasStarted: Boolean
        get() = startDate <= utcDateNow()

    val hasEnded: Boolean
        get() = endDate <= utcDateNow()

    val startDate: LocalDate
        get() = startTime.toUtcDate()

    val endDate: LocalDate
        get() = endTime.toUtcDate()

    val stayDurationDays: Int
        get() = startDate.daysUntilSafe(endExclusive = endDate)

    /**
     * Calculates the number of days a reservation overlaps with a given period.
     *
     * @param periodStart the start date of the period (inclusive)
     * @param periodEndExclusive the end date of the period (exclusive)
     * @return the number of days the reservation overlaps with the specified period,
     * or 0 if there is no overlap
     */
    fun stayDurationDaysInPeriod(periodStart: LocalDate, periodEndExclusive: LocalDate): Int {
        val overlapStart = maxOf(startDate, periodStart)
        val overlapEndExclusive = minOf(endDate, periodEndExclusive)
        return if (overlapStart < overlapEndExclusive) overlapStart.daysUntilSafe(overlapEndExclusive) else 0
    }
}

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
    return this.asSequence().flatMap { r ->
        val start = maxOf(r.startDate, startInclusive)
        val endEx = minOf(r.endDate, endExclusive)
        if (start < endEx) {
            start.datesUntil(endEx).toList()
        } else {
            emptyList()
        }
    }.toSet().size
}

/**
 * Calculates the difference between the total visits in the current year and the total visits
 * in the last 12 months.
 *
 * @param currentYear the current year for which the difference in visits is being calculated
 * @param visitsCurrentYear the total number of visits recorded for the current year
 * @return the difference between the total visits in the current year and the total visits
 * in the last 12 months. Positive if more visits in the current year, negative if more in the last 12 months.
 */
fun List<Reservation>.diffVisitsCurrentYearWithLast12Months(currentYear: Int, visitsCurrentYear: Int): Int {
    val (start, end) = lastYearInterval(currentYear)
    val visitsLast12Months = countInInterval(start, end)
    return visitsCurrentYear - visitsLast12Months
}

/**
 * Counts the number of reservations whose start date falls within the given [start] and [end] dates (inclusive).
 */
fun List<Reservation>.countInInterval(start: LocalDate, end: LocalDate): Int = count { it.startDate in start..end }

/**
 * Finds the month with the longest stay duration among the reservations in the list.
 *
 * @return A pair containing the month with the longest stay and the duration of the stay in days,
 *         or `null` if the list is empty.
 */
fun List<Reservation>.findMonthWithLongestStay(): Pair<Month, Int>? = this.maxByOrNull { it.stayDurationDays }
    ?.let { reservation -> reservation.startDate.month to reservation.stayDurationDays }

/**
 * Aggregates the total visits (reservations) per guest from a list of reservations.
 *
 * Each guest ID from the reservations in the list is extracted, and
 * the total number of visits for each guest is computed.
 *
 * @return a map where the keys are guest IDs (as strings) and the values
 * are the counts of how many times each guest ID appears in the reservations.
 */
fun List<Reservation>.visitsByGuest(): Map<String, Int> = this.flatMap { it.guestIds }.groupingBy { it }.eachCount()

/**
 * Calculates the total number of stay days for each guest within a given period based on reservations.
 *
 * @param periodStart the start date of the period (inclusive) for which the stay days are calculated
 * @param periodEndExclusive the end date of the period (exclusive) for which the stay days are calculated
 * @return a map where the keys are guest IDs, and the values are the total number of stay days for each guest
 * within the specified period
 */
fun List<Reservation>.stayDaysByGuest(periodStart: LocalDate, periodEndExclusive: LocalDate): Map<String, Int> =
    this.flatMap { reservation ->
        val days = reservation.stayDurationDaysInPeriod(periodStart, periodEndExclusive)
        reservation.guestIds.map { it to days }
    }.groupingBy { it.first }.fold(0) { acc, element -> acc + element.second }
