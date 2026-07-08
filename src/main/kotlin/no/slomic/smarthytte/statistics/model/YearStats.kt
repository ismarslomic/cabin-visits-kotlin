@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.YearDrivingMomentStats
import no.slomic.smarthytte.statistics.calculator.YearDrivingTimeStats

@Serializable
data class YearStats(
    @JsonSchema.Description("The calendar year these statistics cover.")
    val year: Int,
    @JsonSchema.Description("Visit count for this year with comparisons against the previous year.")
    val visits: YearVisitsStats,
    @JsonSchema.Description("Occupied-day statistics for this year (including departure day).")
    val days: YearDaysStats,
    @JsonSchema.Description("Occupied-night statistics for this year (excluding departure day).")
    val nights: YearNightsStats,
    @JsonSchema.Description("Occupancy percentages for the year across days, weeks, and months.")
    val occupancy: OccupancyStats,
    @JsonSchema.Description("The guest with the most total days at the cabin this year. Null if no guest data exists.")
    val topGuestByDays: GuestVisitStats?,
    @JsonSchema.Description(
        "Guests who appear in this year's reservations but did not appear in any reservation in the previous year. Sorted by total days descending.",
    )
    val newGuests: List<GuestVisitStats>,
    @JsonSchema.Description("All guests who appear in this year's reservations, sorted by total days descending.")
    val guests: List<GuestVisitStats>,
    @JsonSchema.Description(
        "Driving distance statistics for trips to, from, and at the cabin this year. Null if no vehicle trip data exists.",
    )
    val drivingDistance: YearDrivingDistanceStats?,
    @JsonSchema.Description(
        "Electric vehicle energy statistics for trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val ev: YearEvStats?,
    @JsonSchema.Description(
        "Driving duration statistics (avg/min/max) for trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val drivingTime: YearDrivingTimeStats?,
    @JsonSchema.Description(
        "Average departure and arrival clock times for trips to and from the cabin this year, expressed in Oslo timezone. Null if no vehicle trip data exists.",
    )
    val drivingMoments: YearDrivingMomentStats?,
    @JsonSchema.Description("Statistics broken down for each of the 12 calendar months in this year.")
    val months: List<MonthStats>,
)

@Serializable
data class YearVisitsStats(
    @JsonSchema.Description("Number of reservations that started in this calendar year.")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Difference between this year's visit count and the previous year's visit count. Positive means more visits than last year.",
    )
    val comparedToPreviousYear: Int,
    @JsonSchema.Description("Average number of visits per calendar month (totalVisits / 12).")
    val avgMonthlyVisits: Double,
    @JsonSchema.Description("Average number of guests per reservation this year. Null if there are no reservations.")
    val avgGroupSize: Double?,
    @JsonSchema.Description(
        "The month with the highest number of reservation starts this year. Null if no reservations exist.",
    )
    val monthMostVisits: MonthlyVisitCount?,
    @JsonSchema.Description(
        "The month with the fewest reservation starts this year, among months that had at least one visit. Null if no reservations exist.",
    )
    val monthFewestVisits: MonthlyVisitCount?,
)

@Serializable
data class YearDaysStats(
    @JsonSchema.Description(
        "Total number of occupied days in this year across all reservations, counting overlapping reservations only once per day. Departure day is counted as an occupied day.",
    )
    val totalDays: Int,
    @JsonSchema.Description(
        "Minimum number of days of any single reservation in this year. Null if no reservations exist.",
    )
    val minDays: Int?,
    @JsonSchema.Description(
        "Maximum number of days of any single reservation in this year. Null if no reservations exist.",
    )
    val maxDays: Int?,
    @JsonSchema.Description(
        "Average number of days per reservation in this year. Null if no reservations exist.",
    )
    val avgDays: Double?,
    @JsonSchema.Description(
        "Difference between totalDays for this year and totalDays for the previous year. Positive means more occupied days than last year.",
    )
    val comparedToPreviousYear: Int,
    @JsonSchema.Description(
        "The month in which the single longest individual stay (by days) started this year. Null if no reservations exist.",
    )
    val monthLongestVisit: MonthlyDaysCount?,
)

@Serializable
data class YearNightsStats(
    @JsonSchema.Description(
        "Total number of occupied nights in this year across all reservations, counting overlapping reservations only once per night. Departure night is not counted.",
    )
    val totalNights: Int,
    @JsonSchema.Description(
        "Minimum number of nights of any single reservation in this year. Null if no reservations exist.",
    )
    val minNights: Int?,
    @JsonSchema.Description(
        "Maximum number of nights of any single reservation in this year. Null if no reservations exist.",
    )
    val maxNights: Int?,
    @JsonSchema.Description(
        "Average number of nights per reservation in this year. Null if no reservations exist.",
    )
    val avgNights: Double?,
    @JsonSchema.Description("Average number of occupied nights per calendar month (totalNights / 12).")
    val avgMonthlyNights: Double,
    @JsonSchema.Description(
        "Difference between totalNights for this year and totalNights for the previous year. Positive means more occupied nights than last year.",
    )
    val comparedToPreviousYear: Int,
)

@Serializable
data class YearDrivingDistanceStats(
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips to the cabin this year.",
    )
    val totalToCabinKm: Double,
    @JsonSchema.Description("Shortest single trip distance to the cabin in km this year. Null if no trips exist.")
    val minToCabinKm: Double?,
    @JsonSchema.Description("Longest single trip distance to the cabin in km this year. Null if no trips exist.")
    val maxToCabinKm: Double?,
    @JsonSchema.Description("Average trip distance to the cabin in km this year. Null if no trips exist.")
    val avgToCabinKm: Double?,
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips from the cabin this year.",
    )
    val totalFromCabinKm: Double,
    @JsonSchema.Description("Shortest single trip distance from the cabin in km this year. Null if no trips exist.")
    val minFromCabinKm: Double?,
    @JsonSchema.Description("Longest single trip distance from the cabin in km this year. Null if no trips exist.")
    val maxFromCabinKm: Double?,
    @JsonSchema.Description("Average trip distance from the cabin in km this year. Null if no trips exist.")
    val avgFromCabinKm: Double?,
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips at (around) the cabin this year.",
    )
    val totalAtCabinKm: Double,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all toCabin legs, for the fastest reservation trip to the cabin this year. Null if no trips exist.",
    )
    val minAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all toCabin legs, for the slowest reservation trip to the cabin this year. Null if no trips exist.",
    )
    val maxAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Mean of avgSpeedKmh across all reservations with a toCabin trip this year. Null if no trips exist.",
    )
    val avgAvgSpeedToCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all fromCabin legs, for the fastest reservation trip from the cabin this year. Null if no trips exist.",
    )
    val minAvgSpeedFromCabinKmh: Double?,
    @JsonSchema.Description(
        "Average of VehicleTrip.averageSpeed across all fromCabin legs, for the slowest reservation trip from the cabin this year. Null if no trips exist.",
    )
    val maxAvgSpeedFromCabinKmh: Double?,
    @JsonSchema.Description(
        "Mean of avgSpeedKmh across all reservations with a fromCabin trip this year. Null if no trips exist.",
    )
    val avgAvgSpeedFromCabinKmh: Double?,
)

@Serializable
data class YearEvStats(
    @JsonSchema.Description(
        "Total energy consumed (kWh) for all trips to and from the cabin this year, derived from average consumption × distance per trip. Null if no vehicle trip data exists.",
    )
    val totalEnergyConsumedKwh: Double?,
    @JsonSchema.Description(
        "Distance-weighted average energy consumption in kWh per 100 km across all trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val avgEnergyConsumptionKwhPer100km: Double?,
    @JsonSchema.Description(
        "Total energy regenerated (kWh) through regenerative braking across all trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val totalEnergyRegeneratedKwh: Double?,
)

@Serializable
data class MonthlyVisitCount(
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    val monthName: String,
    @JsonSchema.Description("Number of reservation starts in this month.")
    val visitCount: Int,
)

@Serializable
data class MonthlyDaysCount(
    @JsonSchema.Description("Month number (1 = January, 12 = December).")
    val monthNumber: Int,
    @JsonSchema.Description("Localised month name (e.g. \"Januar\").")
    val monthName: String,
    @JsonSchema.Description("Duration in days of the longest individual stay that started in this month.")
    val daysCount: Int,
)

@Serializable
data class GuestVisitStats(
    @JsonSchema.Description("Internal unique identifier of the guest.")
    val guestId: String,
    @JsonSchema.Description("Guest's first name.")
    val firstName: String,
    @JsonSchema.Description("Guest's last name.")
    val lastName: String,
    @JsonSchema.Description("Guest's age in the reference year (ageYear - birthYear).")
    val age: Int,
    @JsonSchema.Description("Number of reservations the guest was part of in the period.")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Total number of days the guest was present at the cabin in the period, counting only days that fall within the period boundary.",
    )
    val totalDays: Int,
) {
    companion object {
        val COMPARATOR: Comparator<GuestVisitStats> = compareByDescending<GuestVisitStats> { it.totalDays }
            .thenByDescending { it.totalVisits }
            .thenBy { it.lastName }
            .thenBy { it.firstName }
    }
}
