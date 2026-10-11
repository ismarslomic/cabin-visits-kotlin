@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class GuestProfileStats(
    val guest: GuestStats,
    @JsonSchema.Description(
        "All visits of the guest, historical, ongoing and upcoming, oldest first. Suitable for a timeline.",
    )
    val visits: List<GuestVisit>,
    @JsonSchema.Description(
        "Fun facts about the guest, as ready-to-display Norwegian text that mentions the guest by first name. " +
            "Sorted by priority, highest first. Not capped, so pick the ones to display.",
    )
    val funFacts: List<FunFact>,
)

@Serializable
enum class VisitStatus { COMPLETED, ONGOING, UPCOMING }

@Serializable
data class GuestVisit(
    @JsonSchema.Description("First day of the visit (check-in date, inclusive).")
    val startDate: LocalDate,
    @JsonSchema.Description("Last day of the visit (check-out date). Same semantics as the end date of a reservation.")
    val endDate: LocalDate,
    @JsonSchema.Description("Number of calendar days of the whole visit, including arrival and departure day.")
    val days: Int,
    @JsonSchema.Description("Number of nights of the whole visit.")
    val nights: Int,
    @JsonSchema.Description(
        "Where the visit stands today: COMPLETED (the guest has left), ONGOING (the guest is at the cabin) or " +
            "UPCOMING (the visit has not started yet).",
    )
    val status: VisitStatus,
)
