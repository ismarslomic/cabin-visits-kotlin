package no.slomic.smarthytte.reservations

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.datesUntil
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
