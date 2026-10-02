@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalDate
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.yearsUntil
import no.slomic.smarthytte.common.norwegianShortMonthYear
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.LiveGuestStats
import kotlin.random.Random

private const val PRIORITY_FIRST_VISIT = 100
private const val PRIORITY_VISIT_MILESTONE = 95
private const val PRIORITY_CABIN_VISIT_MILESTONE = 85
private const val PRIORITY_LONG_ABSENCE = 80
private const val PRIORITY_VISIT_STREAK = 70
private const val PRIORITY_LAST_DAY = 60
private const val PRIORITY_FIRST_VISIT_THIS_YEAR = 55
private const val PRIORITY_NEW_IN_GROUP = 55
private const val PRIORITY_SHARE_OF_TRIPS_GUEST = 50
private const val PRIORITY_YEARS_SINCE_FIRST_VISIT = 40
private const val PRIORITY_SHARE_OF_TRIPS_FAMILY = 30
private const val PRIORITY_NIGHTS_LEFT = 30
private const val PRIORITY_CABIN_TOTALS = 25
private const val FAMILY_PRIORITY_PENALTY = 30

private val GUEST_VISIT_MILESTONES = setOf(5, 10, 25, 50, 75, 100, 150, 200)
private const val FAMILY_VISIT_MILESTONE_STEP = 50
private const val CABIN_VISIT_MILESTONE_STEP = 50
private const val MIN_MONTHS_FOR_LONG_ABSENCE = 12
private const val MIN_YEARS_FOR_STREAK = 3
private const val MIN_SHARE_OF_TRIPS_PERCENT = 25
private const val MIN_NEW_GUESTS_IN_GROUP = 2
private const val LAST_DAY_REMAINING_NIGHTS = 1
private const val PERCENT_FACTOR = 100

/**
 * Fun facts about the guests of the current reservation, sorted by priority (highest first).
 *
 * Facts about family members are down-prioritised when the reservation includes other guests; when only family
 * is present they keep their full priority. Visits are only registered from the configured data start date,
 * so the wording says "registrert" instead of claiming anything about earlier history.
 */
fun calculateGuestFunFacts(
    guests: List<LiveGuestStats>,
    today: LocalDate,
    remainingNights: Int,
    allTimeVisits: Int,
    random: Random = Random.Default,
): List<FunFact> {
    val hasNonFamilyGuests = guests.any { !it.isFamily }

    val guestFacts = guests.flatMap { guest ->
        val facts = guestFunFacts(guest, today, allTimeVisits, random)
        if (guest.isFamily && hasNonFamilyGuests) {
            facts.map { it.copy(priority = it.priority - FAMILY_PRIORITY_PENALTY) }
        } else {
            facts
        }
    }

    return (guestFacts + groupFunFacts(guests, remainingNights))
        .sortedByDescending { it.priority }
}

/** Fun facts about the cabin as a whole, sorted by priority (highest first). */
fun calculateCabinFunFacts(
    dataStartDate: LocalDate,
    isOccupied: Boolean,
    allTimeVisits: Int,
    allTimeNights: Int,
    allTimeUniqueGuests: Int,
): List<FunFact> = listOfNotNull(
    cabinVisitMilestone(dataStartDate, isOccupied, allTimeVisits),
    FunFact(
        guestId = null,
        text = "Siden ${dataStartDate.norwegianShortMonthYear()}: $allTimeVisits besøk, $allTimeNights netter " +
            "og $allTimeUniqueGuests ulike gjester",
        priority = PRIORITY_CABIN_TOTALS,
    ),
).sortedByDescending { it.priority }

private fun guestFunFacts(guest: LiveGuestStats, today: LocalDate, allTimeVisits: Int, random: Random): List<FunFact> =
    listOfNotNull(
        firstVisit(guest, random),
        visitMilestone(guest),
        longAbsence(guest, today),
        visitStreak(guest, today),
        firstVisitThisYear(guest),
        shareOfTrips(guest, allTimeVisits),
        yearsSinceFirstVisit(guest, today),
    )

private fun groupFunFacts(guests: List<LiveGuestStats>, remainingNights: Int): List<FunFact> =
    listOfNotNull(lastDay(remainingNights), newInGroup(guests), nightsLeft(remainingNights))

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
    val isMilestone = if (guest.isFamily) {
        visits > 0 && visits % FAMILY_VISIT_MILESTONE_STEP == 0
    } else {
        visits in GUEST_VISIT_MILESTONES
    }
    return FunFact(guest.guestId, "Dette er besøk nr. $visits for ${guest.firstName}!", PRIORITY_VISIT_MILESTONE)
        .takeIf { isMilestone }
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

private fun cabinVisitMilestone(dataStartDate: LocalDate, isOccupied: Boolean, allTimeVisits: Int): FunFact? = FunFact(
    null,
    "Dette er hyttebesøk nr. $allTimeVisits siden ${dataStartDate.norwegianShortMonthYear()}",
    PRIORITY_CABIN_VISIT_MILESTONE,
).takeIf { isOccupied && allTimeVisits > 0 && allTimeVisits % CABIN_VISIT_MILESTONE_STEP == 0 }
