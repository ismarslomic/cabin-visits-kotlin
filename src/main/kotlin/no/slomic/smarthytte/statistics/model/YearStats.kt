package no.slomic.smarthytte.statistics.model

import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.DrivingMomentStatsYear
import no.slomic.smarthytte.statistics.calculator.DrivingTimeStatsYear

@Serializable
data class YearStats(
    val year: Int,
    val totalVisits: Int,
    val visitsComparedToLast12Months: Int,
    val averageMonthlyVisits: Double,
    val averageGroupSize: Double?,
    val averageNightsPerVisit: Double?,
    val totalNights: Int,
    val totalNightsComparedToLast12Months: Int,
    val averageMonthlyNights: Double,
    val occupancy: OccupancyStats,
    val monthMostVisits: MonthlyVisitCount?,
    val monthFewestVisits: MonthlyVisitCount?,
    val monthLongestVisits: MonthlyDaysCount?,
    val topGuestByDays: GuestVisitStats?,
    val newGuests: List<GuestVisitStats>,
    val guests: List<GuestVisitStats>,
    val totalDistanceKm: Double?,
    val totalEnergyConsumedKwh: Double?,
    val avgEnergyConsumptionKwhPer100km: Double?,
    val totalEnergyRegeneratedKwh: Double?,
    val drivingTime: DrivingTimeStatsYear?,
    val drivingMoments: DrivingMomentStatsYear?,
    val months: List<MonthStats>,
)

@Serializable
data class MonthlyVisitCount(val monthNumber: Int, val monthName: String, val visitCount: Int)

@Serializable
data class MonthlyDaysCount(val monthNumber: Int, val monthName: String, val daysCount: Int)

@Serializable
data class GuestVisitStats(
    val guestId: String,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val totalVisits: Int,
    val totalDays: Int,
) {
    companion object {
        val COMPARATOR: Comparator<GuestVisitStats> = compareByDescending<GuestVisitStats> { it.totalDays }
            .thenByDescending { it.totalVisits }
            .thenBy { it.lastName }
            .thenBy { it.firstName }
    }
}
