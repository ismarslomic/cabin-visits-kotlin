package no.slomic.smarthytte.statistics.model

import kotlinx.serialization.Serializable

@Serializable
data class OccupancyStats(
    /** Percentage of calendar days occupied (including departure day) out of total days in the period. **/
    val dayOccupancy: Double,
    /** Percentage of ISO weeks with at least one occupied day out of total weeks in the period. **/
    val weekOccupancy: Double,
    /** Percentage of months with at least one occupied day out of 12. Null when used in MonthStats context. **/
    val monthOccupancy: Double? = null,
)
