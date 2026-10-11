@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class LiveStats(
    @JsonSchema.Description(
        "True if the cabin is currently occupied: a reservation has started and its check-out date has not passed, so also on the check-out day.",
    )
    val isOccupied: Boolean,
    @JsonSchema.Description("Details about the ongoing reservation. Null if the cabin is not currently occupied.")
    val currentReservation: CurrentReservationInfo?,
    @JsonSchema.Description("Details about the next upcoming reservation. Null if no future reservation exists.")
    val nextReservation: NextReservationInfo?,
    @JsonSchema.Description(
        "Total number of reservations that have started, across all years. Future bookings are excluded.",
    )
    val allTimeVisits: Int,
    @JsonSchema.Description(
        "Total number of nights across all reservations that have started. Future bookings are excluded.",
    )
    val allTimeNights: Int,
    @JsonSchema.Description("Total number of unique guests registered in the system.")
    val allTimeUniqueGuests: Int,
    @JsonSchema.Description(
        "Fun facts about the guests of the current reservation, sorted by priority. Empty if the cabin is not occupied.",
    )
    val guestFunFacts: List<FunFact>,
    @JsonSchema.Description("Fun facts about the cabin as a whole, sorted by priority.")
    val cabinFunFacts: List<FunFact>,
    @JsonSchema.Description(
        "Fun facts about the next upcoming reservation, sorted by priority. Empty if no future reservation exists.",
    )
    val nextVisitFunFacts: List<FunFact>,
)

@Serializable
data class CurrentReservationInfo(
    @JsonSchema.Description("First day of the current reservation (check-in date, inclusive).")
    val startDate: LocalDate,
    @JsonSchema.Description(
        "Last day of the current reservation (check-out date). The reservation is still current on this day, and the cabin is free the day after.",
    )
    val endDate: LocalDate,
    @JsonSchema.Description("Statistics for each guest in the current reservation, ordered by current year days rank.")
    val guests: List<GuestStats>,
    @JsonSchema.Description(
        "Number of nights remaining in the current reservation, from today until check-out. 0 on the check-out day.",
    )
    val remainingNights: Int,
)

@Serializable
data class NextReservationInfo(
    @JsonSchema.Description("First day of the next reservation (check-in date, inclusive).")
    val startDate: LocalDate,
    @JsonSchema.Description("Last day of the next reservation (check-out date, exclusive).")
    val endDate: LocalDate,
    @JsonSchema.Description("Statistics for each guest in the next reservation, ordered by current year days rank.")
    val guests: List<GuestStats>,
    @JsonSchema.Description("Number of days from today until the next reservation starts.")
    val daysUntil: Int,
)
