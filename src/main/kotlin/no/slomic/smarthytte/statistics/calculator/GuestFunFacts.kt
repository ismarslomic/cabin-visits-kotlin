@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.yearsUntil
import no.slomic.smarthytte.common.DAYS_PER_YEAR
import no.slomic.smarthytte.common.MONTHS_PER_YEAR
import no.slomic.smarthytte.common.PERCENT_FACTOR
import no.slomic.smarthytte.common.norwegianDuration
import no.slomic.smarthytte.common.norwegianShortMonthYear
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.GuestStats

// Selection: at most this many facts are kept per guest
private const val MAX_FACTS_PER_GUEST = 2

// Visit milestones: guests at fixed visit counts, family at every step
internal val GUEST_VISIT_MILESTONES = setOf(5, 10, 25, 50, 75, 100, 150, 200)
private const val FAMILY_VISIT_MILESTONE_STEP = 50

// Days milestones: total days at the cabin crossed during the stay
private val DAYS_MILESTONES = listOf(50, 100, 200, 365)

// Long absence and visit streak
private const val MIN_MONTHS_FOR_LONG_ABSENCE = 12
private const val MIN_MONTHS_FOR_YEARS = 24
internal const val MIN_YEARS_FOR_STREAK = 3

// Share of all trips (percent of the cabin's visits)
private const val MIN_SHARE_OF_TRIPS_PERCENT = 25

// Year race: minimum visits for a leader, and how close the chasers must be
private const val MIN_RACE_VISITS = 2
private const val MAX_GAP_TO_LEADER = 3
private const val MAX_TIED_LEADERS = 3

// Share of days since data start and share of life (percent, shown from this share)
internal const val MIN_SHARE_PERCENT = 5

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
fun calculateGuestFunFacts(guests: List<GuestStats>, context: GuestFunFactContext): List<FunFact> {
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

private fun guestFunFacts(guest: GuestStats, context: GuestFunFactContext): List<FunFact> = listOfNotNull(
    firstVisit(guest),
    visitMilestone(guest),
    daysMilestone(guest, context),
    longAbsence(guest, context.today),
    visitStreak(guest, context.today),
    yearRace(guest, context.standings),
    topGuestAllTime(guest, context.standings, context.dataStartDate),
    firstVisitThisYear(guest),
    shareOfTrips(guest, context.allTimeVisits),
    shareOfDays(guest, context),
    shareOfLife(guest),
    daysThisYear(guest, context.today),
    yearsSinceFirstVisit(guest, context.today),
    totalDays(guest, context.dataStartDate),
    lastVisitDate(guest),
)

private fun groupFunFacts(guests: List<GuestStats>, context: GuestFunFactContext): List<FunFact> = listOfNotNull(
    drivingRecord(context.toCabinDriving),
    lastDay(context.stay.remainingNights),
    newInGroup(guests),
    mostExperienced(guests),
    nightsLeft(context.stay.remainingNights),
    stayProgress(context),
)

private fun firstVisit(guest: GuestStats): FunFact? = funFact(guest.guestId, FunFactPriority.FIRST_VISIT) {
    text("Første registrerte besøk for ")
    guestName(guest.firstName)
    text("! 🎉")
}.takeIf { !guest.isFamily && guest.isFirstVisit }

internal fun visitMilestone(guest: GuestStats): FunFact? {
    val visits = guest.allTime.totalVisits
    return funFact(guest.guestId, FunFactPriority.VISIT_MILESTONE) {
        text("Dette er besøk nr. ")
        stat(visits)
        text(" for ")
        guestName(guest.firstName)
        text("!")
    }.takeIf { isVisitMilestone(guest, visits) }
}

internal fun isVisitMilestone(guest: GuestStats, visits: Int): Boolean = if (guest.isFamily) {
    visits > 0 && visits % FAMILY_VISIT_MILESTONE_STEP == 0
} else {
    visits in GUEST_VISIT_MILESTONES
}

internal fun longAbsence(guest: GuestStats, today: LocalDate): FunFact? {
    val lastVisit = guest.lastVisitDate ?: return null
    val months = lastVisit.monthsUntil(today)
    val sinceLast = absenceInWords(months)
    return funFact(guest.guestId, FunFactPriority.LONG_ABSENCE) {
        text("Det er ")
        stat(sinceLast)
        text(" siden sist, ")
        guestName(guest.firstName)
        text("!")
    }.takeIf { !guest.isFamily && months >= MIN_MONTHS_FOR_LONG_ABSENCE }
}

/** Months below two years, otherwise whole years. */
private fun absenceInWords(months: Int): String =
    if (months < MIN_MONTHS_FOR_YEARS) "$months måneder" else "${months / MONTHS_PER_YEAR} år"

private fun visitStreak(guest: GuestStats, today: LocalDate): FunFact? {
    val years = consecutiveYearsUpTo(today.year, guest.yearsVisited)
    return funFact(guest.guestId, FunFactPriority.VISIT_STREAK) {
        guestName(guest.firstName)
        text(" har besøkt hytta ")
        stat("$years år")
        text(" på rad")
    }.takeIf { !guest.isFamily && years >= MIN_YEARS_FOR_STREAK }
}

/** Number of consecutive years, counting backwards from [year], in which the guest has visited. */
internal fun consecutiveYearsUpTo(year: Int, yearsVisited: List<Int>): Int =
    generateSequence(year) { it - 1 }.takeWhile { it in yearsVisited }.count()

private fun firstVisitThisYear(guest: GuestStats): FunFact? =
    funFact(guest.guestId, FunFactPriority.FIRST_VISIT_THIS_YEAR) {
        text("Årets første besøk for ")
        guestName(guest.firstName)
    }.takeIf { !guest.isFirstVisit && guest.currentYear.totalVisits == 1 }

internal fun shareOfTrips(guest: GuestStats, allTimeVisits: Int): FunFact? {
    if (allTimeVisits <= 0) return null
    val percent = guest.allTime.totalVisits * PERCENT_FACTOR / allTimeVisits
    val priority = if (guest.isFamily) FunFactPriority.SHARE_OF_TRIPS_FAMILY else FunFactPriority.SHARE_OF_TRIPS_GUEST
    return funFact(guest.guestId, priority) {
        guestName(guest.firstName)
        text(" har vært med på ")
        stat("$percent %")
        text(" av alle hytteturer")
    }.takeIf { percent >= MIN_SHARE_OF_TRIPS_PERCENT }
}

internal fun yearsSinceFirstVisit(guest: GuestStats, today: LocalDate): FunFact? {
    val firstVisit = guest.firstVisitDate ?: return null
    val years = firstVisit.yearsUntil(today)
    return funFact(guest.guestId, FunFactPriority.YEARS_SINCE_FIRST_VISIT) {
        guestName(guest.firstName)
        text(" hadde sitt første registrerte besøk for ")
        stat("$years år")
        text(" siden")
    }.takeIf { !guest.isFamily && years >= 1 }
}

private fun lastDay(remainingNights: Int): FunFact? = funFact(null, FunFactPriority.LAST_DAY) {
    text("Siste dag på hytta, ha en trygg hjemreise!")
}.takeIf { remainingNights <= LAST_DAY_REMAINING_NIGHTS }

private fun newInGroup(guests: List<GuestStats>): FunFact? {
    val newGuests = guests.count { !it.isFamily && it.isFirstVisit }
    return funFact(null, FunFactPriority.NEW_IN_GROUP) {
        stat("$newGuests av ${guests.size}")
        text(" er her for første gang")
    }.takeIf { newGuests >= MIN_NEW_GUESTS_IN_GROUP }
}

private fun nightsLeft(remainingNights: Int): FunFact? = funFact(null, FunFactPriority.NIGHTS_LEFT) {
    stat("$remainingNights netter")
    text(" igjen av oppholdet")
}.takeIf { remainingNights > LAST_DAY_REMAINING_NIGHTS }

private fun daysMilestone(guest: GuestStats, context: GuestFunFactContext): FunFact? {
    val total = guest.allTime.totalDays
    val daysThisStay = (context.stay.startDate.daysUntil(context.today) + 1).coerceAtLeast(0)
    val daysBeforeStay = total - daysThisStay
    val milestone = DAYS_MILESTONES.lastOrNull { daysBeforeStay < it && it <= total } ?: return null
    return funFact(guest.guestId, FunFactPriority.DAYS_MILESTONE) {
        guestName(guest.firstName)
        text(" passerer ")
        stat("$milestone dager")
        text(" på hytta! 🎉")
    }
}

/** Leader, tied for the lead or gap to the leader this year, within the family or among the other guests. */
private fun yearRace(guest: GuestStats, standings: List<GuestStanding>): FunFact? {
    val peers = standings.filter { it.isFamily == guest.isFamily }
    val leaderVisits = peers.maxOfOrNull { it.yearVisits } ?: 0
    return peers.firstOrNull { it.guestId == guest.guestId }
        ?.takeIf { leaderVisits >= MIN_RACE_VISITS }
        ?.let { raceFact(guest, it, peers, leaderVisits) }
}

private fun raceFact(guest: GuestStats, me: GuestStanding, peers: List<GuestStanding>, leaderVisits: Int): FunFact? {
    val leaders = peers.filter { it.yearVisits == leaderVisits }.sortedBy { it.guestId }
    val scope = if (guest.isFamily) "i familien" else "blant gjestene"
    val gap = leaderVisits - me.yearVisits
    return when {
        gap > 0 -> funFact(guest.guestId, FunFactPriority.YEAR_GAP_TO_LEADER) {
            guestName(guest.firstName)
            text(" ligger ")
            stat("$gap besøk")
            text(" bak ")
            guestName(leaders.first().firstName)
            text(" $scope i år")
        }.takeIf { gap <= MAX_GAP_TO_LEADER }

        leaders.size == 1 -> funFact(guest.guestId, FunFactPriority.YEAR_LEADER) {
            guestName(guest.firstName)
            text(" leder $scope i år med ")
            stat("$leaderVisits besøk")
        }

        else -> funFact(guest.guestId, FunFactPriority.YEAR_TIED_LEAD) {
            guestName(guest.firstName)
            text(" deler ledelsen $scope i år med ")
            leaders.filter { it.guestId != guest.guestId }.forEachIndexed { index, other ->
                if (index > 0) text(" og ")
                guestName(other.firstName)
            }
            text(" (")
            stat("$leaderVisits besøk")
            text(")")
        }.takeIf { leaders.size <= MAX_TIED_LEADERS }
    }
}

/** The guest (family excluded) with the most visits since the data start, if there is a single one. */
internal fun topGuestAllTime(guest: GuestStats, standings: List<GuestStanding>, dataStartDate: LocalDate): FunFact? {
    val guests = standings.filter { !it.isFamily }
    val maxVisits = guests.maxOfOrNull { it.allTimeVisits } ?: 0
    val leader = guests.singleOrNull { it.allTimeVisits == maxVisits }
    return funFact(guest.guestId, FunFactPriority.TOP_GUEST_ALL_TIME) {
        guestName(guest.firstName)
        text(" er gjesten med flest besøk siden ${dataStartDate.norwegianShortMonthYear()} (")
        stat(maxVisits)
        text(")")
    }.takeIf { !guest.isFamily && leader?.guestId == guest.guestId }
}

private fun shareOfDays(guest: GuestStats, context: GuestFunFactContext): FunFact? {
    val daysSinceStart = context.dataStartDate.daysUntil(context.today) + 1
    if (daysSinceStart <= 0) return null
    val percent = (guest.allTime.totalDays * PERCENT_FACTOR / daysSinceStart).coerceAtMost(PERCENT_FACTOR)
    return funFact(guest.guestId, FunFactPriority.SHARE_OF_DAYS) {
        guestName(guest.firstName)
        text(" har vært på hytta ")
        stat("$percent %")
        text(" av dagene siden ${context.dataStartDate.norwegianShortMonthYear()}")
    }.takeIf { percent >= MIN_SHARE_PERCENT }
}

internal fun shareOfLife(guest: GuestStats): FunFact? {
    val percent = shareOfLifePercent(guest) ?: return null
    return funFact(guest.guestId, FunFactPriority.SHARE_OF_LIFE) {
        guestName(guest.firstName)
        text(" har vært på hytta ca. ")
        stat("$percent %")
        text(" av livet sitt")
    }.takeIf { percent >= MIN_SHARE_PERCENT }
}

/**
 * Percent of the guest's life spent at the cabin, or null for guests under one year. The cabin was bought in Dec 2019,
 * so nobody has visits before the data starts, and the whole life is covered for guests of any age.
 */
internal fun shareOfLifePercent(guest: GuestStats): Int? {
    if (guest.age < 1) return null
    return (guest.allTime.totalDays * PERCENT_FACTOR / (guest.age * DAYS_PER_YEAR)).coerceAtMost(PERCENT_FACTOR)
}

internal fun daysThisYear(guest: GuestStats, today: LocalDate): FunFact? {
    val days = guest.currentYear.totalDays
    val percent = days * PERCENT_FACTOR / today.dayOfYear
    return funFact(guest.guestId, FunFactPriority.DAYS_THIS_YEAR) {
        guestName(guest.firstName)
        text(" har vært ")
        stat("$days dager")
        text(" på hytta i år (")
        stat("$percent %")
        text(" av året så langt)")
    }.takeIf { days >= MIN_DAYS_THIS_YEAR }
}

private fun totalDays(guest: GuestStats, dataStartDate: LocalDate): FunFact? = funFact(
    guest.guestId,
    FunFactPriority.TOTAL_DAYS,
) {
    guestName(guest.firstName)
    text(" har tilbrakt ")
    stat("${guest.allTime.totalDays} dager")
    text(" på hytta siden ${dataStartDate.norwegianShortMonthYear()}")
}.takeIf { guest.allTime.totalDays >= MIN_TOTAL_DAYS }

internal fun lastVisitDate(guest: GuestStats): FunFact? = guest.lastVisitDate?.let {
    funFact(guest.guestId, FunFactPriority.LAST_VISIT_DATE) {
        guestName(guest.firstName)
        text(" var sist her i ")
        stat(it.norwegianShortMonthYear())
    }
}

/** The single non-family guest with the most visits, when at least two non-family guests are present. */
private fun mostExperienced(guests: List<GuestStats>): FunFact? {
    val visitors = guests.filter { !it.isFamily }
    val maxVisits = visitors.maxOfOrNull { it.allTime.totalVisits }
    return visitors.singleOrNull { it.allTime.totalVisits == maxVisits }
        ?.takeIf { visitors.size >= 2 }
        ?.let {
            funFact(it.guestId, FunFactPriority.MOST_EXPERIENCED) {
                guestName(it.firstName)
                text(" er mest erfaren i gjengen med ")
                stat("$maxVisits besøk")
            }
        }
}

private fun stayProgress(context: GuestFunFactContext): FunFact? {
    val day = context.stay.startDate.daysUntil(context.today) + 1
    val totalDays = day + context.stay.remainingNights
    return funFact(null, FunFactPriority.STAY_PROGRESS) {
        text("Dag ")
        stat("$day av $totalDays")
        text(" på hytta")
    }.takeIf { day >= 1 && totalDays > 1 }
}

/** A new record for the fastest trip to the cabin, once there are enough earlier trips to compare with. */
private fun drivingRecord(driving: ToCabinDriving?): FunFact? {
    val current = driving?.currentMinutes
    val previous = driving?.previousMinutes.orEmpty()
    val isRecord = current != null && previous.size >= MIN_TRIPS_FOR_DRIVING_RECORD && current < previous.min()
    return current?.takeIf { isRecord }?.let {
        funFact(null, FunFactPriority.DRIVING_RECORD) {
            text("Ny rekord! Raskeste tur til hytta: ")
            stat(norwegianDuration(it))
        }
    }
}
