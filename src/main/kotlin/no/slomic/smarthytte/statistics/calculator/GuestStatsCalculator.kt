package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.nightsByGuest
import no.slomic.smarthytte.reservations.visitsByGuest
import no.slomic.smarthytte.statistics.model.GuestVisitStats

fun calculateMonthlyGuestStats(
    year: Int,
    guestsById: Map<String, Guest>,
    dates: MonthDates,
    monthlyReservations: List<Reservation>,
): List<GuestVisitStats> = aggregateGuestVisitStats(
    periodStart = dates.firstOfMonth,
    periodEndExclusive = dates.firstOfNextMonth,
    reservations = monthlyReservations,
    guestsById = guestsById,
    ageYear = year,
).sortedWith(GuestVisitStats.COMPARATOR)

data class YearGuestStats(
    val topGuestByDays: GuestVisitStats?,
    val newGuests: List<GuestVisitStats>,
    val allGuestsSorted: List<GuestVisitStats>,
)

fun computeYearGuestStats(
    year: Int,
    yearReservations: List<Reservation>,
    guestsById: Map<String, Guest>,
    byYear: Map<Int, List<Reservation>>,
): YearGuestStats {
    val jan1 = firstDayOfYear(year)
    val jan1Next = firstDayOfYearAfter(year)

    val guestYearStats = aggregateGuestVisitStats(
        periodStart = jan1,
        periodEndExclusive = jan1Next,
        reservations = yearReservations,
        guestsById = guestsById,
        ageYear = year,
    )

    val prevYearGuests: Set<String> = byYear[year - 1]
        ?.flatMap { it.guestIds }
        ?.toSet()
        ?: emptySet()

    val newGuests = guestYearStats.filter { it.guestId !in prevYearGuests }.sortedWith(GuestVisitStats.COMPARATOR)
    val allGuestsSorted = guestYearStats.sortedWith(GuestVisitStats.COMPARATOR)
    val topGuestByDays = allGuestsSorted.maxByOrNull { it.totalNights }

    return YearGuestStats(topGuestByDays, newGuests, allGuestsSorted)
}

fun aggregateGuestVisitStats(
    periodStart: LocalDate,
    periodEndExclusive: LocalDate,
    reservations: List<Reservation>,
    guestsById: Map<String, Guest>,
    ageYear: Int,
): List<GuestVisitStats> {
    if (reservations.isEmpty()) return emptyList()

    val visitsByGuest = reservations.visitsByGuest()
    val nightsByGuest = reservations.nightsByGuest(periodStart, periodEndExclusive)

    return (visitsByGuest.keys + nightsByGuest.keys)
        .toSet()
        .mapNotNull { guestId ->
            val guest = guestsById[guestId] ?: return@mapNotNull null
            GuestVisitStats(
                guestId = guestId,
                firstName = guest.firstName,
                lastName = guest.lastName,
                age = (ageYear - guest.birthYear.toInt()).coerceAtLeast(0),
                totalVisits = visitsByGuest[guestId] ?: 0,
                totalNights = nightsByGuest[guestId] ?: 0,
            )
        }
}
