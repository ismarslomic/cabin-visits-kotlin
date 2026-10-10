@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import no.slomic.smarthytte.common.norwegianShortMonthYear
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.common.toNorwegianDecimal
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.GuestStats
import no.slomic.smarthytte.statistics.model.GuestVisit
import no.slomic.smarthytte.statistics.model.VisitStatus

// Favourite season and record year: minimum number of visits (years) to have a favourite
private const val MIN_VISITS_FOR_FAVOURITE_SEASON = 3
private const val MIN_YEARS_FOR_RECORD_YEAR = 3

// Next milestone: show it when the guest is at most this many visits away
private const val MAX_VISITS_TO_NEXT_MILESTONE = 3

// Companions: minimum number of shared visits, number of names listed at most and number of different companions
private const val MIN_SHARED_VISITS = 2
private const val MAX_TOP_COMPANIONS = 3
private const val MIN_DIFFERENT_COMPANIONS = 5

// Rank among guests: show second and third place when the leader is at most this many visits ahead
private const val MAX_RANK_AMONG_GUESTS = 3
private const val MAX_VISITS_BEHIND_LEADER = 3

// Longest stay and total time at the cabin
private const val MIN_DAYS_FOR_LONGEST_STAY = 6
private const val MIN_DAYS_FOR_TOTAL_TIME = 30
private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_YEAR = 365
private const val MAX_DAYS_IN_WEEKS = 60
private const val MAX_DAYS_IN_MONTHS = 730

/** Another guest the guest has been at the cabin together with, and the number of visits they shared. */
data class Companion(val firstName: String, val isFamily: Boolean, val sharedVisits: Int)

/**
 * Everything besides the guest and the visits that the guest profile fun facts depend on: [allTimeVisits] is the
 * number of visits that have started for the whole cabin, [standings] compare all guests and [companions] are the
 * guests the guest has been at the cabin together with.
 */
data class GuestProfileFunFactContext(
    val today: LocalDate,
    val dataStartDate: LocalDate,
    val allTimeVisits: Int = 0,
    val standings: List<GuestStanding> = emptyList(),
    val companions: List<Companion> = emptyList(),
)

/**
 * Fun facts for the guest profile, written about the guest by first name like the live fun facts, sorted by priority
 * (highest first). Every fact whose threshold holds is included, there is no cap.
 */
fun calculateGuestProfileFunFacts(
    guest: GuestStats,
    visits: List<GuestVisit>,
    context: GuestProfileFunFactContext,
): List<FunFact> {
    val profileVisits = ProfileVisits(visits)
    val today = context.today
    return listOfNotNull(
        shareOfLife(guest),
        visitMilestone(guest, profileVisits),
        visitsToNextMilestone(guest),
        upcomingVisit(guest, profileVisits, today),
        loyalGuest(guest, profileVisits, today),
        favouriteSeason(guest, profileVisits),
        longestStay(guest, profileVisits),
        totalTimeInWords(guest),
        yearsSinceFirstVisit(guest, today),
        longAbsence(guest, today),
        recordYear(guest, profileVisits, today),
        daysThisYear(guest, today),
        shareOfTrips(guest, context.allTimeVisits),
        topGuestAllTime(guest, context.standings, context.dataStartDate),
        rankAmongGuests(guest, context.standings),
        companions(guest, context.companions),
        differentCompanions(guest, context.companions),
    ).sortedWith(compareByDescending<FunFact> { it.priority }.thenBy { it.text })
}

/** The visits of a guest, oldest first, by status. Upcoming visits have not happened yet. */
private class ProfileVisits(visits: List<GuestVisit>) {
    val happened = visits.filter { it.status != VisitStatus.UPCOMING }
    val ongoing = visits.firstOrNull { it.status == VisitStatus.ONGOING }
    val nextUpcoming = visits.firstOrNull { it.status == VisitStatus.UPCOMING }
}

/** The live fact while the guest is at the cabin ("Dette er besøk nr. 10"), otherwise a count of visits so far. */
private fun visitMilestone(guest: GuestStats, visits: ProfileVisits): FunFact? {
    val milestone = visitMilestone(guest) ?: return null
    return if (visits.ongoing != null) {
        milestone
    } else {
        milestone.copy(text = "${guest.firstName} har besøkt hytta ${guest.allTime.totalVisits} ganger!")
    }
}

private fun visitsToNextMilestone(guest: GuestStats): FunFact? {
    val visits = guest.allTime.totalVisits
    val nextMilestone = GUEST_VISIT_MILESTONES.filter { it > visits }.minOrNull() ?: return null
    val remaining = nextMilestone - visits
    return FunFact(
        guest.guestId,
        "${guest.firstName} er $remaining besøk unna å nå $nextMilestone besøk på hytta",
        FunFactPriority.VISITS_TO_NEXT_MILESTONE,
    ).takeIf { !guest.isFamily && remaining <= MAX_VISITS_TO_NEXT_MILESTONE }
}

/** The next visit is `totalVisits + 1`, as only visits that have started are counted in the total. */
private fun upcomingVisit(guest: GuestStats, visits: ProfileVisits, today: LocalDate): FunFact? {
    val next = visits.nextUpcoming ?: return null
    val daysUntil = today.daysUntil(next.startDate)
    val whenText = if (daysUntil == 1) "i morgen" else "om $daysUntil dager"
    val visitNumber = guest.allTime.totalVisits + 1
    val text = "Neste besøk for ${guest.firstName} er $whenText"
    return if (isVisitMilestone(guest, visitNumber)) {
        FunFact(
            guest.guestId,
            "$text, da er det besøk nr. $visitNumber!",
            FunFactPriority.UPCOMING_VISIT_MILESTONE,
        )
    } else {
        FunFact(guest.guestId, text, FunFactPriority.UPCOMING_VISIT)
    }
}

/**
 * Years in a row with a visit, counted backwards from the last year with a visit. The streak is only active if that
 * year is this year or last year. Upcoming visits are not included, they have not happened yet.
 */
private fun loyalGuest(guest: GuestStats, visits: ProfileVisits, today: LocalDate): FunFact? {
    val yearsVisited = visits.happened.map { it.startDate.year }.distinct()
    val lastYear = yearsVisited.maxOrNull()?.takeIf { it >= today.year - 1 } ?: return null
    val years = consecutiveYearsUpTo(lastYear, yearsVisited)
    return FunFact(
        guest.guestId,
        "${guest.firstName} er en trofast gjest og har besøkt hytta de siste $years årene",
        FunFactPriority.VISIT_STREAK,
    ).takeIf { !guest.isFamily && years >= MIN_YEARS_FOR_STREAK }
}

private fun favouriteSeason(guest: GuestStats, visits: ProfileVisits): FunFact? {
    val visitsBySeason = visits.happened.groupingBy { Season.of(it.startDate.month) }.eachCount()
    val mostVisits = visitsBySeason.values.maxOrNull() ?: 0
    val favourite = visitsBySeason.filterValues { it == mostVisits }.keys.singleOrNull()
    return favourite?.let {
        FunFact(
            guest.guestId,
            "${guest.firstName} liker hyttelivet mest ${it.inNorwegian} ($mostVisits av ${visits.happened.size} besøk)",
            FunFactPriority.FAVOURITE_SEASON,
        )
    }.takeIf { visits.happened.size >= MIN_VISITS_FOR_FAVOURITE_SEASON }
}

/** Seasons by calendar month, as the meteorological seasons: winter is December to February and so on. */
private enum class Season(val inNorwegian: String) {
    WINTER("om vinteren"),
    SPRING("om våren"),
    SUMMER("om sommeren"),
    AUTUMN("om høsten"),
    ;

    companion object {
        fun of(month: Month): Season = when (month) {
            Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> WINTER
            Month.MARCH, Month.APRIL, Month.MAY -> SPRING
            Month.JUNE, Month.JULY, Month.AUGUST -> SUMMER
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> AUTUMN
        }
    }
}

private fun longestStay(guest: GuestStats, visits: ProfileVisits): FunFact? {
    val longest =
        visits.happened.maxWithOrNull(compareBy<GuestVisit> { it.days }.thenBy { it.startDate }) ?: return null
    return FunFact(
        guest.guestId,
        "Det lengste oppholdet til ${guest.firstName} var ${longest.days} dager " +
            "(${longest.startDate.norwegianShortMonthYear()})",
        FunFactPriority.GUEST_LONGEST_STAY,
    ).takeIf { longest.days >= MIN_DAYS_FOR_LONGEST_STAY }
}

private fun totalTimeInWords(guest: GuestStats): FunFact? {
    val days = guest.allTime.totalDays
    return FunFact(
        guest.guestId,
        "${guest.firstName} har tilbrakt $days dager på hytta, det er ca. ${daysInWords(days)}",
        FunFactPriority.TOTAL_TIME_IN_WORDS,
    ).takeIf { days >= MIN_DAYS_FOR_TOTAL_TIME }
}

/** Weeks below two months, months below two years, otherwise years with one decimal. */
private fun daysInWords(days: Int): String = when {
    days < MAX_DAYS_IN_WEEKS -> "${days / DAYS_PER_WEEK} uker"
    days < MAX_DAYS_IN_MONTHS -> "${days / DAYS_PER_MONTH} måneder"
    else -> "${(days.toDouble() / DAYS_PER_YEAR).round1().toNorwegianDecimal()} år"
}

private fun recordYear(guest: GuestStats, visits: ProfileVisits, today: LocalDate): FunFact? {
    val visitsByYear = visits.happened.groupingBy { it.startDate.year }.eachCount()
    val mostVisits = visitsByYear.values.maxOrNull() ?: 0
    val bestYear = visitsByYear.filterValues { it == mostVisits }.keys.singleOrNull()
    return bestYear?.let {
        FunFact(
            guest.guestId,
            "Rekordåret til ${guest.firstName} var $it med $mostVisits besøk",
            FunFactPriority.RECORD_YEAR,
        )
    }.takeIf { visitsByYear.size >= MIN_YEARS_FOR_RECORD_YEAR && bestYear != today.year }
}

/** Prefers other guests over family, since a family member is the likeliest companion of almost everyone. */
private fun companions(guest: GuestStats, companions: List<Companion>): FunFact? {
    val shared = companions.filter { it.sharedVisits >= MIN_SHARED_VISITS }
    val candidates = shared.filter { !it.isFamily }.ifEmpty { shared }
    val mostVisits = candidates.maxOfOrNull { it.sharedVisits } ?: return null
    val top = candidates.filter { it.sharedVisits == mostVisits }.map { it.firstName }.sorted()
    return FunFact(
        guest.guestId,
        "Oftest på hytta sammen med ${top.joinToString(" og ")} ($mostVisits ganger)",
        FunFactPriority.COMPANIONS,
    ).takeIf { top.size <= MAX_TOP_COMPANIONS }
}

private fun differentCompanions(guest: GuestStats, companions: List<Companion>): FunFact? = FunFact(
    guest.guestId,
    "${guest.firstName} har delt hytta med ${companions.size} ulike gjester",
    FunFactPriority.DIFFERENT_COMPANIONS,
).takeIf { companions.size >= MIN_DIFFERENT_COMPANIONS }

/** Second and third place among the guests (family excluded) when the leader is close. */
private fun rankAmongGuests(guest: GuestStats, standings: List<GuestStanding>): FunFact? {
    val rank = guest.allTime.visitsRankAmongGuests ?: return null
    val guests = standings.filter { !it.isFamily }
    val leaderVisits = guests.maxOfOrNull { it.allTimeVisits } ?: 0
    val leaders = guests.filter { it.allTimeVisits == leaderVisits }.map { it.firstName }.sorted()
    val visitsBehind = leaderVisits - guest.allTime.totalVisits
    return FunFact(
        guest.guestId,
        "${guest.firstName} er nr. $rank blant gjestene, bare $visitsBehind besøk bak ${leaders.joinToString(" og ")}",
        FunFactPriority.RANK_AMONG_GUESTS,
    ).takeIf { rank in 2..MAX_RANK_AMONG_GUESTS && visitsBehind in 1..MAX_VISITS_BEHIND_LEADER }
}
