package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.daysByGuest
import no.slomic.smarthytte.reservations.nightsByGuest
import no.slomic.smarthytte.reservations.visitsByGuest
import no.slomic.smarthytte.statistics.model.GuestPeriodStats
import no.slomic.smarthytte.statistics.model.GuestProfileStats
import no.slomic.smarthytte.statistics.model.GuestStats
import no.slomic.smarthytte.statistics.model.GuestVisit
import no.slomic.smarthytte.statistics.model.GuestVisitStats
import no.slomic.smarthytte.statistics.model.VisitStatus

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
 * are counted, and days are counted up to and including [today]. Guests without an entry in
 * [avatarUrlsByGuestId] have no avatar.
 */
fun calculateGuestStats(
    reservation: Reservation,
    today: LocalDate,
    allReservations: List<Reservation>,
    guestsById: Map<String, Guest>,
    avatarUrlsByGuestId: Map<String, String> = emptyMap(),
): List<GuestStats> {
    val startedReservations = allReservations.filter { it.startDate <= today }
    val rankings = GuestRankings.of(today, startedReservations, guestsById)

    return reservation.guestIds
        .mapNotNull { guestId ->
            val guest = guestsById[guestId] ?: return@mapNotNull null
            val previousVisits = allReservations.filter {
                guestId in it.guestIds && it.id != reservation.id && it.startDate < reservation.startDate
            }
            guestStatsOf(guestId, guest, previousVisits, today, startedReservations, avatarUrlsByGuestId, rankings)
        }
        .sortedWith(
            compareBy<GuestStats, Int?>(nullsLast()) { it.currentYear.daysRank }
                .thenBy(nullsLast()) { it.allTime.daysRank }
                .thenBy { it.lastName }
                .thenBy { it.firstName },
        )
}

/**
 * Builds the profile of the guest with [guestId] as of [today]: statistics for the guest, all visits of the guest with
 * their status, and fun facts. Returns null if the guest is unknown.
 *
 * A visit is ongoing from its start date until its end date (exclusive), completed when its end date has been reached
 * and upcoming before its start date. If the guest is at the cabin, [GuestStats.lastVisitDate] is the last visit
 * before the ongoing one and [GuestStats.isFirstVisit] tells if the ongoing visit is the first one. Otherwise
 * [GuestStats.isFirstVisit] is false.
 */
fun calculateGuestProfileStats(
    guestId: String,
    today: LocalDate,
    allReservations: List<Reservation>,
    guestsById: Map<String, Guest>,
    avatarUrl: String? = null,
): GuestProfileStats? {
    val guest = guestsById[guestId] ?: return null
    val startedReservations = allReservations.filter { it.startDate <= today }
    val visits = allReservations.filter { guestId in it.guestIds }.sortedBy { it.startDate }
    val ongoing = visits.firstOrNull { it.visitStatus(today) == VisitStatus.ONGOING }
    val previousVisits = visits.filter {
        it.startDate <= today && (ongoing == null || it.startDate < ongoing.startDate)
    }

    val guestStats = guestStatsOf(
        guestId = guestId,
        guest = guest,
        previousVisits = previousVisits,
        today = today,
        startedReservations = startedReservations,
        avatarUrlsByGuestId = avatarUrl?.let { mapOf(guestId to it) }.orEmpty(),
        rankings = GuestRankings.of(today, startedReservations, guestsById),
    ).copy(isFirstVisit = ongoing != null && previousVisits.isEmpty())

    val guestVisits = visits.map {
        GuestVisit(it.startDate, it.endDate, it.durationDays, it.durationNights, it.visitStatus(today))
    }

    return GuestProfileStats(
        guest = guestStats,
        visits = guestVisits,
        funFacts = calculateGuestProfileFunFacts(guestStats, guestVisits, GuestProfileFunFactContext(today)),
    )
}

private fun Reservation.visitStatus(today: LocalDate): VisitStatus = when {
    startDate > today -> VisitStatus.UPCOMING
    endDate > today -> VisitStatus.ONGOING
    else -> VisitStatus.COMPLETED
}

/**
 * Statistics for one guest. [previousVisits] are the guest's visits before the reference visit, which decide
 * [GuestStats.isFirstVisit] and [GuestStats.lastVisitDate].
 */
private fun guestStatsOf(
    guestId: String,
    guest: Guest,
    previousVisits: List<Reservation>,
    today: LocalDate,
    startedReservations: List<Reservation>,
    avatarUrlsByGuestId: Map<String, String>,
    rankings: GuestRankings,
): GuestStats {
    val startedVisits = startedReservations.filter { guestId in it.guestIds }
    return GuestStats(
        guestId = guestId,
        firstName = guest.firstName,
        lastName = guest.lastName,
        age = (today.year - guest.birthYear.toInt()).coerceAtLeast(0),
        isFamily = guest.isFamily,
        avatarUrl = avatarUrlsByGuestId[guestId],
        isFirstVisit = previousVisits.isEmpty(),
        firstVisitDate = startedVisits.minOfOrNull { it.startDate },
        lastVisitDate = previousVisits.filter { it.endDate <= today }.maxOfOrNull { it.endDate },
        yearsVisited = startedVisits.map { it.startDate.year }.distinct().sortedDescending(),
        currentYear = rankings.currentYear.periodStatsFor(guestId),
        allTime = rankings.allTime.periodStatsFor(guestId),
    )
}

/** Rankings of all guests this year and all time, counting only started reservations up to and including today. */
private class GuestRankings(val currentYear: PeriodRanking, val allTime: PeriodRanking) {
    companion object {
        fun of(
            today: LocalDate,
            startedReservations: List<Reservation>,
            guestsById: Map<String, Guest>,
        ): GuestRankings {
            val periodEndExclusive = today.plus(DatePeriod(days = 1))
            val firstYear = startedReservations.minOfOrNull { it.startDate.year } ?: today.year

            fun rankingFrom(periodStart: LocalDate) = PeriodRanking(
                stats = aggregateGuestVisitStats(
                    periodStart = periodStart,
                    periodEndExclusive = periodEndExclusive,
                    reservations = startedReservations,
                    guestsById = guestsById,
                    ageYear = today.year,
                ),
                nightsByGuest = startedReservations
                    .filter { it.startDate >= periodStart && it.startDate < periodEndExclusive }
                    .nightsByGuest(),
                familyGuestIds = guestsById.values.filter { it.isFamily }.map { it.id }.toSet(),
            )

            return GuestRankings(
                currentYear = rankingFrom(firstDayOfYear(today.year)),
                allTime = rankingFrom(firstDayOfYear(firstYear)),
            )
        }
    }
}

/** Visits per guest this year and all time (reservations started up to today), used to compare guests. */
data class GuestStanding(
    val guestId: String,
    val firstName: String,
    val isFamily: Boolean,
    val yearVisits: Int,
    val allTimeVisits: Int,
)

fun calculateGuestStandings(
    today: LocalDate,
    allReservations: List<Reservation>,
    guestsById: Map<String, Guest>,
): List<GuestStanding> {
    val startedReservations = allReservations.filter { it.startDate <= today }
    val periodEndExclusive = today.plus(DatePeriod(days = 1))
    val firstYear = startedReservations.minOfOrNull { it.startDate.year } ?: today.year

    fun visitsFrom(periodStart: LocalDate) = aggregateGuestVisitStats(
        periodStart = periodStart,
        periodEndExclusive = periodEndExclusive,
        reservations = startedReservations,
        guestsById = guestsById,
        ageYear = today.year,
    ).associate { it.guestId to it.totalVisits }

    val yearVisits = visitsFrom(firstDayOfYear(today.year))
    return visitsFrom(firstDayOfYear(firstYear)).map { (guestId, allTimeVisits) ->
        val guest = guestsById.getValue(guestId)
        GuestStanding(guestId, guest.firstName, guest.isFamily, yearVisits[guestId] ?: 0, allTimeVisits)
    }
}

private class PeriodRanking(
    stats: List<GuestVisitStats>,
    private val nightsByGuest: Map<String, Int>,
    familyGuestIds: Set<String>,
) {
    private val statsById = stats.associateBy { it.guestId }
    private val guestsOnlyStats = stats.filter { it.guestId !in familyGuestIds }
    private val visitsRanks = ranksBy(stats) { it.totalVisits }
    private val daysRanks = ranksBy(stats) { it.totalDays }
    private val visitsRanksAmongGuests = ranksBy(guestsOnlyStats) { it.totalVisits }
    private val daysRanksAmongGuests = ranksBy(guestsOnlyStats) { it.totalDays }

    fun periodStatsFor(guestId: String): GuestPeriodStats {
        val guestStats = statsById[guestId]
        return GuestPeriodStats(
            totalVisits = guestStats?.totalVisits ?: 0,
            totalDays = guestStats?.totalDays ?: 0,
            totalNights = nightsByGuest[guestId] ?: 0,
            visitsRank = visitsRanks[guestId],
            daysRank = daysRanks[guestId],
            visitsRankAmongGuests = visitsRanksAmongGuests[guestId],
            daysRankAmongGuests = daysRanksAmongGuests[guestId],
        )
    }

    // Standard competition ranking: tied guests share rank and the next rank is skipped (1, 2, 2, 4).
    private fun ranksBy(ranked: List<GuestVisitStats>, selector: (GuestVisitStats) -> Int): Map<String, Int> {
        val values = ranked.map(selector)
        return ranked
            .filter { it.totalVisits > 0 }
            .associate { guestStats -> guestStats.guestId to 1 + values.count { it > selector(guestStats) } }
    }
}
