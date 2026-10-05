@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.yearsUntil
import no.slomic.smarthytte.common.norwegianMonthNameOf
import no.slomic.smarthytte.common.norwegianShortMonthYear
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.LiveGuestStats
import no.slomic.smarthytte.statistics.model.NextReservationInfo
import no.slomic.smarthytte.statistics.model.YearStats
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

private const val PRIORITY_FIRST_VISIT = 100
private const val PRIORITY_VISIT_MILESTONE = 95
private const val PRIORITY_DAYS_MILESTONE = 85
private const val PRIORITY_DRIVING_RECORD = 85
private const val PRIORITY_CABIN_VISIT_MILESTONE = 85
private const val PRIORITY_LONG_ABSENCE = 80
private const val PRIORITY_ARRIVAL_SOON = 70
private const val PRIORITY_VISIT_STREAK = 70
private const val PRIORITY_YEAR_TIED_LEAD = 70
private const val PRIORITY_TOP_GUEST_ALL_TIME = 70
private const val PRIORITY_YEAR_LEADER = 65
private const val PRIORITY_YEAR_GAP_TO_LEADER = 60
private const val PRIORITY_DRIVING_TIME_VS_LAST_YEAR = 60
private const val PRIORITY_LAST_DAY = 60
private const val PRIORITY_FIRST_VISIT_THIS_YEAR = 55
private const val PRIORITY_NEW_IN_GROUP = 55
private const val PRIORITY_SHARE_OF_TRIPS_GUEST = 50
private const val PRIORITY_SHARE_OF_DAYS = 45
private const val PRIORITY_SHARE_OF_LIFE = 45
private const val PRIORITY_NEXT_FIRST_TIMER = 45
private const val PRIORITY_COUNTDOWN_TO_NEXT_VISIT = 45
private const val PRIORITY_NEW_GUESTS_THIS_YEAR = 40
private const val PRIORITY_DAYS_THIS_YEAR = 40
private const val PRIORITY_MOST_EXPERIENCED = 40
private const val PRIORITY_YEARS_SINCE_FIRST_VISIT = 40
private const val PRIORITY_NEXT_VISIT_MILESTONE = 40
private const val PRIORITY_SHARE_OF_TRIPS_FAMILY = 30
private const val PRIORITY_NIGHTS_LEFT = 30
private const val PRIORITY_OCCUPANCY = 35
private const val PRIORITY_MOST_VISITED_MONTH = 35
private const val PRIORITY_MONTH_VS_LAST_YEAR = 35
private const val PRIORITY_LONGEST_STAY = 30
private const val PRIORITY_AVG_GROUP_SIZE = 30
private const val PRIORITY_TOTAL_DISTANCE = 30
private const val PRIORITY_STAY_PROGRESS = 30
private const val PRIORITY_TOTAL_DAYS = 30
private const val PRIORITY_LAST_VISIT_DATE = 30
private const val PRIORITY_CABIN_TOTALS = 25
private const val PRIORITY_EV_CONSUMPTION = 20
private const val PRIORITY_AVG_DRIVING_TIME = 20
private const val PRIORITY_YEARS_OF_OWNERSHIP = 15
private const val FAMILY_PRIORITY_PENALTY = 30

private val GUEST_VISIT_MILESTONES = setOf(5, 10, 25, 50, 75, 100, 150, 200)
private const val FAMILY_VISIT_MILESTONE_STEP = 50
private const val CABIN_VISIT_MILESTONE_STEP = 50
private const val MIN_MONTHS_FOR_LONG_ABSENCE = 12
private const val MIN_YEARS_FOR_STREAK = 3
private const val MIN_SHARE_OF_TRIPS_PERCENT = 25
private const val MIN_NEW_GUESTS_IN_GROUP = 2
private const val LAST_DAY_REMAINING_NIGHTS = 1
private const val ARRIVAL_SOON_MAX_DAYS = 2
private const val PERCENT_FACTOR = 100
private const val MAX_FACTS_PER_GUEST = 2
private const val MIN_SHARE_PERCENT = 5
private const val MIN_DAYS_THIS_YEAR = 3
private const val MIN_TOTAL_DAYS = 5
private const val MIN_RACE_VISITS = 2
private const val MAX_GAP_TO_LEADER = 3
private const val MAX_TIED_LEADERS = 3
private const val MIN_TRIPS_FOR_DRIVING_RECORD = 5
private const val DAYS_PER_YEAR = 365
private const val MINUTES_PER_HOUR = 60
private const val MIN_DRIVING_TIME_DIFF_MINUTES = 5
private const val MIN_MONTH_VISITS_FOR_MOST_VISITED = 2
private const val EARTH_CIRCUMFERENCE_KM = 40_075.0
private const val MIN_LAPS_AROUND_EARTH = 0.1
private const val THOUSANDS_GROUP_SIZE = 3
private val OWNERSHIP_START = LocalDate(2019, 12, 1)
private val DAYS_MILESTONES = listOf(50, 100, 200, 365)

/** All-time totals for the cabin; reservations that have not started are excluded. */
data class CabinTotals(val visits: Int, val nights: Int, val uniqueGuests: Int)

/** Everything the cabin fun facts depend on. The year stats are optional so a fact is skipped without its source. */
data class CabinFunFactContext(
    val today: LocalDate,
    val dataStartDate: LocalDate,
    val isOccupied: Boolean,
    val totals: CabinTotals,
    val currentYear: YearStats? = null,
    val previousYear: YearStats? = null,
)

/** Where the current stay stands: arrival date and the nights left until check-out. */
data class StayInfo(val startDate: LocalDate, val remainingNights: Int)

/** Driving time to the cabin for the current stay, and for all earlier started stays (minutes). */
data class ToCabinDriving(val currentMinutes: Int?, val previousMinutes: List<Int>)

/** Everything besides the guests themselves that the guest fun facts depend on. */
data class GuestFunFactContext(
    val today: LocalDate,
    val stay: StayInfo,
    val allTimeVisits: Int,
    val dataStartDate: LocalDate,
    val standings: List<GuestStanding> = emptyList(),
    val toCabinDriving: ToCabinDriving? = null,
)

/**
 * Fun facts about the guests of the current reservation, sorted by priority (highest first).
 *
 * Facts about family members are down-prioritised when the reservation includes other guests; when only family
 * is present they keep their full priority. Visits are only registered from the configured data start date,
 * so the wording says "registrert" instead of claiming anything about earlier history.
 */
fun calculateGuestFunFacts(
    guests: List<LiveGuestStats>,
    context: GuestFunFactContext,
    random: Random = Random.Default,
): List<FunFact> {
    val hasNonFamilyGuests = guests.any { !it.isFamily }

    val guestFacts = guests.flatMap { guest ->
        val facts = guestFunFacts(guest, context, random)
            .sortedByDescending { it.priority }
            .take(MAX_FACTS_PER_GUEST)
        if (guest.isFamily && hasNonFamilyGuests) {
            facts.map { it.copy(priority = it.priority - FAMILY_PRIORITY_PENALTY) }
        } else {
            facts
        }
    }

    return (guestFacts + groupFunFacts(guests, context))
        .sortedWith(compareByDescending<FunFact> { it.priority }.thenBy { it.guestId.orEmpty() })
}

/** Fun facts about the cabin as a whole, sorted by priority (highest first). */
fun calculateCabinFunFacts(context: CabinFunFactContext): List<FunFact> {
    val year = context.currentYear
    return (
        listOfNotNull(
            cabinVisitMilestone(context),
            cabinTotals(context),
            newGuestsThisYear(year),
            occupancyThisYear(year),
            mostVisitedMonth(year),
            monthVsSameMonthLastYear(context),
            longestStay(year),
            avgGroupSize(year),
            totalDistance(year),
            evConsumption(year),
            avgDrivingTime(year),
            yearsOfOwnership(context.today),
        ) + drivingTimeVsLastYear(context)
        ).sortedByDescending { it.priority }
}

/**
 * Fun facts about the next reservation, sorted by priority (highest first). [isOccupied] tells whether a stay is
 * in progress, in which case a countdown to the next visit is relevant.
 */
fun calculateNextVisitFunFacts(next: NextReservationInfo, isOccupied: Boolean): List<FunFact> = (
    listOfNotNull(arrivalSoon(next.daysUntil), countdownToNextVisit(next.daysUntil, isOccupied)) +
        next.guests.flatMap { listOfNotNull(nextFirstTimer(it), nextVisitMilestone(it)) }
    ).sortedByDescending { it.priority }

private fun guestFunFacts(guest: LiveGuestStats, context: GuestFunFactContext, random: Random): List<FunFact> =
    listOfNotNull(
        firstVisit(guest, random),
        visitMilestone(guest),
        daysMilestone(guest, context),
        longAbsence(guest, context.today),
        visitStreak(guest, context.today),
        yearRace(guest, context.standings),
        topGuestAllTime(guest, context),
        firstVisitThisYear(guest),
        shareOfTrips(guest, context.allTimeVisits),
        shareOfDays(guest, context),
        shareOfLife(guest, context),
        daysThisYear(guest, context.today),
        yearsSinceFirstVisit(guest, context.today),
        totalDays(guest, context.dataStartDate),
        lastVisitDate(guest),
    )

private fun groupFunFacts(guests: List<LiveGuestStats>, context: GuestFunFactContext): List<FunFact> = listOfNotNull(
    drivingRecord(context.toCabinDriving),
    lastDay(context.stay.remainingNights),
    newInGroup(guests),
    mostExperienced(guests),
    nightsLeft(context.stay.remainingNights),
    stayProgress(context),
)

private fun firstVisit(guest: LiveGuestStats, random: Random): FunFact? {
    if (guest.isFamily || !guest.isFirstVisit) return null
    val text = listOf(
        "Første registrerte besøk for ${guest.firstName}! 🎉",
        "${guest.firstName} er på hytta for første gang! 🎉",
    ).random(random)
    return FunFact(guest.guestId, text, PRIORITY_FIRST_VISIT)
}

private fun visitMilestone(guest: LiveGuestStats): FunFact? {
    val visits = guest.allTime.totalVisits
    return FunFact(guest.guestId, "Dette er besøk nr. $visits for ${guest.firstName}!", PRIORITY_VISIT_MILESTONE)
        .takeIf { isVisitMilestone(guest, visits) }
}

private fun isVisitMilestone(guest: LiveGuestStats, visits: Int): Boolean = if (guest.isFamily) {
    visits > 0 && visits % FAMILY_VISIT_MILESTONE_STEP == 0
} else {
    visits in GUEST_VISIT_MILESTONES
}

private fun longAbsence(guest: LiveGuestStats, today: LocalDate): FunFact? {
    val lastVisit = guest.lastVisitDate ?: return null
    val months = lastVisit.monthsUntil(today)
    return FunFact(guest.guestId, "Det er $months måneder siden sist, ${guest.firstName}!", PRIORITY_LONG_ABSENCE)
        .takeIf { !guest.isFamily && months >= MIN_MONTHS_FOR_LONG_ABSENCE }
}

private fun visitStreak(guest: LiveGuestStats, today: LocalDate): FunFact? {
    val years = consecutiveYearsUpTo(today.year, guest.yearsVisited)
    return FunFact(guest.guestId, "${guest.firstName} har besøkt hytta $years år på rad", PRIORITY_VISIT_STREAK)
        .takeIf { !guest.isFamily && years >= MIN_YEARS_FOR_STREAK }
}

/** Number of consecutive years, counting backwards from [year], in which the guest has visited. */
private fun consecutiveYearsUpTo(year: Int, yearsVisited: List<Int>): Int =
    generateSequence(year) { it - 1 }.takeWhile { it in yearsVisited }.count()

private fun firstVisitThisYear(guest: LiveGuestStats): FunFact? =
    FunFact(guest.guestId, "Årets første besøk for ${guest.firstName}", PRIORITY_FIRST_VISIT_THIS_YEAR)
        .takeIf { !guest.isFirstVisit && guest.currentYear.totalVisits == 1 }

private fun shareOfTrips(guest: LiveGuestStats, allTimeVisits: Int): FunFact? {
    if (allTimeVisits <= 0) return null
    val percent = guest.allTime.totalVisits * PERCENT_FACTOR / allTimeVisits
    val priority = if (guest.isFamily) PRIORITY_SHARE_OF_TRIPS_FAMILY else PRIORITY_SHARE_OF_TRIPS_GUEST
    return FunFact(guest.guestId, "${guest.firstName} har vært med på $percent % av alle hytteturer", priority)
        .takeIf { percent >= MIN_SHARE_OF_TRIPS_PERCENT }
}

private fun yearsSinceFirstVisit(guest: LiveGuestStats, today: LocalDate): FunFact? {
    val firstVisit = guest.firstVisitDate ?: return null
    val years = firstVisit.yearsUntil(today)
    return FunFact(
        guest.guestId,
        "${guest.firstName} hadde sitt første registrerte besøk for $years år siden",
        PRIORITY_YEARS_SINCE_FIRST_VISIT,
    ).takeIf { !guest.isFamily && years >= 1 }
}

private fun lastDay(remainingNights: Int): FunFact? =
    FunFact(null, "Siste dag på hytta, ha en trygg hjemreise!", PRIORITY_LAST_DAY)
        .takeIf { remainingNights <= LAST_DAY_REMAINING_NIGHTS }

private fun newInGroup(guests: List<LiveGuestStats>): FunFact? {
    val newGuests = guests.count { !it.isFamily && it.isFirstVisit }
    return FunFact(null, "$newGuests av ${guests.size} er her for første gang", PRIORITY_NEW_IN_GROUP)
        .takeIf { newGuests >= MIN_NEW_GUESTS_IN_GROUP }
}

private fun nightsLeft(remainingNights: Int): FunFact? =
    FunFact(null, "$remainingNights netter igjen av oppholdet", PRIORITY_NIGHTS_LEFT)
        .takeIf { remainingNights > LAST_DAY_REMAINING_NIGHTS }

private fun daysMilestone(guest: LiveGuestStats, context: GuestFunFactContext): FunFact? {
    val total = guest.allTime.totalDays
    val daysThisStay = (context.stay.startDate.daysUntil(context.today) + 1).coerceAtLeast(0)
    val daysBeforeStay = total - daysThisStay
    val milestone = DAYS_MILESTONES.lastOrNull { daysBeforeStay < it && it <= total } ?: return null
    return FunFact(guest.guestId, "${guest.firstName} passerer $milestone dager på hytta! 🎉", PRIORITY_DAYS_MILESTONE)
}

/** Leader, tied for the lead or gap to the leader this year, within the family or among the other guests. */
private fun yearRace(guest: LiveGuestStats, standings: List<GuestStanding>): FunFact? {
    val peers = standings.filter { it.isFamily == guest.isFamily }
    val leaderVisits = peers.maxOfOrNull { it.yearVisits } ?: 0
    return peers.firstOrNull { it.guestId == guest.guestId }
        ?.takeIf { leaderVisits >= MIN_RACE_VISITS }
        ?.let { raceFact(guest, it, peers, leaderVisits) }
}

private fun raceFact(
    guest: LiveGuestStats,
    me: GuestStanding,
    peers: List<GuestStanding>,
    leaderVisits: Int,
): FunFact? {
    val leaders = peers.filter { it.yearVisits == leaderVisits }.sortedBy { it.guestId }
    val scope = if (guest.isFamily) "i familien" else "blant gjestene"
    val gap = leaderVisits - me.yearVisits
    return when {
        gap > 0 -> FunFact(
            guest.guestId,
            "${guest.firstName} ligger $gap besøk bak ${leaders.first().firstName} $scope i år",
            PRIORITY_YEAR_GAP_TO_LEADER,
        ).takeIf { gap <= MAX_GAP_TO_LEADER }

        leaders.size == 1 -> FunFact(
            guest.guestId,
            "${guest.firstName} leder $scope i år med $leaderVisits besøk",
            PRIORITY_YEAR_LEADER,
        )

        else -> FunFact(
            guest.guestId,
            "${guest.firstName} deler ledelsen $scope i år med " +
                leaders.filter { it.guestId != guest.guestId }.joinToString(" og ") { it.firstName } +
                " ($leaderVisits besøk)",
            PRIORITY_YEAR_TIED_LEAD,
        ).takeIf { leaders.size <= MAX_TIED_LEADERS }
    }
}

/** The guest (family excluded) with the most visits since the data start, if there is a single one. */
private fun topGuestAllTime(guest: LiveGuestStats, context: GuestFunFactContext): FunFact? {
    val guests = context.standings.filter { !it.isFamily }
    val maxVisits = guests.maxOfOrNull { it.allTimeVisits } ?: 0
    val leader = guests.singleOrNull { it.allTimeVisits == maxVisits }
    return FunFact(
        guest.guestId,
        "${guest.firstName} er gjesten med flest besøk siden ${context.dataStartDate.norwegianShortMonthYear()} " +
            "($maxVisits)",
        PRIORITY_TOP_GUEST_ALL_TIME,
    ).takeIf { !guest.isFamily && leader?.guestId == guest.guestId }
}

private fun shareOfDays(guest: LiveGuestStats, context: GuestFunFactContext): FunFact? {
    val daysSinceStart = context.dataStartDate.daysUntil(context.today) + 1
    if (daysSinceStart <= 0) return null
    val percent = (guest.allTime.totalDays * PERCENT_FACTOR / daysSinceStart).coerceAtMost(PERCENT_FACTOR)
    return FunFact(
        guest.guestId,
        "${guest.firstName} har vært på hytta $percent % av dagene siden " +
            context.dataStartDate.norwegianShortMonthYear(),
        PRIORITY_SHARE_OF_DAYS,
    ).takeIf { percent >= MIN_SHARE_PERCENT }
}

/** Only for guests born in or after the first data year, so the whole life is covered by the data. */
private fun shareOfLife(guest: LiveGuestStats, context: GuestFunFactContext): FunFact? {
    if (guest.age !in 1..(context.today.year - context.dataStartDate.year)) return null
    val percent = (guest.allTime.totalDays * PERCENT_FACTOR / (guest.age * DAYS_PER_YEAR))
        .coerceAtMost(PERCENT_FACTOR)
    return FunFact(
        guest.guestId,
        "${guest.firstName} har vært på hytta ca. $percent % av livet sitt",
        PRIORITY_SHARE_OF_LIFE,
    ).takeIf { percent >= MIN_SHARE_PERCENT }
}

private fun daysThisYear(guest: LiveGuestStats, today: LocalDate): FunFact? {
    val days = guest.currentYear.totalDays
    val percent = days * PERCENT_FACTOR / today.dayOfYear
    return FunFact(
        guest.guestId,
        "${guest.firstName} har vært $days dager på hytta i år ($percent % av året så langt)",
        PRIORITY_DAYS_THIS_YEAR,
    ).takeIf { days >= MIN_DAYS_THIS_YEAR }
}

private fun totalDays(guest: LiveGuestStats, dataStartDate: LocalDate): FunFact? = FunFact(
    guest.guestId,
    "${guest.firstName} har tilbrakt ${guest.allTime.totalDays} dager på hytta siden " +
        dataStartDate.norwegianShortMonthYear(),
    PRIORITY_TOTAL_DAYS,
).takeIf { guest.allTime.totalDays >= MIN_TOTAL_DAYS }

private fun lastVisitDate(guest: LiveGuestStats): FunFact? = guest.lastVisitDate?.let {
    FunFact(
        guest.guestId,
        "${guest.firstName} var sist her i ${it.norwegianShortMonthYear()}",
        PRIORITY_LAST_VISIT_DATE,
    )
}

/** The single non-family guest with the most visits, when at least two non-family guests are present. */
private fun mostExperienced(guests: List<LiveGuestStats>): FunFact? {
    val visitors = guests.filter { !it.isFamily }
    val maxVisits = visitors.maxOfOrNull { it.allTime.totalVisits }
    return visitors.singleOrNull { it.allTime.totalVisits == maxVisits }
        ?.takeIf { visitors.size >= 2 }
        ?.let {
            FunFact(
                it.guestId,
                "${it.firstName} er mest erfaren i gjengen med $maxVisits besøk",
                PRIORITY_MOST_EXPERIENCED,
            )
        }
}

private fun stayProgress(context: GuestFunFactContext): FunFact? {
    val day = context.stay.startDate.daysUntil(context.today) + 1
    val totalDays = day + context.stay.remainingNights
    return FunFact(null, "Dag $day av $totalDays på hytta", PRIORITY_STAY_PROGRESS)
        .takeIf { day >= 1 && totalDays > 1 }
}

/** A new record for the fastest trip to the cabin, once there are enough earlier trips to compare with. */
private fun drivingRecord(driving: ToCabinDriving?): FunFact? {
    val current = driving?.currentMinutes
    val previous = driving?.previousMinutes.orEmpty()
    val isRecord = current != null && previous.size >= MIN_TRIPS_FOR_DRIVING_RECORD && current < previous.min()
    return current?.takeIf { isRecord }?.let {
        FunFact(null, "Ny rekord! Raskeste tur til hytta: ${norwegianDuration(it)}", PRIORITY_DRIVING_RECORD)
    }
}

private fun norwegianDuration(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    return if (hours > 0) "$hours t ${minutes % MINUTES_PER_HOUR} min" else "$minutes min"
}

private fun arrivalSoon(daysUntil: Int): FunFact? {
    val text = when (daysUntil) {
        0 -> "Neste besøk starter i dag"
        1 -> "Neste besøk starter i morgen"
        else -> "Neste besøk starter i overmorgen"
    }
    return FunFact(null, text, PRIORITY_ARRIVAL_SOON).takeIf { daysUntil in 0..ARRIVAL_SOON_MAX_DAYS }
}

private fun countdownToNextVisit(daysUntil: Int, isOccupied: Boolean): FunFact? =
    FunFact(null, "Neste besøk om $daysUntil dager", PRIORITY_COUNTDOWN_TO_NEXT_VISIT)
        .takeIf { isOccupied && daysUntil > ARRIVAL_SOON_MAX_DAYS }

private fun nextFirstTimer(guest: LiveGuestStats): FunFact? =
    FunFact(guest.guestId, "${guest.firstName} kommer på hytta for første gang! 🎉", PRIORITY_NEXT_FIRST_TIMER)
        .takeIf { !guest.isFamily && guest.isFirstVisit }

/** The visit is already counted for started reservations only, so the next visit is `totalVisits + 1`. */
private fun nextVisitMilestone(guest: LiveGuestStats): FunFact? {
    val visits = guest.allTime.totalVisits + 1
    return FunFact(
        guest.guestId,
        "Neste besøk blir besøk nr. $visits for ${guest.firstName}",
        PRIORITY_NEXT_VISIT_MILESTONE,
    ).takeIf { isVisitMilestone(guest, visits) }
}

private fun cabinVisitMilestone(context: CabinFunFactContext): FunFact? = FunFact(
    null,
    "Dette er hyttebesøk nr. ${context.totals.visits} siden ${context.dataStartDate.norwegianShortMonthYear()}",
    PRIORITY_CABIN_VISIT_MILESTONE,
).takeIf { context.isOccupied && context.totals.visits > 0 && context.totals.visits % CABIN_VISIT_MILESTONE_STEP == 0 }

private fun cabinTotals(context: CabinFunFactContext): FunFact = FunFact(
    guestId = null,
    text = "Siden ${context.dataStartDate.norwegianShortMonthYear()}: ${context.totals.visits} besøk, " +
        "${context.totals.nights} netter og ${context.totals.uniqueGuests} ulike gjester",
    priority = PRIORITY_CABIN_TOTALS,
)

/** Average driving time compared with the same month last year, or with last year when the month has no data. */
private fun drivingTimeVsLastYear(context: CabinFunFactContext): List<FunFact> {
    val month = context.today.month
    val thisMonth = context.currentYear?.months?.getOrNull(month.ordinal)?.drivingTime
    val lastYearMonth = context.previousYear?.months?.getOrNull(month.ordinal)?.drivingTime
    val thisYear = context.currentYear?.drivingTime
    val lastYear = context.previousYear?.drivingTime
    val monthLabel = "i ${norwegianMonthNameOf(month)} i fjor"
    return listOfNotNull(
        drivingTimeFact(
            "Turen til hytta",
            MinutesComparison(thisMonth?.avgToCabinMinutes, lastYearMonth?.avgToCabinMinutes),
            MinutesComparison(thisYear?.avgToCabinMinutes, lastYear?.avgToCabinMinutes),
            monthLabel,
        ),
        drivingTimeFact(
            "Turen fra hytta",
            MinutesComparison(thisMonth?.avgFromCabinMinutes, lastYearMonth?.avgFromCabinMinutes),
            MinutesComparison(thisYear?.avgFromCabinMinutes, lastYear?.avgFromCabinMinutes),
            monthLabel,
        ),
    )
}

private data class MinutesComparison(val current: Int?, val previous: Int?) {
    val isComplete: Boolean get() = current != null && previous != null
    val diff: Int? get() = if (current != null && previous != null) current - previous else null
}

private fun drivingTimeFact(
    subject: String,
    month: MinutesComparison,
    year: MinutesComparison,
    monthLabel: String,
): FunFact? {
    val comparison = if (month.isComplete) month else year
    val label = if (month.isComplete) monthLabel else "i fjor"
    return comparison.diff
        ?.takeIf { abs(it) >= MIN_DRIVING_TIME_DIFF_MINUTES }
        ?.let {
            val direction = if (it < 0) "kortere" else "lengre"
            FunFact(null, "$subject er ${abs(it)} min $direction enn $label", PRIORITY_DRIVING_TIME_VS_LAST_YEAR)
        }
}

private fun newGuestsThisYear(year: YearStats?): FunFact? = year?.newGuests?.size?.takeIf { it > 0 }?.let {
    FunFact(
        null,
        if (it ==
            1
        ) {
            "1 ny gjest på hytta i år"
        } else {
            "$it nye gjester på hytta i år"
        },
        PRIORITY_NEW_GUESTS_THIS_YEAR,
    )
}

private fun occupancyThisYear(year: YearStats?): FunFact? =
    year?.occupancy?.dayOccupancy?.roundToInt()?.takeIf { it >= 1 }?.let {
        FunFact(null, "Hytta har vært i bruk $it % av årets dager", PRIORITY_OCCUPANCY)
    }

private fun mostVisitedMonth(year: YearStats?): FunFact? =
    year?.visits?.monthMostVisits?.takeIf { it.visitCount >= MIN_MONTH_VISITS_FOR_MOST_VISITED }?.let {
        val monthName = norwegianMonthNameOf(Month(it.monthNumber))
        FunFact(null, "Mest besøkte måned i år: $monthName (${it.visitCount} besøk)", PRIORITY_MOST_VISITED_MONTH)
    }

private fun monthVsSameMonthLastYear(context: CabinFunFactContext): FunFact? {
    val month = context.today.month
    val diff = context.currentYear?.months?.getOrNull(month.ordinal)?.visits?.comparedToSameMonthLastYear
    return diff?.takeIf { it != 0 }?.let {
        val moreOrFewer = if (it > 0) "flere" else "færre"
        FunFact(
            null,
            "Så langt i ${norwegianMonthNameOf(month)}: ${abs(it)} $moreOrFewer besøk enn i fjor",
            PRIORITY_MONTH_VS_LAST_YEAR,
        )
    }
}

private fun longestStay(year: YearStats?): FunFact? = year?.nights?.maxNights?.takeIf { it >= 1 }?.let {
    FunFact(null, "Årets lengste opphold: $it netter", PRIORITY_LONGEST_STAY)
}

private fun avgGroupSize(year: YearStats?): FunFact? = year?.visits?.avgGroupSize?.let {
    FunFact(null, "I snitt ${it.round1().toNorwegianDecimal()} personer per besøk i år", PRIORITY_AVG_GROUP_SIZE)
}

/** Total km driven to and from the cabin this year, as laps around the earth. */
private fun totalDistance(year: YearStats?): FunFact? {
    val distance = year?.drivingDistance ?: return null
    val km = distance.totalToCabinKm + distance.totalFromCabinKm
    val laps = (km / EARTH_CIRCUMFERENCE_KM).round1()
    return FunFact(
        null,
        "Årets hytteturer: ${km.roundToInt().groupThousands()} km, ${laps.toNorwegianDecimal()} ganger rundt jorda",
        PRIORITY_TOTAL_DISTANCE,
    ).takeIf { laps >= MIN_LAPS_AROUND_EARTH }
}

private fun evConsumption(year: YearStats?): FunFact? = year?.ev?.avgEnergyConsumptionKwhPer100km?.let {
    FunFact(
        null,
        "Strømforbruk på hyttetur i år: ${it.round1().toNorwegianDecimal()} kWh per 100 km",
        PRIORITY_EV_CONSUMPTION,
    )
}

private fun avgDrivingTime(year: YearStats?): FunFact? {
    val driving = year?.drivingTime
    val toCabin = driving?.avgToCabinMinutes
    val fromCabin = driving?.avgFromCabinMinutes
    return if (toCabin != null && fromCabin != null) {
        FunFact(
            null,
            "Snitt kjøretid i år: ${norwegianDuration(toCabin)} til hytta og ${norwegianDuration(fromCabin)} hjem",
            PRIORITY_AVG_DRIVING_TIME,
        )
    } else {
        null
    }
}

private fun yearsOfOwnership(today: LocalDate): FunFact? = OWNERSHIP_START.yearsUntil(today).takeIf { it >= 1 }?.let {
    FunFact(null, "Hytta har vært i familien i $it år", PRIORITY_YEARS_OF_OWNERSHIP)
}

private fun Double.toNorwegianDecimal(): String = toString().replace('.', ',')

private fun Int.groupThousands(): String =
    toString().reversed().chunked(THOUSANDS_GROUP_SIZE).joinToString(" ").reversed()
