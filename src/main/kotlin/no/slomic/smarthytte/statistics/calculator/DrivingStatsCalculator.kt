@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.common.averageOrNullInt
import no.slomic.smarthytte.common.formatClock
import no.slomic.smarthytte.common.formatMinutes
import no.slomic.smarthytte.common.minutesOfDay
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.previousMonth
import no.slomic.smarthytte.reservations.Reservation
import kotlin.time.Instant

private val osloTimeZone: TimeZone = TimeZone.of("Europe/Oslo")

fun calculateYearDrivingTimeStats(year: Int, reservations: List<Reservation>): DrivingTimeStatsYear {
    fun getStats(durations: List<Int>) = object {
        val avg = durations.averageOrNullInt()
        val min = durations.minOrNull()
        val max = durations.maxOrNull()
    }

    val toStats = getStats(reservations.toCabinDurationsForYear(year))
    val fromStats = getStats(reservations.fromCabinDurationsForYear(year))

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

fun calculateMonthDrivingTimeStats(year: Int, month: Month, reservations: List<Reservation>): DrivingTimeStatsMonth {
    val (prevYear, prevMonth) = previousMonth(currentYear = year, currentMonth = month)

    fun getStats(durations: List<Int>) = object {
        val avg = durations.averageOrNullInt()
        val min = durations.minOrNull()
        val max = durations.maxOrNull()
    }

    val toCabinStats = getStats(reservations.toCabinDurationsForMonth(year, month))
    val fromCabinStats = getStats(reservations.fromCabinDurationsForMonth(year, month))

    val avgToPrev = reservations.toCabinDurationsForMonth(prevYear, prevMonth).averageOrNullInt()
    val avgFromPrev = reservations.fromCabinDurationsForMonth(prevYear, prevMonth).averageOrNullInt()

    fun diff(current: Int?, prev: Int?) = if (current != null && prev != null) current - prev else null
    val diffTo = diff(toCabinStats.avg, avgToPrev)
    val diffFrom = diff(fromCabinStats.avg, avgFromPrev)

    return DrivingTimeStatsMonth(
        monthNumber = month.ordinal + 1,
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

fun calculateYearDrivingMomentStats(year: Int, reservations: List<Reservation>): DrivingMomentStatsYear {
    val avgDepHome = reservations.avgDepartureHomeMinutes(year)
    val avgArrCabin = reservations.avgArrivalCabinMinutes(year)
    val avgDepCabin = reservations.avgDepartureCabinMinutes(year)
    val avgArrHome = reservations.avgArrivalHomeMinutes(year)

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

fun calculateMonthDrivingMomentStats(
    year: Int,
    month: Month,
    reservations: List<Reservation>,
): DrivingMomentStatsMonth {
    val avgDepHome = reservations.avgDepartureHomeMinutes(year, month)
    val avgArrCabin = reservations.avgArrivalCabinMinutes(year, month)
    val avgDepCabin = reservations.avgDepartureCabinMinutes(year, month)
    val avgArrHome = reservations.avgArrivalHomeMinutes(year, month)

    return DrivingMomentStatsMonth(
        monthNumber = month.ordinal + 1,
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

// --- Private extension functions on List<Reservation> ---

private fun List<Reservation>.toCabinDurationsForYear(year: Int): List<Int> = mapNotNull { reservation ->
    val departure = reservation.toCabinDrivingDepartureTime ?: return@mapNotNull null
    if (departure.year() != year) return@mapNotNull null
    reservation.toCabinDrivingDuration?.inWholeMinutes?.toInt()
}

private fun List<Reservation>.toCabinDurationsForMonth(year: Int, month: Month): List<Int> = mapNotNull { reservation ->
    val departure = reservation.toCabinDrivingDepartureTime ?: return@mapNotNull null
    if (departure.year() != year || departure.month() != month) return@mapNotNull null
    reservation.toCabinDrivingDuration?.inWholeMinutes?.toInt()
}

private fun List<Reservation>.fromCabinDurationsForYear(year: Int): List<Int> = mapNotNull { reservation ->
    val departure = reservation.fromCabinDrivingDepartureTime ?: return@mapNotNull null
    if (departure.year() != year) return@mapNotNull null
    reservation.fromCabinDrivingDuration?.inWholeMinutes?.toInt()
}

private fun List<Reservation>.fromCabinDurationsForMonth(year: Int, month: Month): List<Int> =
    mapNotNull { reservation ->
        val departure = reservation.fromCabinDrivingDepartureTime ?: return@mapNotNull null
        if (departure.year() != year || departure.month() != month) return@mapNotNull null
        reservation.fromCabinDrivingDuration?.inWholeMinutes?.toInt()
    }

private fun List<Reservation>.avgDepartureHomeMinutes(year: Int, month: Month? = null): Int? =
    mapNotNull { reservation ->
        val departure = reservation.toCabinDrivingDepartureTime ?: return@mapNotNull null
        if (!departure.matchesYearMonth(year, month)) return@mapNotNull null
        departure.minutesOfDayOslo()
    }.averageOrNullInt()

private fun List<Reservation>.avgArrivalCabinMinutes(year: Int, month: Month? = null): Int? =
    mapNotNull { reservation ->
        val departure = reservation.toCabinDrivingDepartureTime ?: return@mapNotNull null
        if (!departure.matchesYearMonth(year, month)) return@mapNotNull null
        reservation.toCabinDrivingArrivalTime?.minutesOfDayOslo()
    }.averageOrNullInt()

private fun List<Reservation>.avgDepartureCabinMinutes(year: Int, month: Month? = null): Int? =
    mapNotNull { reservation ->
        val departure = reservation.fromCabinDrivingDepartureTime ?: return@mapNotNull null
        if (!departure.matchesYearMonth(year, month)) return@mapNotNull null
        departure.minutesOfDayOslo()
    }.averageOrNullInt()

private fun List<Reservation>.avgArrivalHomeMinutes(year: Int, month: Month? = null): Int? = mapNotNull { reservation ->
    val departure = reservation.fromCabinDrivingDepartureTime ?: return@mapNotNull null
    if (!departure.matchesYearMonth(year, month)) return@mapNotNull null
    reservation.fromCabinDrivingArrivalTime?.minutesOfDayOslo()
}.averageOrNullInt()

// --- Instant helpers ---

private fun Instant.year(): Int = toLocalDateTime(osloTimeZone).year

private fun Instant.month(): Month = toLocalDateTime(osloTimeZone).month

private fun Instant.matchesYearMonth(year: Int, month: Month?): Boolean =
    year() == year && (month == null || month() == month)

private fun Instant.minutesOfDayOslo(): Int {
    val localTime: LocalTime = toLocalDateTime(osloTimeZone).time
    return localTime.minutesOfDay()
}
