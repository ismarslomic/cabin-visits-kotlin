package no.slomic.smarthytte.statistics.calculator

import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.LiveGuestStats
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
    val text = when (daysUntil) {
        0 -> "Neste besøk starter i dag"
        1 -> "Neste besøk starter i morgen"
        else -> "Neste besøk starter i overmorgen"
    }
    return FunFact(null, text, FunFactPriority.ARRIVAL_SOON).takeIf { daysUntil in 0..ARRIVAL_SOON_MAX_DAYS }
}

private fun countdownToNextVisit(daysUntil: Int, isOccupied: Boolean): FunFact? =
    FunFact(null, "Neste besøk om $daysUntil dager", FunFactPriority.COUNTDOWN_TO_NEXT_VISIT)
        .takeIf { isOccupied && daysUntil > ARRIVAL_SOON_MAX_DAYS }

private fun nextFirstTimer(guest: LiveGuestStats): FunFact? =
    FunFact(guest.guestId, "${guest.firstName} kommer på hytta for første gang! 🎉", FunFactPriority.NEXT_FIRST_TIMER)
        .takeIf { !guest.isFamily && guest.isFirstVisit }

/** The visit is already counted for started reservations only, so the next visit is `totalVisits + 1`. */
private fun nextVisitMilestone(guest: LiveGuestStats): FunFact? {
    val visits = guest.allTime.totalVisits + 1
    return FunFact(
        guest.guestId,
        "Neste besøk blir besøk nr. $visits for ${guest.firstName}",
        FunFactPriority.NEXT_VISIT_MILESTONE,
    ).takeIf { isVisitMilestone(guest, visits) }
}
