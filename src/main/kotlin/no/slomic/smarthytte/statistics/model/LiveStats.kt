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
        "Last day of the current reservation (check-out date, exclusive — cabin is free from this date).",
    )
    val endDate: LocalDate,
    @JsonSchema.Description("Statistics for each guest in the current reservation, ordered by current year days rank.")
    val guests: List<LiveGuestStats>,
    @JsonSchema.Description("Number of nights remaining in the current reservation, from today until check-out.")
    val remainingNights: Int,
)

@Serializable
data class NextReservationInfo(
    @JsonSchema.Description("First day of the next reservation (check-in date, inclusive).")
    val startDate: LocalDate,
    @JsonSchema.Description("Last day of the next reservation (check-out date, exclusive).")
    val endDate: LocalDate,
    @JsonSchema.Description("Statistics for each guest in the next reservation, ordered by current year days rank.")
    val guests: List<LiveGuestStats>,
    @JsonSchema.Description("Number of days from today until the next reservation starts.")
    val daysUntil: Int,
)

@Serializable
data class LiveGuestStats(
    @JsonSchema.Description("Internal unique identifier of the guest.")
    val guestId: String,
    @JsonSchema.Description("Guest's first name.")
    val firstName: String,
    @JsonSchema.Description("Guest's last name.")
    val lastName: String,
    @JsonSchema.Description("Guest's age in the current year (current year - birthYear).")
    val age: Int,
    @JsonSchema.Description("True if the guest is a family member (owner of the cabin), false for other guests.")
    val isFamily: Boolean,
    @JsonSchema.Description(
        "Relative URL to the guest's avatar image (JPEG), see GET /api/guests/{guestId}/avatar. " +
            "Null if the guest has no avatar.",
    )
    val avatarUrl: String?,
    @JsonSchema.Description("True if the guest has no reservations that started before this reservation.")
    val isFirstVisit: Boolean,
    @JsonSchema.Description(
        "Start date of the guest's first started reservation. Null if the guest has no started reservations.",
    )
    val firstVisitDate: LocalDate?,
    @JsonSchema.Description(
        "End date of the guest's last completed visit before this reservation. Null if there is no completed visit.",
    )
    val lastVisitDate: LocalDate?,
    @JsonSchema.Description("Years in which the guest has started reservations, in descending order.")
    val yearsVisited: List<Int>,
    @JsonSchema.Description("Guest statistics for the current year, counting only started reservations up to today.")
    val currentYear: GuestPeriodStats,
    @JsonSchema.Description("Guest statistics across all years, counting only started reservations up to today.")
    val allTime: GuestPeriodStats,
)

@Serializable
data class GuestPeriodStats(
    @JsonSchema.Description("Number of reservations the guest was part of that started in the period (arrival date).")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Number of days the guest was present at the cabin in the period, up to and including today.",
    )
    val totalDays: Int,
    @JsonSchema.Description(
        "Guest's rank by total visits among all guests in the period (1 = most). Tied guests share rank. Null if no visits.",
    )
    val visitsRank: Int?,
    @JsonSchema.Description(
        "Guest's rank by total days among all guests in the period (1 = most). Tied guests share rank. Null if no visits.",
    )
    val daysRank: Int?,
)
