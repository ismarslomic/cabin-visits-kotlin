package no.slomic.smarthytte.statistics.calculator

import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.GuestStats
import no.slomic.smarthytte.statistics.model.NextReservationInfo

// Arrival soon (today, tomorrow, the day after) and countdown for later arrivals
private const val ARRIVAL_SOON_MAX_DAYS = 2

/**
 * Fun facts about the next reservation, sorted by priority (highest first). [isOccupied] tells whether a stay is
 * in progress, in which case a countdown to the next visit is relevant.
 */
fun calculateNextVisitFunFacts(next: NextReservationInfo, isOccupied: Boolean): List<FunFact> = (
    listOfNotNull(arrivalSoon(next.daysUntil), countdownToNextVisit(next.daysUntil, isOccupied)) +
        next.guests.flatMap { listOfNotNull(nextFirstTimer(it), nextVisitMilestone(it)) }
    ).sortedByDescending { it.priority }

private fun arrivalSoon(daysUntil: Int): FunFact? {
    val day = when (daysUntil) {
        0 -> "i dag"
        1 -> "i morgen"
        else -> "i overmorgen"
    }
    return funFact(null, FunFactPriority.ARRIVAL_SOON) {
        text("Neste besøk starter ")
        stat(day)
    }.takeIf { daysUntil in 0..ARRIVAL_SOON_MAX_DAYS }
}

private fun countdownToNextVisit(daysUntil: Int, isOccupied: Boolean): FunFact? =
    funFact(null, FunFactPriority.COUNTDOWN_TO_NEXT_VISIT) {
        text("Neste besøk om ")
        stat("$daysUntil dager")
    }.takeIf { isOccupied && daysUntil > ARRIVAL_SOON_MAX_DAYS }

private fun nextFirstTimer(guest: GuestStats): FunFact? = funFact(guest.guestId, FunFactPriority.NEXT_FIRST_TIMER) {
    guestName(guest.firstName)
    text(" kommer på hytta for første gang! 🎉")
}.takeIf { !guest.isFamily && guest.isFirstVisit }

/** The visit is already counted for started reservations only, so the next visit is `totalVisits + 1`. */
private fun nextVisitMilestone(guest: GuestStats): FunFact? {
    val visits = guest.allTime.totalVisits + 1
    return funFact(guest.guestId, FunFactPriority.NEXT_VISIT_MILESTONE) {
        text("Neste besøk blir besøk nr. ")
        stat(visits)
        text(" for ")
        guestName(guest.firstName)
    }.takeIf { isVisitMilestone(guest, visits) }
}
