@file:Suppress("LongParameterList")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.Month
import no.slomic.smarthytte.statistics.model.GuestVisitStats
import no.slomic.smarthytte.statistics.model.MonthDaysStats
import no.slomic.smarthytte.statistics.model.MonthNightsStats
import no.slomic.smarthytte.statistics.model.MonthStats
import no.slomic.smarthytte.statistics.model.MonthVisitsStats
import no.slomic.smarthytte.statistics.model.MonthlyVisitCount
import no.slomic.smarthytte.statistics.model.OccupancyStats
import no.slomic.smarthytte.statistics.model.YearDaysStats
import no.slomic.smarthytte.statistics.model.YearDrivingDistanceStats
import no.slomic.smarthytte.statistics.model.YearEvStats
import no.slomic.smarthytte.statistics.model.YearNightsStats
import no.slomic.smarthytte.statistics.model.YearStats
import no.slomic.smarthytte.statistics.model.YearVisitsStats

/** Minimal [YearStats] for fun fact tests: only the given values are set, the rest is empty or zero. */
fun yearStatsFixture(
    year: Int = 2026,
    totalVisits: Int = 0,
    avgGroupSize: Double? = null,
    monthMostVisits: MonthlyVisitCount? = null,
    maxNights: Int? = null,
    dayOccupancy: Double = 0.0,
    newGuests: Int = 0,
    drivingDistance: YearDrivingDistanceStats? = null,
    ev: YearEvStats? = null,
    drivingTime: YearDrivingTimeStats? = null,
    months: List<MonthStats> = emptyList(),
) = YearStats(
    year = year,
    visits = YearVisitsStats(
        totalVisits = totalVisits,
        comparedToPreviousYear = 0,
        avgMonthlyVisits = 0.0,
        avgGroupSize = avgGroupSize,
        monthMostVisits = monthMostVisits,
        monthFewestVisits = null,
    ),
    days = YearDaysStats(
        totalDays = 0,
        minDays = null,
        maxDays = null,
        avgDays = null,
        comparedToPreviousYear = 0,
        monthLongestVisit = null,
    ),
    nights = YearNightsStats(
        totalNights = 0,
        minNights = null,
        maxNights = maxNights,
        avgNights = null,
        avgMonthlyNights = 0.0,
        comparedToPreviousYear = 0,
    ),
    occupancy = OccupancyStats(dayOccupancy = dayOccupancy, weekOccupancy = 0.0),
    topGuestByDays = null,
    newGuests = List(newGuests) { GuestVisitStats("guest$it", "Guest", "$it", 30, 1, 1) },
    guests = emptyList(),
    drivingDistance = drivingDistance,
    ev = ev,
    drivingTime = drivingTime,
    drivingMoments = null,
    months = months,
)

fun distanceFixture(toCabinKm: Double, fromCabinKm: Double) = YearDrivingDistanceStats(
    totalToCabinKm = toCabinKm,
    minToCabinKm = null,
    maxToCabinKm = null,
    avgToCabinKm = null,
    totalFromCabinKm = fromCabinKm,
    minFromCabinKm = null,
    maxFromCabinKm = null,
    avgFromCabinKm = null,
    totalAtCabinKm = 0.0,
    minAvgSpeedToCabinKmh = null,
    maxAvgSpeedToCabinKmh = null,
    avgAvgSpeedToCabinKmh = null,
    minAvgSpeedFromCabinKmh = null,
    maxAvgSpeedFromCabinKmh = null,
    avgAvgSpeedFromCabinKmh = null,
)

fun yearDrivingTimeFixture(avgToCabinMinutes: Int?, avgFromCabinMinutes: Int?, year: Int = 2026) = YearDrivingTimeStats(
    year = year,
    avgToCabinMinutes = avgToCabinMinutes,
    avgToCabin = null,
    minToCabinMinutes = null,
    minToCabin = null,
    maxToCabinMinutes = null,
    maxToCabin = null,
    avgFromCabinMinutes = avgFromCabinMinutes,
    avgFromCabin = null,
    minFromCabinMinutes = null,
    minFromCabin = null,
    maxFromCabinMinutes = null,
    maxFromCabin = null,
)

fun monthDrivingTimeFixture(avgToCabinMinutes: Int?, avgFromCabinMinutes: Int?, month: Month, year: Int = 2026) =
    MonthDrivingTimeStats(
        monthNumber = month.ordinal + 1,
        monthName = month.name,
        year = year,
        avgToCabinMinutes = avgToCabinMinutes,
        avgToCabin = null,
        minToCabinMinutes = null,
        minToCabin = null,
        maxToCabinMinutes = null,
        maxToCabin = null,
        avgFromCabinMinutes = avgFromCabinMinutes,
        avgFromCabin = null,
        minFromCabinMinutes = null,
        minFromCabin = null,
        maxFromCabinMinutes = null,
        maxFromCabin = null,
        diffAvgToCabinMinutesVsPrevMonth = null,
        diffAvgToCabinVsPrevMonth = null,
        diffAvgFromCabinMinutesVsPrevMonth = null,
        diffAvgFromCabinVsPrevMonth = null,
    )

/** All 12 months of a year; only the given month gets driving time and visit delta, the others stay empty. */
fun monthsFixture(
    year: Int,
    month: Month,
    drivingTime: MonthDrivingTimeStats? = null,
    comparedToSameMonthLastYear: Int = 0,
): List<MonthStats> = Month.entries.map {
    val isTarget = it == month
    MonthStats(
        year = year,
        monthNumber = it.ordinal + 1,
        monthName = it.name,
        visits = MonthVisitsStats(
            totalVisits = 0,
            comparedToPreviousMonth = 0,
            comparedToSameMonthLastYear = if (isTarget) comparedToSameMonthLastYear else 0,
            comparedToYearToDateAverage = 0.0,
        ),
        days = MonthDaysStats(0, null, null, null, 0, 0),
        nights = MonthNightsStats(0, null, null, null, 0, 0),
        occupancy = OccupancyStats(dayOccupancy = 0.0, weekOccupancy = 0.0),
        guests = emptyList(),
        drivingDistance = null,
        drivingTime = drivingTime.takeIf { isTarget },
        drivingMoments = null,
    )
}
