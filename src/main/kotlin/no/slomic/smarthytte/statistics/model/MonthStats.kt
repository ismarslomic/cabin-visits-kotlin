@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.DrivingMomentStatsMonth
import no.slomic.smarthytte.statistics.calculator.DrivingTimeStatsMonth

@Serializable
data class MonthStats(
    @JsonSchema.Description("The calendar year this month belongs to.")
    val year: Int,
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    val monthName: String,
    @JsonSchema.Description("Visit count for this month with comparisons against other reference periods.")
    val visits: VisitsStats,
    @JsonSchema.Description("Occupied-day statistics for this month (including departure day).")
    val days: DaysStats,
    @JsonSchema.Description("Occupied-night statistics for this month (excluding departure day).")
    val nights: NightsStats,
    @JsonSchema.Description("Occupancy percentages for this month across days and weeks.")
    val occupancy: OccupancyStats,
    @JsonSchema.Description("All guests present in this month, sorted by total days descending.")
    val guests: List<GuestVisitStats>,
    @JsonSchema.Description(
        "Driving duration statistics for trips to and from the cabin in this month. Null if no vehicle trip data exists.",
    )
    val drivingTime: DrivingTimeStatsMonth?,
    @JsonSchema.Description(
        "Average departure and arrival clock times for trips in this month, expressed in Oslo timezone. Null if no vehicle trip data exists.",
    )
    val drivingMoments: DrivingMomentStatsMonth?,
)

@Serializable
data class VisitsStats(
    @JsonSchema.Description("Number of reservations that started in this month.")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Difference between this month's visit count and the previous calendar month's visit count. Positive means more visits than last month.",
    )
    val comparedToPreviousMonth: Int,
    @JsonSchema.Description(
        "Difference between this month's visit count and the same month in the previous year. Positive means more visits than the same month last year.",
    )
    val comparedToSameMonthLastYear: Int,
    @JsonSchema.Description(
        "Difference between this month's visit count and the year-to-date monthly average (sum of visits Jan–this month divided by number of months so far). Positive means above average.",
    )
    val comparedToYearToDateAverage: Double,
)

@Serializable
data class DaysStats(
    @JsonSchema.Description(
        "Total number of occupied days in this month across all reservations, counting overlapping reservations only once per day. Departure day is counted as an occupied day.",
    )
    val totalDays: Int,
    @JsonSchema.Description(
        "Minimum number of days any single reservation overlapped with this month. Null if no reservations overlap.",
    )
    val minDays: Int?,
    @JsonSchema.Description(
        "Maximum number of days any single reservation overlapped with this month. Null if no reservations overlap.",
    )
    val maxDays: Int?,
    @JsonSchema.Description(
        "Average number of days per reservation overlapping this month. Null if no reservations overlap.",
    )
    val avgDays: Double?,
    @JsonSchema.Description(
        "Difference between totalDays for this month and totalDays for the previous calendar month. Positive means more occupied days than last month.",
    )
    val comparedToPreviousMonth: Int,
    @JsonSchema.Description(
        "Difference between totalDays for this month and totalDays for the same month in the previous year. Positive means more occupied days than the same month last year.",
    )
    val comparedToSameMonthLastYear: Int,
)

@Serializable
data class NightsStats(
    @JsonSchema.Description(
        "Total number of occupied nights in this month across all reservations, counting overlapping reservations only once per night. Departure night is not counted.",
    )
    val totalNights: Int,
    @JsonSchema.Description(
        "Minimum number of nights any single reservation overlapped with this month. Null if no reservations overlap.",
    )
    val minNights: Int?,
    @JsonSchema.Description(
        "Maximum number of nights any single reservation overlapped with this month. Null if no reservations overlap.",
    )
    val maxNights: Int?,
    @JsonSchema.Description(
        "Average number of nights per reservation overlapping this month. Null if no reservations overlap.",
    )
    val avgNights: Double?,
    @JsonSchema.Description(
        "Difference between totalNights for this month and totalNights for the previous calendar month. Positive means more occupied nights than last month.",
    )
    val comparedToPreviousMonth: Int,
    @JsonSchema.Description(
        "Difference between totalNights for this month and totalNights for the same month in the previous year. Positive means more occupied nights than the same month last year.",
    )
    val comparedToSameMonthLastYear: Int,
)
