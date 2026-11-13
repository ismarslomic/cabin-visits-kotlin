package no.slomic.smarthytte.reservations

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.slomic.smarthytte.common.daysUntilSafe

/**
 * The own stay of one guest in one reservation, as UTC dates: [startDate] is the guest's first day and [endDate] the
 * last day, both inclusive. They are the guest's own dates ([ReservationGuest.fromDate], [ReservationGuest.toDate]) and
 * fall back to the dates of the reservation, see [Reservation.stays]. A stay is only valid with the reservation it
 * belongs to ([reservationId]).
 *
 * The guest statistics count with the stay. The numbers about the reservation itself (occupancy, group size, trips)
 * keep using the reservation's dates.
 */
data class GuestStay(val guestId: String, val reservationId: String, val startDate: LocalDate, val endDate: LocalDate) {
    /** Nights of the stay, like [Reservation.durationNights] but for the guest's own dates. */
    val nights: Int
        get() = startDate.daysUntilSafe(endExclusive = endDate)

    /** Calendar days of the stay, including arrival and departure day. */
    val days: Int
        get() = nights + 1

    /** Days of the stay inside `[periodStart, periodEndExclusive)`. */
    fun daysInPeriod(periodStart: LocalDate, periodEndExclusive: LocalDate): Int =
        daysWithin(startDate, endDate, periodStart, periodEndExclusive)

    /** Where the stay stands on [today], with the rule of [Reservation.visitStatus] applied to the guest's dates. */
    fun visitStatus(today: LocalDate): VisitStatus = visitStatusOf(startDate, endDate, today)
}

/** The days of `[startDate, endDate]` (both inclusive) that lie inside `[periodStart, periodEndExclusive)`. */
internal fun daysWithin(
    startDate: LocalDate,
    endDate: LocalDate,
    periodStart: LocalDate,
    periodEndExclusive: LocalDate,
): Int {
    val overlapStart = maxOf(startDate, periodStart)
    val overlapEndExclusive = minOf(endDate.plus(DatePeriod(days = 1)), periodEndExclusive)

    return if (overlapStart < overlapEndExclusive) overlapStart.daysUntilSafe(overlapEndExclusive) else 0
}

internal fun visitStatusOf(startDate: LocalDate, endDate: LocalDate, today: LocalDate): VisitStatus = when {
    startDate > today -> VisitStatus.UPCOMING
    endDate > today -> VisitStatus.ONGOING
    else -> VisitStatus.COMPLETED
}

/** Visits per guest: one for every stay in the list. */
fun List<GuestStay>.visitsByGuest(): Map<String, Int> = groupingBy { it.guestId }.eachCount()

/** Nights per guest, of the whole stay of each stay in the list. */
fun List<GuestStay>.nightsByGuest(): Map<String, Int> =
    groupingBy { it.guestId }.fold(0) { nights, stay -> nights + stay.nights }

/** Days per guest inside `[periodStart, periodEndExclusive)`. */
fun List<GuestStay>.daysByGuest(periodStart: LocalDate, periodEndExclusive: LocalDate): Map<String, Int> =
    groupingBy { it.guestId }.fold(0) { days, stay -> days + stay.daysInPeriod(periodStart, periodEndExclusive) }
