@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.DrivingMomentStatsYear
import no.slomic.smarthytte.statistics.calculator.DrivingTimeStatsYear

@Serializable
data class YearStats(
    @JsonSchema.Description("The calendar year these statistics cover.")
    val year: Int,
    @JsonSchema.Description("Number of reservations that started in this calendar year.")
    val totalVisits: Int,
    @JsonSchema.Description(
        "Difference between totalVisits for this year and the number of visits in the 12-month window ending on Jan 1 of this year. Positive means more visits than the rolling 12-month baseline.",
    )
    val visitsComparedToLast12Months: Int,
    @JsonSchema.Description("Average number of visits per calendar month (totalVisits / 12).")
    val averageMonthlyVisits: Double,
    @JsonSchema.Description("Average number of guests per reservation this year. Null if there are no reservations.")
    val averageGroupSize: Double?,
    @JsonSchema.Description("Average number of nights per reservation this year. Null if there are no reservations.")
    val averageNightsPerVisit: Double?,
    @JsonSchema.Description(
        "Total nights the cabin was occupied this year, counting overlapping reservations only once per night.",
    )
    val totalNights: Int,
    @JsonSchema.Description(
        "Difference between totalNights for this year and totalNights in the 12-month window ending on Jan 1 of this year.",
    )
    val totalNightsComparedToLast12Months: Int,
    @JsonSchema.Description("Average number of occupied nights per calendar month (totalNights / 12).")
    val averageMonthlyNights: Double,
    @JsonSchema.Description("Occupancy percentages for the year across days, weeks, and months.")
    val occupancy: OccupancyStats,
    @JsonSchema.Description(
        "The month with the highest number of reservation starts this year. Null if no reservations exist.",
    )
    val monthMostVisits: MonthlyVisitCount?,
    @JsonSchema.Description(
        "The month with the fewest reservation starts this year, among months that had at least one visit. Null if no reservations exist.",
    )
    val monthFewestVisits: MonthlyVisitCount?,
    @JsonSchema.Description(
        "The month in which the single longest individual stay (by days) started this year. Null if no reservations exist.",
    )
    val monthLongestVisits: MonthlyDaysCount?,
    @JsonSchema.Description("The guest with the most total days at the cabin this year. Null if no guest data exists.")
    val topGuestByDays: GuestVisitStats?,
    @JsonSchema.Description(
        "Guests who appear in this year's reservations but did not appear in any reservation in the previous year. Sorted by total days descending.",
    )
    val newGuests: List<GuestVisitStats>,
    @JsonSchema.Description("All guests who appear in this year's reservations, sorted by total days descending.")
    val guests: List<GuestVisitStats>,
    @JsonSchema.Description(
        "Total driving distance in kilometres for all trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val totalDistanceKm: Double?,
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
    @JsonSchema.Description(
        "Driving duration statistics (avg/min/max) for trips to and from the cabin this year. Null if no vehicle trip data exists.",
    )
    val drivingTime: DrivingTimeStatsYear?,
    @JsonSchema.Description(
        "Average departure and arrival clock times for trips to and from the cabin this year, expressed in Oslo timezone. Null if no vehicle trip data exists.",
    )
    val drivingMoments: DrivingMomentStatsYear?,
    @JsonSchema.Description("Statistics broken down for each of the 12 calendar months in this year.")
    val months: List<MonthStats>,
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
