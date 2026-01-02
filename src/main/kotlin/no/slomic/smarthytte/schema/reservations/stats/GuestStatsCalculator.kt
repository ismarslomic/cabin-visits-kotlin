package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.stayDaysByGuest
import no.slomic.smarthytte.reservations.visitsByGuest

/**
 * Aggregates and sorts guest statistics for a specific month.
 */
fun calculateMonthlyGuestStats(
    context: MonthStatsContext,
    dates: MonthDates,
    monthlyReservations: List<Reservation>,
): List<GuestVisitStats> = aggregateGuestVisitStats(
    periodStart = dates.firstOfMonth,
    periodEndExclusive = dates.firstOfNextMonth,
    reservations = monthlyReservations,
    guestsById = context.guestsById,
    ageYear = context.year,
).sortedWith(GuestVisitStats.COMPARATOR)

/**
 * Internal helper to aggregate raw reservation data into guest visit statistics.
 */
fun aggregateGuestVisitStats(
    periodStart: LocalDate,
    periodEndExclusive: LocalDate,
    reservations: List<Reservation>,
    guestsById: Map<String, Guest>,
    ageYear: Int,
): List<GuestVisitStats> {
    if (reservations.isEmpty()) return emptyList()

    val visitsByGuest = reservations.visitsByGuest()
    val stayDaysByGuest = reservations.stayDaysByGuest(periodStart, periodEndExclusive)

    return (visitsByGuest.keys + stayDaysByGuest.keys)
        .toSet()
        .mapNotNull { guestId ->
            val guest = guestsById[guestId] ?: return@mapNotNull null
            GuestVisitStats(
                guestId = guestId,
                firstName = guest.firstName,
                lastName = guest.lastName,
                age = (ageYear - guest.birthYear.toInt()).coerceAtLeast(0),
                totalVisits = visitsByGuest[guestId] ?: 0,
                totalStayDays = stayDaysByGuest[guestId] ?: 0,
            )
        }
}
