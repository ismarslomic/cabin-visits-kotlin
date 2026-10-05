@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.yearsUntil
import no.slomic.smarthytte.common.norwegianDuration
import no.slomic.smarthytte.common.norwegianShortMonthYear
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.LiveGuestStats

// Selection: at most this many facts are kept per guest
private const val MAX_FACTS_PER_GUEST = 2

// Visit milestones: guests at fixed visit counts, family at every step
private val GUEST_VISIT_MILESTONES = setOf(5, 10, 25, 50, 75, 100, 150, 200)
private const val FAMILY_VISIT_MILESTONE_STEP = 50

// Days milestones: total days at the cabin crossed during the stay
private val DAYS_MILESTONES = listOf(50, 100, 200, 365)

// Long absence and visit streak
private const val MIN_MONTHS_FOR_LONG_ABSENCE = 12
private const val MIN_YEARS_FOR_STREAK = 3

// Share of all trips (percent of the cabin's visits)
private const val MIN_SHARE_OF_TRIPS_PERCENT = 25

// Year race: minimum visits for a leader, and how close the chasers must be
private const val MIN_RACE_VISITS = 2
private const val MAX_GAP_TO_LEADER = 3
private const val MAX_TIED_LEADERS = 3

// Share of days since data start and share of life (percent, shown from this share)
private const val PERCENT_FACTOR = 100
private const val MIN_SHARE_PERCENT = 5
private const val DAYS_PER_YEAR = 365

// Days this year and total days at the cabin
private const val MIN_DAYS_THIS_YEAR = 3
private const val MIN_TOTAL_DAYS = 5

// Group facts: new guests, last day and nights left, driving record
private const val MIN_NEW_GUESTS_IN_GROUP = 2
private const val LAST_DAY_REMAINING_NIGHTS = 1
private const val MIN_TRIPS_FOR_DRIVING_RECORD = 5

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
fun calculateGuestFunFacts(guests: List<LiveGuestStats>, context: GuestFunFactContext): List<FunFact> {
    val hasNonFamilyGuests = guests.any { !it.isFamily }

    val guestFacts = guests.flatMap { guest ->
        val facts = guestFunFacts(guest, context)
            .sortedByDescending { it.priority }
            .take(MAX_FACTS_PER_GUEST)
        if (guest.isFamily && hasNonFamilyGuests) {
            facts.map { it.copy(priority = it.priority - FunFactPriority.FAMILY_PENALTY) }
        } else {
            facts
        }
    }

    return (guestFacts + groupFunFacts(guests, context))
        .sortedWith(compareByDescending<FunFact> { it.priority }.thenBy { it.guestId.orEmpty() })
}

private fun guestFunFacts(guest: LiveGuestStats, context: GuestFunFactContext): List<FunFact> = listOfNotNull(
    firstVisit(guest),
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

private fun firstVisit(guest: LiveGuestStats): FunFact? =
    FunFact(guest.guestId, "Første registrerte besøk for ${guest.firstName}! 🎉", FunFactPriority.FIRST_VISIT)
        .takeIf { !guest.isFamily && guest.isFirstVisit }

private fun visitMilestone(guest: LiveGuestStats): FunFact? {
    val visits = guest.allTime.totalVisits
    return FunFact(guest.guestId, "Dette er besøk nr. $visits for ${guest.firstName}!", FunFactPriority.VISIT_MILESTONE)
        .takeIf { isVisitMilestone(guest, visits) }
}

internal fun isVisitMilestone(guest: LiveGuestStats, visits: Int): Boolean = if (guest.isFamily) {
    visits > 0 && visits % FAMILY_VISIT_MILESTONE_STEP == 0
} else {
    visits in GUEST_VISIT_MILESTONES
}

private fun longAbsence(guest: LiveGuestStats, today: LocalDate): FunFact? {
    val lastVisit = guest.lastVisitDate ?: return null
    val months = lastVisit.monthsUntil(today)
    return FunFact(
        guest.guestId,
        "Det er $months måneder siden sist, ${guest.firstName}!",
        FunFactPriority.LONG_ABSENCE,
    )
        .takeIf { !guest.isFamily && months >= MIN_MONTHS_FOR_LONG_ABSENCE }
}

private fun visitStreak(guest: LiveGuestStats, today: LocalDate): FunFact? {
    val years = consecutiveYearsUpTo(today.year, guest.yearsVisited)
    return FunFact(guest.guestId, "${guest.firstName} har besøkt hytta $years år på rad", FunFactPriority.VISIT_STREAK)
        .takeIf { !guest.isFamily && years >= MIN_YEARS_FOR_STREAK }
}

/** Number of consecutive years, counting backwards from [year], in which the guest has visited. */
private fun consecutiveYearsUpTo(year: Int, yearsVisited: List<Int>): Int =
    generateSequence(year) { it - 1 }.takeWhile { it in yearsVisited }.count()

private fun firstVisitThisYear(guest: LiveGuestStats): FunFact? =
    FunFact(guest.guestId, "Årets første besøk for ${guest.firstName}", FunFactPriority.FIRST_VISIT_THIS_YEAR)
        .takeIf { !guest.isFirstVisit && guest.currentYear.totalVisits == 1 }

private fun shareOfTrips(guest: LiveGuestStats, allTimeVisits: Int): FunFact? {
    if (allTimeVisits <= 0) return null
    val percent = guest.allTime.totalVisits * PERCENT_FACTOR / allTimeVisits
    val priority = if (guest.isFamily) FunFactPriority.SHARE_OF_TRIPS_FAMILY else FunFactPriority.SHARE_OF_TRIPS_GUEST
    return FunFact(guest.guestId, "${guest.firstName} har vært med på $percent % av alle hytteturer", priority)
        .takeIf { percent >= MIN_SHARE_OF_TRIPS_PERCENT }
}

private fun yearsSinceFirstVisit(guest: LiveGuestStats, today: LocalDate): FunFact? {
    val firstVisit = guest.firstVisitDate ?: return null
    val years = firstVisit.yearsUntil(today)
    return FunFact(
        guest.guestId,
        "${guest.firstName} hadde sitt første registrerte besøk for $years år siden",
        FunFactPriority.YEARS_SINCE_FIRST_VISIT,
    ).takeIf { !guest.isFamily && years >= 1 }
}

private fun lastDay(remainingNights: Int): FunFact? =
    FunFact(null, "Siste dag på hytta, ha en trygg hjemreise!", FunFactPriority.LAST_DAY)
        .takeIf { remainingNights <= LAST_DAY_REMAINING_NIGHTS }

private fun newInGroup(guests: List<LiveGuestStats>): FunFact? {
    val newGuests = guests.count { !it.isFamily && it.isFirstVisit }
    return FunFact(null, "$newGuests av ${guests.size} er her for første gang", FunFactPriority.NEW_IN_GROUP)
        .takeIf { newGuests >= MIN_NEW_GUESTS_IN_GROUP }
}

private fun nightsLeft(remainingNights: Int): FunFact? =
    FunFact(null, "$remainingNights netter igjen av oppholdet", FunFactPriority.NIGHTS_LEFT)
        .takeIf { remainingNights > LAST_DAY_REMAINING_NIGHTS }

private fun daysMilestone(guest: LiveGuestStats, context: GuestFunFactContext): FunFact? {
    val total = guest.allTime.totalDays
    val daysThisStay = (context.stay.startDate.daysUntil(context.today) + 1).coerceAtLeast(0)
    val daysBeforeStay = total - daysThisStay
    val milestone = DAYS_MILESTONES.lastOrNull { daysBeforeStay < it && it <= total } ?: return null
    return FunFact(
        guest.guestId,
        "${guest.firstName} passerer $milestone dager på hytta! 🎉",
        FunFactPriority.DAYS_MILESTONE,
    )
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
            FunFactPriority.YEAR_GAP_TO_LEADER,
        ).takeIf { gap <= MAX_GAP_TO_LEADER }

        leaders.size == 1 -> FunFact(
            guest.guestId,
            "${guest.firstName} leder $scope i år med $leaderVisits besøk",
            FunFactPriority.YEAR_LEADER,
        )

        else -> FunFact(
            guest.guestId,
            "${guest.firstName} deler ledelsen $scope i år med " +
                leaders.filter { it.guestId != guest.guestId }.joinToString(" og ") { it.firstName } +
                " ($leaderVisits besøk)",
            FunFactPriority.YEAR_TIED_LEAD,
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
        FunFactPriority.TOP_GUEST_ALL_TIME,
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
        FunFactPriority.SHARE_OF_DAYS,
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
        FunFactPriority.SHARE_OF_LIFE,
    ).takeIf { percent >= MIN_SHARE_PERCENT }
}

private fun daysThisYear(guest: LiveGuestStats, today: LocalDate): FunFact? {
    val days = guest.currentYear.totalDays
    val percent = days * PERCENT_FACTOR / today.dayOfYear
    return FunFact(
        guest.guestId,
        "${guest.firstName} har vært $days dager på hytta i år ($percent % av året så langt)",
        FunFactPriority.DAYS_THIS_YEAR,
    ).takeIf { days >= MIN_DAYS_THIS_YEAR }
}

private fun totalDays(guest: LiveGuestStats, dataStartDate: LocalDate): FunFact? = FunFact(
    guest.guestId,
    "${guest.firstName} har tilbrakt ${guest.allTime.totalDays} dager på hytta siden " +
        dataStartDate.norwegianShortMonthYear(),
    FunFactPriority.TOTAL_DAYS,
).takeIf { guest.allTime.totalDays >= MIN_TOTAL_DAYS }

private fun lastVisitDate(guest: LiveGuestStats): FunFact? = guest.lastVisitDate?.let {
    FunFact(
        guest.guestId,
        "${guest.firstName} var sist her i ${it.norwegianShortMonthYear()}",
        FunFactPriority.LAST_VISIT_DATE,
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
                FunFactPriority.MOST_EXPERIENCED,
            )
        }
}

private fun stayProgress(context: GuestFunFactContext): FunFact? {
    val day = context.stay.startDate.daysUntil(context.today) + 1
    val totalDays = day + context.stay.remainingNights
    return FunFact(null, "Dag $day av $totalDays på hytta", FunFactPriority.STAY_PROGRESS)
        .takeIf { day >= 1 && totalDays > 1 }
}

/** A new record for the fastest trip to the cabin, once there are enough earlier trips to compare with. */
private fun drivingRecord(driving: ToCabinDriving?): FunFact? {
    val current = driving?.currentMinutes
    val previous = driving?.previousMinutes.orEmpty()
    val isRecord = current != null && previous.size >= MIN_TRIPS_FOR_DRIVING_RECORD && current < previous.min()
    return current?.takeIf { isRecord }?.let {
        FunFact(null, "Ny rekord! Raskeste tur til hytta: ${norwegianDuration(it)}", FunFactPriority.DRIVING_RECORD)
    }
}
