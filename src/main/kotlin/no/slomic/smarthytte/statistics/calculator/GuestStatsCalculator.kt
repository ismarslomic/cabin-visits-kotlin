package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.daysByGuest
import no.slomic.smarthytte.reservations.visitsByGuest
import no.slomic.smarthytte.statistics.model.GuestPeriodStats
import no.slomic.smarthytte.statistics.model.GuestVisitStats
import no.slomic.smarthytte.statistics.model.LiveGuestStats

fun calculateMonthlyGuestStats(
    year: Int,
    guestsById: Map<String, Guest>,
    dates: MonthDates,
    allReservations: List<Reservation>,
): List<GuestVisitStats> = aggregateGuestVisitStats(
    periodStart = dates.firstOfMonth,
    periodEndExclusive = dates.firstOfNextMonth,
    reservations = allReservations,
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
    allReservations: List<Reservation>,
    guestsById: Map<String, Guest>,
): YearGuestStats {
    val jan1 = firstDayOfYear(year)
    val jan1Next = firstDayOfYearAfter(year)

    val guestYearStats = aggregateGuestVisitStats(
        periodStart = jan1,
        periodEndExclusive = jan1Next,
        reservations = allReservations,
        guestsById = guestsById,
        ageYear = year,
    )

    val prevYearGuests: Set<String> = allReservations
        .filter { it.startDate.year == year - 1 }
        .flatMap { it.guestIds }
        .toSet()

    val newGuests = guestYearStats.filter { it.guestId !in prevYearGuests }.sortedWith(GuestVisitStats.COMPARATOR)
    val allGuestsSorted = guestYearStats.sortedWith(GuestVisitStats.COMPARATOR)
    val topGuestByDays = allGuestsSorted.maxByOrNull { it.totalDays }

    return YearGuestStats(topGuestByDays, newGuests, allGuestsSorted)
}

/**
 * Aggregates visits and days per guest for the period. Visits are counted for reservations that started in the period
 * (arrival date), while days are counted within the period for all overlapping reservations. Guests without any
 * visits or days in the period are excluded.
 */
fun aggregateGuestVisitStats(
    periodStart: LocalDate,
    periodEndExclusive: LocalDate,
    reservations: List<Reservation>,
    guestsById: Map<String, Guest>,
    ageYear: Int,
): List<GuestVisitStats> {
    val visitsByGuest = reservations
        .filter { it.startDate >= periodStart && it.startDate < periodEndExclusive }
        .visitsByGuest()
    val daysByGuest = reservations
        .daysByGuest(periodStart, periodEndExclusive)
        .filterValues { it > 0 }

    return (visitsByGuest.keys + daysByGuest.keys)
        .toSet()
        .mapNotNull { guestId ->
            val guest = guestsById[guestId] ?: return@mapNotNull null
            GuestVisitStats(
                guestId = guestId,
                firstName = guest.firstName,
                lastName = guest.lastName,
                age = (ageYear - guest.birthYear.toInt()).coerceAtLeast(0),
                totalVisits = visitsByGuest[guestId] ?: 0,
                totalDays = daysByGuest[guestId] ?: 0,
            )
        }
}

/**
 * Builds live statistics for each guest in [reservation]. Only reservations started on or before [today]
 * are counted, and days are counted up to and including [today].
 */
fun calculateLiveGuestStats(
    reservation: Reservation,
    today: LocalDate,
    allReservations: List<Reservation>,
    guestsById: Map<String, Guest>,
): List<LiveGuestStats> {
    val startedReservations = allReservations.filter { it.startDate <= today }
    val periodEndExclusive = today.plus(DatePeriod(days = 1))
    val firstYear = startedReservations.minOfOrNull { it.startDate.year } ?: today.year

    val currentYearStats = aggregateGuestVisitStats(
        periodStart = firstDayOfYear(today.year),
        periodEndExclusive = periodEndExclusive,
        reservations = startedReservations,
        guestsById = guestsById,
        ageYear = today.year,
    )
    val allTimeStats = aggregateGuestVisitStats(
        periodStart = firstDayOfYear(firstYear),
        periodEndExclusive = periodEndExclusive,
        reservations = startedReservations,
        guestsById = guestsById,
        ageYear = today.year,
    )
    val currentYearRanking = PeriodRanking(currentYearStats)
    val allTimeRanking = PeriodRanking(allTimeStats)

    return reservation.guestIds
        .mapNotNull { guestId ->
            val guest = guestsById[guestId] ?: return@mapNotNull null
            val previousVisits = allReservations.filter {
                guestId in it.guestIds && it.id != reservation.id && it.startDate < reservation.startDate
            }
            val startedVisits = startedReservations.filter { guestId in it.guestIds }
            LiveGuestStats(
                guestId = guestId,
                firstName = guest.firstName,
                lastName = guest.lastName,
                age = (today.year - guest.birthYear.toInt()).coerceAtLeast(0),
                isFamily = guest.isFamily,
                isFirstVisit = previousVisits.isEmpty(),
                firstVisitDate = startedVisits.minOfOrNull { it.startDate },
                lastVisitDate = previousVisits.filter { it.endDate <= today }.maxOfOrNull { it.endDate },
                yearsVisited = startedVisits.map { it.startDate.year }.distinct().sortedDescending(),
                currentYear = currentYearRanking.periodStatsFor(guestId),
                allTime = allTimeRanking.periodStatsFor(guestId),
            )
        }
        .sortedWith(
            compareBy<LiveGuestStats, Int?>(nullsLast()) { it.currentYear.daysRank }
                .thenBy(nullsLast()) { it.allTime.daysRank }
                .thenBy { it.lastName }
                .thenBy { it.firstName },
        )
}

private class PeriodRanking(private val stats: List<GuestVisitStats>) {
    private val statsById = stats.associateBy { it.guestId }
    private val visitsRanks = ranksBy { it.totalVisits }
    private val daysRanks = ranksBy { it.totalDays }

    fun periodStatsFor(guestId: String): GuestPeriodStats {
        val guestStats = statsById[guestId]
        return GuestPeriodStats(
            totalVisits = guestStats?.totalVisits ?: 0,
            totalDays = guestStats?.totalDays ?: 0,
            visitsRank = visitsRanks[guestId],
            daysRank = daysRanks[guestId],
        )
    }

    // Standard competition ranking: tied guests share rank and the next rank is skipped (1, 2, 2, 4).
    private fun ranksBy(selector: (GuestVisitStats) -> Int): Map<String, Int> {
        val values = stats.map(selector)
        return stats
            .filter { it.totalVisits > 0 }
            .associate { guestStats -> guestStats.guestId to 1 + values.count { it > selector(guestStats) } }
    }
}
