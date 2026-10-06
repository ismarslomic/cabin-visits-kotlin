@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@JsonSchema.Description("Occupancy percentages for a period (year or month) across days, weeks and, for years, months.")
@Serializable
data class OccupancyStats(
    @JsonSchema.Description(
        "Percentage of calendar days in the period where the cabin was occupied, including the departure day. E.g. 25.0 means 25%.",
    )
    val dayOccupancy: Double,
    @JsonSchema.Description(
        "Percentage of ISO weeks in the period that contained at least one occupied day. E.g. 50.0 means half of all weeks had at least one visit.",
    )
    val weekOccupancy: Double,
    @JsonSchema.Description(
        "Percentage of the 12 calendar months in the year that contained at least one occupied day. Only present in yearly statistics, null in monthly context.",
    )
    val monthOccupancy: Double? = null,
)
