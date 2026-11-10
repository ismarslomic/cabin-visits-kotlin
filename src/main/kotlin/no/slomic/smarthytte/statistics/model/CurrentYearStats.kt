@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@Serializable
data class CurrentYearStats(
    @JsonSchema.Description("The calendar year this summary covers.")
    @JsonSchema.Example("2026")
    val year: Int,
    @JsonSchema.Description("Number of reservations that started in this calendar year.")
    @JsonSchema.Example("12")
    val visits: Int,
    @JsonSchema.Description(
        "Total nights spent at the cabin during this calendar year, counting only nights within the year boundary.",
    )
    @JsonSchema.Example("10")
    val totalNights: Int,
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    @JsonSchema.Example("2476.3")
    val totalDistanceKm: Double?,
)
