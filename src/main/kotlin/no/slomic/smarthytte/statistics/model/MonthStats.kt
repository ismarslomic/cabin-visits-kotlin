@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.MonthDrivingMomentStats
import no.slomic.smarthytte.statistics.calculator.MonthDrivingTimeStats

@Serializable
data class MonthStats(
    @JsonSchema.Description("The calendar year this month belongs to.")
    val year: Int,
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    val monthName: String,
    @JsonSchema.Description("Visit count for this month with comparisons against other reference periods.")
    val visits: MonthVisitsStats,
    @JsonSchema.Description("Occupied-day statistics for this month (including departure day).")
    val days: MonthDaysStats,
    @JsonSchema.Description("Occupied-night statistics for this month (excluding departure day).")
    val nights: MonthNightsStats,
    @JsonSchema.Description("Occupancy percentages for this month across days and weeks.")
    val occupancy: OccupancyStats,
    @JsonSchema.Description(
        "All guests present in this month, sorted by total days descending. Visits count reservations that started in this month, days count days within this month.",
    )
    val guests: List<GuestVisitStats>,
    @JsonSchema.Description(
        "Driving distance statistics for trips to, from, and at the cabin in this month. Null if no vehicle trip data exists.",
    )
    val drivingDistance: MonthDrivingDistanceStats?,
    @JsonSchema.Description(
        "Driving duration statistics for trips to and from the cabin in this month. Null if no vehicle trip data exists.",
    )
    val drivingTime: MonthDrivingTimeStats?,
    @JsonSchema.Description(
        "Average departure and arrival clock times for trips in this month, expressed in Oslo timezone. Null if no vehicle trip data exists.",
    )
    val drivingMoments: MonthDrivingMomentStats?,
)

@Serializable
data class MonthVisitsStats(
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
data class MonthDaysStats(
    @JsonSchema.Description(
        "Total number of occupied days in this month across all reservations, counting overlapping reservations only once per day. Departure day is counted as an occupied day.",
    )
    val totalDays: Int,
    @JsonSchema.Description(
        "Shortest stay in days (full duration, arrival and departure day included) among reservations that started in this month. Null if no reservations started this month.",
    )
    val minDays: Int?,
    @JsonSchema.Description(
        "Longest stay in days (full duration, arrival and departure day included) among reservations that started in this month. Null if no reservations started this month.",
    )
    val maxDays: Int?,
    @JsonSchema.Description(
        "Average stay in days (full duration, arrival and departure day included) among reservations that started in this month. Null if no reservations started this month.",
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
data class MonthNightsStats(
    @JsonSchema.Description(
        "Total number of occupied nights in this month across all reservations, counting overlapping reservations only once per night. Departure night is not counted.",
    )
    val totalNights: Int,
    @JsonSchema.Description(
        "Shortest stay in nights (full duration) among reservations that started in this month. Null if no reservations started this month.",
    )
    val minNights: Int?,
    @JsonSchema.Description(
        "Longest stay in nights (full duration) among reservations that started in this month. Null if no reservations started this month.",
    )
    val maxNights: Int?,
    @JsonSchema.Description(
        "Average stay in nights (full duration) among reservations that started in this month. Null if no reservations started this month.",
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

@Serializable
data class MonthDrivingDistanceStats(
    @JsonSchema.Description("Total driving distance in kilometres for all trips to the cabin in this month.")
    val totalToCabinKm: Double,
    @JsonSchema.Description("Shortest single trip distance to the cabin in km this month. Null if no trips exist.")
    val minToCabinKm: Double?,
    @JsonSchema.Description("Longest single trip distance to the cabin in km this month. Null if no trips exist.")
    val maxToCabinKm: Double?,
    @JsonSchema.Description("Average trip distance to the cabin in km this month. Null if no trips exist.")
    val avgToCabinKm: Double?,
    @JsonSchema.Description("Total driving distance in kilometres for all trips from the cabin in this month.")
    val totalFromCabinKm: Double,
    @JsonSchema.Description("Shortest single trip distance from the cabin in km this month. Null if no trips exist.")
    val minFromCabinKm: Double?,
    @JsonSchema.Description("Longest single trip distance from the cabin in km this month. Null if no trips exist.")
    val maxFromCabinKm: Double?,
    @JsonSchema.Description("Average trip distance from the cabin in km this month. Null if no trips exist.")
    val avgFromCabinKm: Double?,
    @JsonSchema.Description("Total driving distance in kilometres for all trips at (around) the cabin in this month.")
    val totalAtCabinKm: Double,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all toCabin legs, for the fastest reservation trip to the cabin this month. Null if no trips exist.",
    )
    val minAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all toCabin legs, for the slowest reservation trip to the cabin this month. Null if no trips exist.",
    )
    val maxAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Mean of avgSpeedKmh across all reservations with a toCabin trip this month. Null if no trips exist.",
    )
    val avgAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all fromCabin legs, for the fastest reservation trip from the cabin this month. Null if no trips exist.",
    )
    val minAvgSpeedFromCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all fromCabin legs, for the slowest reservation trip from the cabin this month. Null if no trips exist.",
    )
    val maxAvgSpeedFromCabinKmh: Double?,
    @JsonSchema.Description(
        "Mean of avgSpeedKmh across all reservations with a fromCabin trip this month. Null if no trips exist.",
    )
    val avgAvgSpeedFromCabinKmh: Double?,
)
