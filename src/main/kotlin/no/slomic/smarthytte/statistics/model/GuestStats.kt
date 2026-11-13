@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@JsonSchema.Description(
    "Statistics for one guest as of today, counted with the guest's own stays (a guest's own dates, or the " +
        "reservation period when the guest has no own dates) that have started. Used for the guests of the current " +
        "and next reservation in the live stats and for the guest profile.",
)
@Serializable
data class GuestStats(
    @JsonSchema.Description("Internal unique identifier of the guest.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val guestId: String,
    @JsonSchema.Description("Guest's first name.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("Guest's last name.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("Guest's age in the current year (current year - birthYear).")
    @JsonSchema.Example("38")
    val age: Int,
    @JsonSchema.Description("True if the guest is a family member (owner of the cabin), false for other guests.")
    @JsonSchema.Example("true")
    val isFamily: Boolean,
    @JsonSchema.Description(
        "Relative URL to the guest's avatar image (JPEG), see GET /api/guests/{guestId}/avatar. " +
            "Null if the guest has no avatar.",
    )
    @JsonSchema.Example("\"/api/guests/ola.nordmann/avatar?v=1767225600000\"")
    val avatarUrl: String?,
    @JsonSchema.Description(
        "True if the guest has no stay that started before this one (guest's own arrival date). In a guest " +
            "profile this is true only when the guest is at the cabin right now and the ongoing visit is the " +
            "first one.",
    )
    @JsonSchema.Example("false")
    val isFirstVisit: Boolean,
    @JsonSchema.Description(
        "Own arrival date of the guest's first started stay. Null if the guest has no started stays.",
    )
    @JsonSchema.Example("\"2019-02-16\"")
    val firstVisitDate: LocalDate?,
    @JsonSchema.Description(
        "Own departure date of the guest's last completed stay before this one. Null if there is no completed stay. " +
            "In a guest profile: the last visit before the current one if the guest is at the cabin right now, " +
            "otherwise the last completed visit.",
    )
    @JsonSchema.Example("\"2026-04-05\"")
    val lastVisitDate: LocalDate?,
    @JsonSchema.Description("Years in which the guest has arrived (own arrival date), in descending order.")
    @JsonSchema.Example("[2026, 2025, 2023]")
    val yearsVisited: List<Int>,
    val currentYear: GuestPeriodStats,
    val allTime: GuestPeriodStats,
)

@JsonSchema.Description(
    "Guest statistics for one period (the current year or all years), counted with the guest's own stays that " +
        "have started, up to today (falls back to the reservation period when the guest has no own dates).",
)
@Serializable
data class GuestPeriodStats(
    @JsonSchema.Description("Number of stays of the guest that arrived in the period (guest's own arrival date).")
    @JsonSchema.Example("5")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Number of days of the guest's own stay within the period, up to and including today.",
    )
    @JsonSchema.Example("14")
    val totalDays: Int,
    @JsonSchema.Description(
        "Number of nights of the guest's own stays that arrived in the period (the whole stay, also for an " +
            "ongoing stay). Same counting as totalVisits.",
    )
    @JsonSchema.Example("10")
    val totalNights: Int,
    @JsonSchema.Description(
        "Guest's rank by total visits among all guests, family included, in the period (1 = most). " +
            "Tied guests share rank. Null if no visits.",
    )
    @JsonSchema.Example("3")
    val visitsRank: Int?,
    @JsonSchema.Description(
        "Guest's rank by total days among all guests, family included, in the period (1 = most). " +
            "Tied guests share rank. Null if no visits.",
    )
    @JsonSchema.Example("2")
    val daysRank: Int?,
    @JsonSchema.Description(
        "Guest's rank by total visits among guests only, family members excluded (1 = most). " +
            "Tied guests share rank. Null for family members and if no visits.",
    )
    @JsonSchema.Example("2")
    val visitsRankAmongGuests: Int?,
    @JsonSchema.Description(
        "Guest's rank by total days among guests only, family members excluded (1 = most). " +
            "Tied guests share rank. Null for family members and if no visits.",
    )
    @JsonSchema.Example("1")
    val daysRankAmongGuests: Int?,
)
