@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class LiveStats(
    @JsonSchema.Description("True if the cabin is currently occupied (a reservation has started but not yet ended).")
    val isOccupied: Boolean,
    @JsonSchema.Description("Details about the ongoing reservation. Null if the cabin is not currently occupied.")
    val currentReservation: CurrentReservationInfo?,
    @JsonSchema.Description("Details about the next upcoming reservation. Null if no future reservation exists.")
    val nextReservation: NextReservationInfo?,
    @JsonSchema.Description("Total number of reservations ever recorded, across all years.")
    val allTimeVisits: Int,
    @JsonSchema.Description("Total number of nights spent at the cabin across all reservations ever recorded.")
    val allTimeNights: Int,
    @JsonSchema.Description("Total number of unique guests registered in the system.")
    val allTimeUniqueGuests: Int,
)

@Serializable
data class CurrentReservationInfo(
    @JsonSchema.Description("First day of the current reservation (check-in date, inclusive).")
    val startDate: LocalDate,
    @JsonSchema.Description(
        "Last day of the current reservation (check-out date, exclusive — cabin is free from this date).",
    )
    val endDate: LocalDate,
    @JsonSchema.Description("First names of guests in the current reservation.")
    val guestNames: List<String>,
    @JsonSchema.Description("Number of nights remaining in the current reservation, from today until check-out.")
    val remainingNights: Int,
)

@Serializable
data class NextReservationInfo(
    @JsonSchema.Description("First day of the next reservation (check-in date, inclusive).")
    val startDate: LocalDate,
    @JsonSchema.Description("Last day of the next reservation (check-out date, exclusive).")
    val endDate: LocalDate,
    @JsonSchema.Description("First names of guests in the next reservation.")
    val guestNames: List<String>,
    @JsonSchema.Description("Number of days from today until the next reservation starts.")
    val daysUntil: Int,
)
