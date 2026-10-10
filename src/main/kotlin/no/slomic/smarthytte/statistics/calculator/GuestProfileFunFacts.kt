package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.GuestStats
import no.slomic.smarthytte.statistics.model.GuestVisit
import no.slomic.smarthytte.statistics.model.VisitStatus

// Favourite season: minimum number of visits to have a favourite
private const val MIN_VISITS_FOR_FAVOURITE_SEASON = 3

/** Everything besides the guest and the visits that the guest profile fun facts depend on. */
data class GuestProfileFunFactContext(val today: LocalDate)

/**
 * Fun facts for the guest profile, written about the guest by first name like the live fun facts, sorted by priority
 * (highest first). Every fact whose threshold holds is included, there is no cap.
 */
fun calculateGuestProfileFunFacts(
    guest: GuestStats,
    visits: List<GuestVisit>,
    context: GuestProfileFunFactContext,
): List<FunFact> {
    val happenedVisits = visits.filter { it.status != VisitStatus.UPCOMING }
    return listOfNotNull(
        shareOfLife(guest),
        loyalGuest(guest, happenedVisits, context.today),
        favouriteSeason(guest, happenedVisits),
    ).sortedWith(compareByDescending<FunFact> { it.priority }.thenBy { it.text })
}

/**
 * Years in a row with a visit, counted backwards from the last year with a visit. The streak is only active if that
 * year is this year or last year. Upcoming visits are not included, they have not happened yet.
 */
private fun loyalGuest(guest: GuestStats, happenedVisits: List<GuestVisit>, today: LocalDate): FunFact? {
    val yearsVisited = happenedVisits.map { it.startDate.year }.distinct()
    val lastYear = yearsVisited.maxOrNull()?.takeIf { it >= today.year - 1 } ?: return null
    val years = consecutiveYearsUpTo(lastYear, yearsVisited)
    return FunFact(
        guest.guestId,
        "${guest.firstName} er en trofast gjest og har besøkt hytta de siste $years årene",
        FunFactPriority.VISIT_STREAK,
    ).takeIf { !guest.isFamily && years >= MIN_YEARS_FOR_STREAK }
}

private fun favouriteSeason(guest: GuestStats, happenedVisits: List<GuestVisit>): FunFact? {
    val visitsBySeason = happenedVisits.groupingBy { Season.of(it.startDate.month) }.eachCount()
    val mostVisits = visitsBySeason.values.maxOrNull() ?: 0
    val favourite = visitsBySeason.filterValues { it == mostVisits }.keys.singleOrNull()
    return favourite?.let {
        FunFact(
            guest.guestId,
            "${guest.firstName} liker hyttelivet mest ${it.inNorwegian} ($mostVisits av ${happenedVisits.size} besøk)",
            FunFactPriority.FAVOURITE_SEASON,
        )
    }.takeIf { happenedVisits.size >= MIN_VISITS_FOR_FAVOURITE_SEASON }
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
