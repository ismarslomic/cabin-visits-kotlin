package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.Month
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.common.averageOrNullInt
import no.slomic.smarthytte.common.formatClock
import no.slomic.smarthytte.common.formatMinutes
import no.slomic.smarthytte.common.minutesOfDay
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.osloTimeZone
import no.slomic.smarthytte.common.previousMonth
import no.slomic.smarthytte.vehicletrips.CabinVehicleTrip
import no.slomic.smarthytte.vehicletrips.totalDurationMinutes

/**
 * Calculates driving time statistics for a given calendar year based on (vehicle) trips to and from the cabin.
 *
 * The method processes the provided list of trips, filtering them by the specified year and calculating
 * average, minimum, and maximum driving durations for trips to the cabin and trips from the cabin.
 *
 * @param year The calendar year for which the driving statistics are to be calculated.
 * @param cabinTrips A list of cabin vehicle trips that include details about trips to and from the cabin.
 * @return A [DrivingTimeStatsYear] object containing the calculated driving statistics for the specified year.
 */
fun calculateYearDrivingStats(year: Int, cabinTrips: List<CabinVehicleTrip>): DrivingTimeStatsYear {
    val toCabinDurations = cabinTrips
        .filter { it.toCabinEndDate?.year == year }
        .mapNotNull { it.toCabinTrips.totalDurationMinutes() }
    val fromCabinDurations = cabinTrips
        .filter { it.fromCabinStartDate?.year == year }
        .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }

    fun getStats(durations: List<Int>) = object {
        val avg = durations.averageOrNullInt()
        val min = durations.minOrNull()
        val max = durations.maxOrNull()
    }

    val to = getStats(toCabinDurations)
    val from = getStats(fromCabinDurations)

    return DrivingTimeStatsYear(
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
    )
}

fun computeYearDrivingMomentStats(year: Int, cabinTrips: List<CabinVehicleTrip>): DrivingMomentStatsYear {
    val depHome: List<Int> = cabinTrips.mapNotNull { trip ->
        trip.toCabinStartTimestamp?.toLocalDateTime(osloTimeZone)?.let {
            if (it.date.year == year) it.time.minutesOfDay() else null
        }
    }
    val arrCabin: List<Int> = cabinTrips.mapNotNull { trip ->
        trip.toCabinEndTimestamp?.toLocalDateTime(osloTimeZone)?.let {
            if (it.date.year == year) it.time.minutesOfDay() else null
        }
    }
    val depCabin: List<Int> = cabinTrips.mapNotNull { trip ->
        trip.fromCabinStartTimestamp?.toLocalDateTime(osloTimeZone)?.let {
            if (it.date.year == year) it.time.minutesOfDay() else null
        }
    }
    val arrHome: List<Int> = cabinTrips.mapNotNull { trip ->
        trip.fromCabinEndTimestamp?.toLocalDateTime(osloTimeZone)?.let {
            if (it.date.year == year) it.time.minutesOfDay() else null
        }
    }

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

fun computeMonthDrivingStats(year: Int, month: Month, cabinTrips: List<CabinVehicleTrip>): DrivingTimeStatsMonth {
    val (prevYear, prevMonth) = previousMonth(currentYear = year, currentMonth = month)

    val toCabinDurations = cabinTrips
        .filter { it.toCabinEndDate?.year == year && it.toCabinEndDate?.month == month }
        .mapNotNull { it.toCabinTrips.totalDurationMinutes() }
    val fromCabinDurations = cabinTrips
        .filter { it.fromCabinStartDate?.year == year && it.fromCabinStartDate?.month == month }
        .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }

    val toCabinDurationsPrev = cabinTrips
        .filter { it.toCabinEndDate?.year == prevYear && it.toCabinEndDate?.month == prevMonth }
        .mapNotNull { it.toCabinTrips.totalDurationMinutes() }
    val fromCabinDurationsPrev = cabinTrips
        .filter { it.fromCabinStartDate?.year == prevYear && it.fromCabinStartDate?.month == prevMonth }
        .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }

    val avgTo = toCabinDurations.averageOrNullInt()
    val minTo = toCabinDurations.minOrNull()
    val maxTo = toCabinDurations.maxOrNull()
    val avgToPrev = toCabinDurationsPrev.averageOrNullInt()

    val avgFrom = fromCabinDurations.averageOrNullInt()
    val minFrom = fromCabinDurations.minOrNull()
    val maxFrom = fromCabinDurations.maxOrNull()
    val avgFromPrev = fromCabinDurationsPrev.averageOrNullInt()

    val diffAvgTo = if (avgTo != null && avgToPrev != null) avgTo - avgToPrev else null
    val diffAvgFrom = if (avgFrom != null && avgFromPrev != null) avgFrom - avgFromPrev else null

    return DrivingTimeStatsMonth(
        monthNumber = month.value,
        monthName = monthNameOf(month),
        year = year,
        avgToCabinMinutes = avgTo,
        avgToCabin = formatMinutes(avgTo),
        minToCabinMinutes = minTo,
        minToCabin = formatMinutes(minTo),
        maxToCabinMinutes = maxTo,
        maxToCabin = formatMinutes(maxTo),
        avgFromCabinMinutes = avgFrom,
        avgFromCabin = formatMinutes(avgFrom),
        minFromCabinMinutes = minFrom,
        minFromCabin = formatMinutes(minFrom),
        maxFromCabinMinutes = maxFrom,
        maxFromCabin = formatMinutes(maxFrom),
        diffAvgToCabinMinutesVsPrevMonth = diffAvgTo,
        diffAvgToCabinVsPrevMonth = formatMinutes(diffAvgTo, showSign = true),
        diffAvgFromCabinMinutesVsPrevMonth = diffAvgFrom,
        diffAvgFromCabinVsPrevMonth = formatMinutes(diffAvgFrom, showSign = true),
    )
}

fun computeMonthDrivingMomentStats(
    year: Int,
    month: Month,
    cabinTrips: List<CabinVehicleTrip>,
): DrivingMomentStatsMonth {
    val depHome: List<Int> = cabinTrips.mapNotNull { trip ->
        trip.toCabinStartTimestamp?.toLocalDateTime(osloTimeZone)?.let {
            if (it.date.year == year && it.date.month == month) it.time.minutesOfDay() else null
        }
    }
    val arrCabin: List<Int> = cabinTrips.mapNotNull { trip ->
        trip.toCabinEndTimestamp?.toLocalDateTime(osloTimeZone)?.let {
            if (it.date.year == year && it.date.month == month) it.time.minutesOfDay() else null
        }
    }
    val depCabin: List<Int> = cabinTrips.mapNotNull { trip ->
        trip.fromCabinStartTimestamp?.toLocalDateTime(osloTimeZone)?.let {
            if (it.date.year == year && it.date.month == month) it.time.minutesOfDay() else null
        }
    }
    val arrHome: List<Int> = cabinTrips.mapNotNull { trip ->
        trip.fromCabinEndTimestamp?.toLocalDateTime(osloTimeZone)?.let {
            if (it.date.year == year && it.date.month == month) it.time.minutesOfDay() else null
        }
    }

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
