package no.slomic.smarthytte.statistics.model

import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.DrivingMomentStatsMonth
import no.slomic.smarthytte.statistics.calculator.DrivingTimeStatsMonth

@Serializable
data class MonthStats(
    val year: Int,
    val monthNumber: Int,
    val monthName: String,
    val visits: VisitsStats,
    val days: DaysStats,
    val nights: NightsStats,
    val occupancy: OccupancyStats,
    val guests: List<GuestVisitStats>,
    val drivingTime: DrivingTimeStatsMonth?,
    val drivingMoments: DrivingMomentStatsMonth?,
)

@Serializable
data class VisitsStats(
    val totalVisits: Int,
    val comparedToPreviousMonth: Int,
    val comparedToSameMonthLastYear: Int,
    val comparedToYearToDateAverage: Double,
)

@Serializable
data class DaysStats(
    val totalDays: Int,
    val minDays: Int?,
    val maxDays: Int?,
    val avgDays: Double?,
    val comparedToPreviousMonth: Int,
    val comparedToSameMonthLastYear: Int,
)

@Serializable
data class NightsStats(
    val totalNights: Int,
    val minNights: Int?,
    val maxNights: Int?,
    val avgNights: Double?,
    val comparedToPreviousMonth: Int,
    val comparedToSameMonthLastYear: Int,
)
