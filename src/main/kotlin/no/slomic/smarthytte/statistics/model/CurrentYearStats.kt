@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@Serializable
data class CurrentYearStats(
    @JsonSchema.Description("The calendar year this summary covers.")
    val year: Int,
    @JsonSchema.Description("Number of reservations that started in this calendar year.")
    val visits: Int,
    @JsonSchema.Description(
        "Total nights spent at the cabin during this calendar year, counting only nights within the year boundary.",
    )
    val totalNights: Int,
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val totalDistanceKm: Double?,
)
