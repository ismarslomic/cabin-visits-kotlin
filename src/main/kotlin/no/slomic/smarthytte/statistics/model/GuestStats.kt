@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class GuestStats(
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
    val currentYear: GuestPeriodStats,
    val allTime: GuestPeriodStats,
)

@JsonSchema.Description(
    "Guest statistics for one period (the current year or all years), counting only started reservations up to today.",
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
        "Number of nights of the reservations the guest was part of that started in the period (full stay, " +
            "also for an ongoing stay). Same counting as totalVisits.",
    )
    val totalNights: Int,
    @JsonSchema.Description(
        "Guest's rank by total visits among all guests, family included, in the period (1 = most). " +
            "Tied guests share rank. Null if no visits.",
    )
    val visitsRank: Int?,
    @JsonSchema.Description(
        "Guest's rank by total days among all guests, family included, in the period (1 = most). " +
            "Tied guests share rank. Null if no visits.",
    )
    val daysRank: Int?,
    @JsonSchema.Description(
        "Guest's rank by total visits among guests only, family members excluded (1 = most). " +
            "Tied guests share rank. Null for family members and if no visits.",
    )
    val visitsRankAmongGuests: Int?,
    @JsonSchema.Description(
        "Guest's rank by total days among guests only, family members excluded (1 = most). " +
            "Tied guests share rank. Null for family members and if no visits.",
    )
    val daysRankAmongGuests: Int?,
)
