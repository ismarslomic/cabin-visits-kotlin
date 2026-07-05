package no.slomic.smarthytte.statistics.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class LiveStatsResponse(
    val isOccupied: Boolean,
    val currentReservation: CurrentReservationInfo?,
    val nextReservation: NextReservationInfo?,
    val allTimeVisits: Int,
    val allTimeStayDays: Int,
    val allTimeUniqueGuests: Int,
)

@Serializable
data class CurrentReservationInfo(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val guestNames: List<String>,
    val remainingNights: Int,
)

@Serializable
data class NextReservationInfo(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val guestNames: List<String>,
    val daysUntil: Int,
)
