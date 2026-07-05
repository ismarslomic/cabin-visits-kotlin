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
    val averageStayDays: Double?,
    val totalStayDays: Int,
    val stayDaysComparedToLast12Months: Int,
    val averageMonthlyStayDays: Double,
    val percentDaysOccupied: Double,
    val percentWeeksOccupied: Double,
    val percentMonthsOccupied: Double,
    val monthMostVisits: MonthCount?,
    val monthFewestVisits: MonthCount?,
    val monthWithLongestStay: MonthStay?,
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
data class MonthCount(val monthNumber: Int, val monthName: String, val visitCount: Int)

@Serializable
data class MonthStay(val monthNumber: Int, val monthName: String, val totalDays: Int)

@Serializable
data class GuestVisitStats(
    val guestId: String,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val totalVisits: Int,
    val totalStayDays: Int,
) {
    companion object {
        val COMPARATOR: Comparator<GuestVisitStats> = compareByDescending<GuestVisitStats> { it.totalStayDays }
            .thenByDescending { it.totalVisits }
            .thenBy { it.lastName }
            .thenBy { it.firstName }
    }
}
