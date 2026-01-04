package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.Month
import no.slomic.smarthytte.common.averageOrNullInt
import no.slomic.smarthytte.common.formatClock
import no.slomic.smarthytte.common.formatMinutes
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.previousMonth
import no.slomic.smarthytte.vehicletrips.CabinVehicleTrip
import no.slomic.smarthytte.vehicletrips.avgArrivalCabinMinutes
import no.slomic.smarthytte.vehicletrips.avgArrivalHomeMinutes
import no.slomic.smarthytte.vehicletrips.avgDepartureCabinMinutes
import no.slomic.smarthytte.vehicletrips.avgDepartureHomeMinutes
import no.slomic.smarthytte.vehicletrips.fromCabinDurations
import no.slomic.smarthytte.vehicletrips.toCabinDurations

/**
 * Calculates driving time statistics for a specific year based on data from cabin vehicle trips.
 *
 * The method processes the provided list of trips, filtering them by the specified year and calculating
 * average, minimum, and maximum driving durations for trips to the cabin and trips from the cabin.
 *
 * @param year The calendar year for which the driving statistics are to be calculated.
 * @param cabinTrips A list of cabin vehicle trips that include details about trips to and from the cabin.
 * @return A [DrivingTimeStatsYear] containing the calculated driving statistics for the specified year.
 */
fun calculateYearDrivingTimeStats(year: Int, cabinTrips: List<CabinVehicleTrip>): DrivingTimeStatsYear {
    fun getStats(durations: List<Int>) = object {
        val avg = durations.averageOrNullInt()
        val min = durations.minOrNull()
        val max = durations.maxOrNull()
    }

    val toStats = getStats(cabinTrips.toCabinDurations(year))
    val fromStats = getStats(cabinTrips.fromCabinDurations(year))

    return DrivingTimeStatsYear(
        year = year,
        avgToCabinMinutes = toStats.avg,
        avgToCabin = formatMinutes(toStats.avg),
        minToCabinMinutes = toStats.min,
        minToCabin = formatMinutes(toStats.min),
        maxToCabinMinutes = toStats.max,
        maxToCabin = formatMinutes(toStats.max),
        avgFromCabinMinutes = fromStats.avg,
        avgFromCabin = formatMinutes(fromStats.avg),
        minFromCabinMinutes = fromStats.min,
        minFromCabin = formatMinutes(fromStats.min),
        maxFromCabinMinutes = fromStats.max,
        maxFromCabin = formatMinutes(fromStats.max),
    )
}

/**
 * Calculates driving time statistics for a specific month and year based on data from cabin vehicle trips.
 *
 * The method processes the provided list of trips, filtering them by the specified year and month and calculating
 * average, minimum, and maximum driving durations for trips to the cabin and trips from the cabin, as well
 * as comparisons with the previous month's averages.
 *
 * @param year The calendar year for which the driving statistics are to be calculated.
 * @param month The target month for which the driving statistics are to be calculated, represented as a [Month] enum.
 * @param cabinTrips A list of cabin vehicle trips that include details about trips to and from the cabin.
 * @return A [DrivingTimeStatsMonth] containing the calculated driving statistics for the specified month and year.
 */
fun calculateMonthDrivingTimeStats(year: Int, month: Month, cabinTrips: List<CabinVehicleTrip>): DrivingTimeStatsMonth {
    val (prevYear, prevMonth) = previousMonth(currentYear = year, currentMonth = month)

    fun getStats(durations: List<Int>) = object {
        val avg = durations.averageOrNullInt()
        val min = durations.minOrNull()
        val max = durations.maxOrNull()
    }

    val toCabinStats = getStats(cabinTrips.toCabinDurations(year, month))
    val fromCabinStats = getStats(cabinTrips.fromCabinDurations(year, month))

    val avgToPrev = cabinTrips.toCabinDurations(prevYear, prevMonth).averageOrNullInt()
    val avgFromPrev = cabinTrips.fromCabinDurations(prevYear, prevMonth).averageOrNullInt()

    fun diff(current: Int?, prev: Int?) = if (current != null && prev != null) current - prev else null
    val diffTo = diff(toCabinStats.avg, avgToPrev)
    val diffFrom = diff(fromCabinStats.avg, avgFromPrev)
    return DrivingTimeStatsMonth(
        monthNumber = month.value,
        monthName = monthNameOf(month),
        year = year,
        avgToCabinMinutes = toCabinStats.avg,
        avgToCabin = formatMinutes(toCabinStats.avg),
        minToCabinMinutes = toCabinStats.min,
        minToCabin = formatMinutes(toCabinStats.min),
        maxToCabinMinutes = toCabinStats.max,
        maxToCabin = formatMinutes(toCabinStats.max),
        avgFromCabinMinutes = fromCabinStats.avg,
        avgFromCabin = formatMinutes(fromCabinStats.avg),
        minFromCabinMinutes = fromCabinStats.min,
        minFromCabin = formatMinutes(fromCabinStats.min),
        maxFromCabinMinutes = fromCabinStats.max,
        maxFromCabin = formatMinutes(fromCabinStats.max),
        diffAvgToCabinMinutesVsPrevMonth = diffTo,
        diffAvgToCabinVsPrevMonth = formatMinutes(diffTo, showSign = true),
        diffAvgFromCabinMinutesVsPrevMonth = diffFrom,
        diffAvgFromCabinVsPrevMonth = formatMinutes(diffFrom, showSign = true),
    )
}

/**
 * Calculates average driving moment statistics (times of day) for a specific year based on data from cabin
 * vehicle trips.
 *
 * The method processes the provided list of trips, filtering them by the specified year and calculating
 * average departure and arrival times (represented as minutes since midnight in Oslo time) for trips
 * to the cabin and trips from the cabin.
 *
 * @param year The calendar year for which the driving statistics are to be calculated.
 * @param cabinTrips A list of cabin vehicle trips that include details about trips to and from the cabin.
 * @return A [DrivingMomentStatsYear] containing the calculated driving statistics for the specified year.
 */
fun calculateYearDrivingMomentStats(year: Int, cabinTrips: List<CabinVehicleTrip>): DrivingMomentStatsYear {
    val avgDepHome = cabinTrips.avgDepartureHomeMinutes(year)
    val avgArrCabin = cabinTrips.avgArrivalCabinMinutes(year)
    val avgDepCabin = cabinTrips.avgDepartureCabinMinutes(year)
    val avgArrHome = cabinTrips.avgArrivalHomeMinutes(year)

    return DrivingMomentStatsYear(
        year = year,
        avgDepartureHomeMinutes = avgDepHome,
        avgDepartureHome = formatClock(avgDepHome),
        avgArrivalCabinMinutes = avgArrCabin,
        avgArrivalCabin = formatClock(avgArrCabin),
        avgDepartureCabinMinutes = avgDepCabin,
        avgDepartureCabin = formatClock(avgDepCabin),
        avgArrivalHomeMinutes = avgArrHome,
        avgArrivalHome = formatClock(avgArrHome),
    )
}

/**
 * Calculates average driving moment statistics (times of day) for a specific month and year based on data
 * from cabin vehicle trips.
 *
 * The method processes the provided list of trips, filtering them by the specified year and month and calculating
 * average departure and arrival times (represented as minutes since midnight in Oslo time) for trips
 * to the cabin and trips from the cabin.
 *
 * @param year The calendar year for which the driving statistics are to be calculated.
 * @param month The target month for which the driving statistics are to be calculated, represented as a [Month] enum.
 * @param cabinTrips A list of cabin vehicle trips that include details about trips to and from the cabin.
 * @return A [DrivingMomentStatsMonth] containing the calculated driving statistics for the specified month and year.
 */
fun calculateMonthDrivingMomentStats(
    year: Int,
    month: Month,
    cabinTrips: List<CabinVehicleTrip>,
): DrivingMomentStatsMonth {
    val avgDepHome = cabinTrips.avgDepartureHomeMinutes(year, month)
    val avgArrCabin = cabinTrips.avgArrivalCabinMinutes(year, month)
    val avgDepCabin = cabinTrips.avgDepartureCabinMinutes(year, month)
    val avgArrHome = cabinTrips.avgArrivalHomeMinutes(year, month)

    return DrivingMomentStatsMonth(
        monthNumber = month.value,
        monthName = monthNameOf(month),
        year = year,
        avgDepartureHomeMinutes = avgDepHome,
        avgDepartureHome = formatClock(avgDepHome),
        avgArrivalCabinMinutes = avgArrCabin,
        avgArrivalCabin = formatClock(avgArrCabin),
        avgDepartureCabinMinutes = avgDepCabin,
        avgDepartureCabin = formatClock(avgDepCabin),
        avgArrivalHomeMinutes = avgArrHome,
        avgArrivalHome = formatClock(avgArrHome),
    )
}
