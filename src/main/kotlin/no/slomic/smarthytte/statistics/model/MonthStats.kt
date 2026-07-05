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
    val minStayDays: Int?,
    val maxStayDays: Int?,
    val avgStayDays: Double?,
    val percentDaysOccupied: Double,
    val percentWeeksOccupied: Double,
    val guests: List<GuestVisitStats>,
    val drivingTime: DrivingTimeStatsMonth?,
    val drivingMoments: DrivingMomentStatsMonth?,
)
