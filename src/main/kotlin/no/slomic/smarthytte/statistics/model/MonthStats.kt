@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.MonthDrivingMomentStats
import no.slomic.smarthytte.statistics.calculator.MonthDrivingTimeStats

@Serializable
data class MonthStats(
    @JsonSchema.Description("The calendar year this month belongs to.")
    @JsonSchema.Example("2026")
    val year: Int,
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    @JsonSchema.Example("7")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    @JsonSchema.Example("\"Juli\"")
    val monthName: String,
    @JsonSchema.Description("Visit count for this month with comparisons against other reference periods.")
    val visits: MonthVisitsStats,
    @JsonSchema.Description("Occupied-day statistics for this month (including departure day).")
    val days: MonthDaysStats,
    @JsonSchema.Description("Occupied-night statistics for this month (excluding departure day).")
    val nights: MonthNightsStats,
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
    @JsonSchema.Example("5")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Difference between this month's visit count and the previous calendar month's visit count. Positive means more visits than last month.",
    )
    @JsonSchema.Example("1")
    val comparedToPreviousMonth: Int,
    @JsonSchema.Description(
        "Difference between this month's visit count and the same month in the previous year. Positive means more visits than the same month last year.",
    )
    @JsonSchema.Example("-1")
    val comparedToSameMonthLastYear: Int,
    @JsonSchema.Description(
        "Difference between this month's visit count and the year-to-date monthly average (sum of visits Jan–this month divided by number of months so far). Positive means above average.",
    )
    @JsonSchema.Example("0.5")
    val comparedToYearToDateAverage: Double,
)

@Serializable
data class MonthDaysStats(
    @JsonSchema.Description(
        "Total number of occupied days in this month across all reservations, counting overlapping reservations only once per day. Departure day is counted as an occupied day.",
    )
    @JsonSchema.Example("14")
    val totalDays: Int,
    @JsonSchema.Description(
        "Shortest stay in days (full duration, arrival and departure day included) among reservations that started in this month. Null if no reservations started this month.",
    )
    @JsonSchema.Example("2")
    val minDays: Int?,
    @JsonSchema.Description(
        "Longest stay in days (full duration, arrival and departure day included) among reservations that started in this month. Null if no reservations started this month.",
    )
    @JsonSchema.Example("6")
    val maxDays: Int?,
    @JsonSchema.Description(
        "Average stay in days (full duration, arrival and departure day included) among reservations that started in this month. Null if no reservations started this month.",
    )
    @JsonSchema.Example("3.5")
    val avgDays: Double?,
    @JsonSchema.Description(
        "Difference between totalDays for this month and totalDays for the previous calendar month. Positive means more occupied days than last month.",
    )
    @JsonSchema.Example("1")
    val comparedToPreviousMonth: Int,
    @JsonSchema.Description(
        "Difference between totalDays for this month and totalDays for the same month in the previous year. Positive means more occupied days than the same month last year.",
    )
    @JsonSchema.Example("-1")
    val comparedToSameMonthLastYear: Int,
)

@Serializable
data class MonthNightsStats(
    @JsonSchema.Description(
        "Total number of occupied nights in this month across all reservations, counting overlapping reservations only once per night. Departure night is not counted.",
    )
    @JsonSchema.Example("10")
    val totalNights: Int,
    @JsonSchema.Description(
        "Shortest stay in nights (full duration) among reservations that started in this month. Null if no reservations started this month.",
    )
    @JsonSchema.Example("1")
    val minNights: Int?,
    @JsonSchema.Description(
        "Longest stay in nights (full duration) among reservations that started in this month. Null if no reservations started this month.",
    )
    @JsonSchema.Example("5")
    val maxNights: Int?,
    @JsonSchema.Description(
        "Average stay in nights (full duration) among reservations that started in this month. Null if no reservations started this month.",
    )
    @JsonSchema.Example("2.5")
    val avgNights: Double?,
    @JsonSchema.Description(
        "Difference between totalNights for this month and totalNights for the previous calendar month. Positive means more occupied nights than last month.",
    )
    @JsonSchema.Example("1")
    val comparedToPreviousMonth: Int,
    @JsonSchema.Description(
        "Difference between totalNights for this month and totalNights for the same month in the previous year. Positive means more occupied nights than the same month last year.",
    )
    @JsonSchema.Example("-1")
    val comparedToSameMonthLastYear: Int,
)

@Serializable
data class MonthDrivingDistanceStats(
    @JsonSchema.Description("Total driving distance in kilometres for all trips to the cabin in this month.")
    @JsonSchema.Example("1240.5")
    val totalToCabinKm: Double,
    @JsonSchema.Description("Shortest single trip distance to the cabin in km this month. Null if no trips exist.")
    @JsonSchema.Example("148.2")
    val minToCabinKm: Double?,
    @JsonSchema.Description("Longest single trip distance to the cabin in km this month. Null if no trips exist.")
    @JsonSchema.Example("162.7")
    val maxToCabinKm: Double?,
    @JsonSchema.Description("Average trip distance to the cabin in km this month. Null if no trips exist.")
    @JsonSchema.Example("155.1")
    val avgToCabinKm: Double?,
    @JsonSchema.Description("Total driving distance in kilometres for all trips from the cabin in this month.")
    @JsonSchema.Example("1235.8")
    val totalFromCabinKm: Double,
    @JsonSchema.Description("Shortest single trip distance from the cabin in km this month. Null if no trips exist.")
    @JsonSchema.Example("147.9")
    val minFromCabinKm: Double?,
    @JsonSchema.Description("Longest single trip distance from the cabin in km this month. Null if no trips exist.")
    @JsonSchema.Example("163.4")
    val maxFromCabinKm: Double?,
    @JsonSchema.Description("Average trip distance from the cabin in km this month. Null if no trips exist.")
    @JsonSchema.Example("154.5")
    val avgFromCabinKm: Double?,
    @JsonSchema.Description("Total driving distance in kilometres for all trips at (around) the cabin in this month.")
    @JsonSchema.Example("86.4")
    val totalAtCabinKm: Double,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all toCabin legs, for the fastest reservation trip to the cabin this month. Null if no trips exist.",
    )
    @JsonSchema.Example("72.5")
    val minAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all toCabin legs, for the slowest reservation trip to the cabin this month. Null if no trips exist.",
    )
    @JsonSchema.Example("91.0")
    val maxAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Mean of avgSpeedKmh across all reservations with a toCabin trip this month. Null if no trips exist.",
    )
    @JsonSchema.Example("83.2")
    val avgAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all fromCabin legs, for the fastest reservation trip from the cabin this month. Null if no trips exist.",
    )
    @JsonSchema.Example("70.8")
    val minAvgSpeedFromCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all fromCabin legs, for the slowest reservation trip from the cabin this month. Null if no trips exist.",
    )
    @JsonSchema.Example("89.5")
    val maxAvgSpeedFromCabinKmh: Double?,
    @JsonSchema.Description(
        "Mean of avgSpeedKmh across all reservations with a fromCabin trip this month. Null if no trips exist.",
    )
    @JsonSchema.Example("81.6")
    val avgAvgSpeedFromCabinKmh: Double?,
)
