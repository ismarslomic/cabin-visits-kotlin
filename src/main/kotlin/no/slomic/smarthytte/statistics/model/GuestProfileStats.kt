@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.reservations.VisitStatus

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
data class GuestVisit(
    @JsonSchema.Description(
        "First day of the guest's own stay (inclusive): the guest's own arrival date, or the check-in date of the reservation when the guest has no own dates.",
    )
    @JsonSchema.Example("\"2026-07-10\"")
    val startDate: LocalDate,
    @JsonSchema.Description(
        "Last day of the guest's own stay: the guest's own departure date, or the check-out date of the reservation when the guest has no own dates. Same semantics as the end date of a reservation.",
    )
    @JsonSchema.Example("\"2026-07-12\"")
    val endDate: LocalDate,
    @JsonSchema.Description("Number of calendar days of the guest's own stay, including arrival and departure day.")
    @JsonSchema.Example("3")
    val days: Int,
    @JsonSchema.Description("Number of nights of the guest's own stay.")
    @JsonSchema.Example("2")
    val nights: Int,
    @JsonSchema.Description(
        "Where the guest's own stay stands today: COMPLETED (the guest has left), ONGOING (the guest is at the " +
            "cabin) or UPCOMING (the guest has not arrived yet, also when the reservation has started).",
    )
    @JsonSchema.Example("\"ONGOING\"")
    val status: VisitStatus,
)
