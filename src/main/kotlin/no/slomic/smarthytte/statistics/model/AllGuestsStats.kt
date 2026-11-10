package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@Serializable
data class AllGuestsStats(
    @JsonSchema.Description("Top 10 guests ranked by total number of reservation visits across all years.")
    val topGuestsByVisits: List<GuestRanking>,
    @JsonSchema.Description("Top 10 guests ranked by total number of days spent at the cabin across all years.")
    val topGuestsByDays: List<GuestRanking>,
    @JsonSchema.Description("Gender distribution across all registered guests.")
    val genderDistribution: GenderDistribution,
    @JsonSchema.Description("Age group breakdown of all registered guests, using current age at time of the request.")
    val ageGroups: List<AgeGroup>,
)

@Serializable
data class GuestRanking(
    @JsonSchema.Description("Internal unique identifier of the guest.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val guestId: String,
    @JsonSchema.Description("Guest's first name.")
    @JsonSchema.Example("\"Ola\"")
    val firstName: String,
    @JsonSchema.Description("Guest's last name.")
    @JsonSchema.Example("\"Nordmann\"")
    val lastName: String,
    @JsonSchema.Description("Total number of reservations the guest was part of across all years.")
    @JsonSchema.Example("5")
    val totalVisits: Int,
    @JsonSchema.Description("Total number of days the guest was present at the cabin across all years.")
    @JsonSchema.Example("14")
    val totalDays: Int,
)

@Serializable
data class GenderDistribution(
    @JsonSchema.Description("Number of male guests among all registered guests.")
    @JsonSchema.Example("15")
    val maleCount: Int,
    @JsonSchema.Description("Number of female guests among all registered guests.")
    @JsonSchema.Example("14")
    val femaleCount: Int,
    @JsonSchema.Description("Percentage of male guests (maleCount / total * 100), rounded to one decimal.")
    @JsonSchema.Example("51.7")
    val malePercent: Double,
    @JsonSchema.Description("Percentage of female guests (femaleCount / total * 100), rounded to one decimal.")
    @JsonSchema.Example("48.3")
    val femalePercent: Double,
)

@Serializable
data class AgeGroup(
    @JsonSchema.Description("Age range label (e.g. \"0-12\", \"13-17\", \"18-30\", \"31-50\", \"51+\").")
    @JsonSchema.Example("\"18-30\"")
    val label: String,
    @JsonSchema.Description("Number of registered guests whose current age falls within this range.")
    @JsonSchema.Example("6")
    val guestCount: Int,
    @JsonSchema.Description("Percentage of all registered guests in this age group, rounded to one decimal.")
    @JsonSchema.Example("20.7")
    val percent: Double,
)
