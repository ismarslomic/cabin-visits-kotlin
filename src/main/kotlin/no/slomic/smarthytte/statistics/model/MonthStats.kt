package no.slomic.smarthytte.statistics.model

import kotlinx.serialization.Serializable
import no.slomic.smarthytte.statistics.calculator.DrivingMomentStatsMonth
import no.slomic.smarthytte.statistics.calculator.DrivingTimeStatsMonth

@Serializable
data class MonthStats(
    val year: Int,
    val monthNumber: Int,
    val monthName: String,
    val totalVisits: Int,
    val visitsComparedToLast30Days: Int,
    val visitsComparedToSameMonthLastYear: Int,
    val visitsComparedToYearToDateAverage: Double,
    val days: DaysStats,
    val nights: NightsStats,
    val percentDaysOccupied: Double,
    val percentWeeksOccupied: Double,
    val guests: List<GuestVisitStats>,
    val drivingTime: DrivingTimeStatsMonth?,
    val drivingMoments: DrivingMomentStatsMonth?,
)

@Serializable
data class DaysStats(
    val totalDays: Int,
    val minDays: Int?,
    val maxDays: Int?,
    val avgDays: Double?,
    val totalDaysComparedToLast30Days: Int,
    val totalDaysComparedToSameMonthLastYear: Int,
)

@Serializable
data class NightsStats(
    val totalNights: Int,
    val minNights: Int?,
    val maxNights: Int?,
    val avgNights: Double?,
    val totalNightsComparedToLast30Days: Int,
    val totalNightsComparedToSameMonthLastYear: Int,
)
