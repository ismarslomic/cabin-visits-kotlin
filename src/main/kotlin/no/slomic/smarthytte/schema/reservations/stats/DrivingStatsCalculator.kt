package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.Month
import no.slomic.smarthytte.common.averageOrNullInt
import no.slomic.smarthytte.common.formatClock
import no.slomic.smarthytte.common.formatMinutes
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.previousMonth
import no.slomic.smarthytte.vehicletrips.CabinVehicleTrip
import no.slomic.smarthytte.vehicletrips.arrivalCabinMinutes
import no.slomic.smarthytte.vehicletrips.arrivalHomeMinutes
import no.slomic.smarthytte.vehicletrips.departureCabinMinutes
import no.slomic.smarthytte.vehicletrips.departureHomeMinutes
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

    val to = getStats(cabinTrips.toCabinDurations(year, month))
    val from = getStats(cabinTrips.fromCabinDurations(year, month))

    val avgToPrev = cabinTrips.toCabinDurations(prevYear, prevMonth).averageOrNullInt()
    val avgFromPrev = cabinTrips.fromCabinDurations(prevYear, prevMonth).averageOrNullInt()

    fun diff(current: Int?, prev: Int?) = if (current != null && prev != null) current - prev else null
    val diffTo = diff(to.avg, avgToPrev)
    val diffFrom = diff(from.avg, avgFromPrev)
    return DrivingTimeStatsMonth(
        monthNumber = month.value,
        monthName = monthNameOf(month),
        year = year,
        avgToCabinMinutes = to.avg,
        avgToCabin = formatMinutes(to.avg),
        minToCabinMinutes = to.min,
        minToCabin = formatMinutes(to.min),
        maxToCabinMinutes = to.max,
        maxToCabin = formatMinutes(to.max),
        avgFromCabinMinutes = from.avg,
        avgFromCabin = formatMinutes(from.avg),
        minFromCabinMinutes = from.min,
        minFromCabin = formatMinutes(from.min),
        maxFromCabinMinutes = from.max,
        maxFromCabin = formatMinutes(from.max),
        diffAvgToCabinMinutesVsPrevMonth = diffTo,
        diffAvgToCabinVsPrevMonth = formatMinutes(diffTo, showSign = true),
        diffAvgFromCabinMinutesVsPrevMonth = diffFrom,
        diffAvgFromCabinVsPrevMonth = formatMinutes(diffFrom, showSign = true),
    )
}

/**
 * Calculates the yearly driving moment statistics based on the provided cabin trips.
 * These statistics include the average departure and arrival times (both in minutes since
 * midnight and formatted as "HH:MM") for trips to and from the cabin within the specified year.
 *
 * @param year The calendar year for which the statistics should be calculated.
 * @param cabinTrips A list of `CabinVehicleTrip` instances representing the trips to and from the cabin.
 * @return A `DrivingMomentStatsYear` object containing the calculated average departure and arrival
 *         times for the specified year.
 */
fun calculateYearDrivingMomentStats(year: Int, cabinTrips: List<CabinVehicleTrip>): DrivingMomentStatsYear {
    val depHome: List<Int> = cabinTrips.departureHomeMinutes(year)
    val arrCabin: List<Int> = cabinTrips.arrivalCabinMinutes(year)
    val depCabin: List<Int> = cabinTrips.departureCabinMinutes(year)
    val arrHome: List<Int> = cabinTrips.arrivalHomeMinutes(year)

    return DrivingMomentStatsYear(
        year = year,
        avgDepartureHomeMinutes = depHome.averageOrNullInt(),
        avgDepartureHome = formatClock(depHome.averageOrNullInt()),
        avgArrivalCabinMinutes = arrCabin.averageOrNullInt(),
        avgArrivalCabin = formatClock(arrCabin.averageOrNullInt()),
        avgDepartureCabinMinutes = depCabin.averageOrNullInt(),
        avgDepartureCabin = formatClock(depCabin.averageOrNullInt()),
        avgArrivalHomeMinutes = arrHome.averageOrNullInt(),
        avgArrivalHome = formatClock(arrHome.averageOrNullInt()),
    )
}

fun calculateMonthDrivingMomentStats(
    year: Int,
    month: Month,
    cabinTrips: List<CabinVehicleTrip>,
): DrivingMomentStatsMonth {
    val depHome = cabinTrips.departureHomeMinutes(year, month)
    val arrCabin = cabinTrips.arrivalCabinMinutes(year, month)
    val depCabin = cabinTrips.departureCabinMinutes(year, month)
    val arrHome = cabinTrips.arrivalHomeMinutes(year, month)

    return DrivingMomentStatsMonth(
        monthNumber = month.value,
        monthName = monthNameOf(month),
        year = year,
        avgDepartureHomeMinutes = depHome.averageOrNullInt(),
        avgDepartureHome = formatClock(depHome.averageOrNullInt()),
        avgArrivalCabinMinutes = arrCabin.averageOrNullInt(),
        avgArrivalCabin = formatClock(arrCabin.averageOrNullInt()),
        avgDepartureCabinMinutes = depCabin.averageOrNullInt(),
        avgDepartureCabin = formatClock(depCabin.averageOrNullInt()),
        avgArrivalHomeMinutes = arrHome.averageOrNullInt(),
        avgArrivalHome = formatClock(arrHome.averageOrNullInt()),
    )
}
