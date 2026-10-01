@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import no.slomic.smarthytte.common.HOURS_PER_DAY
import no.slomic.smarthytte.common.MINUTES_PER_HOUR
import no.slomic.smarthytte.common.averageOrNullInt
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.formatClock
import no.slomic.smarthytte.common.formatMinutes
import no.slomic.smarthytte.common.minutesOfDayOslo
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.common.toOsloDate
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.statistics.model.MonthDrivingDistanceStats
import no.slomic.smarthytte.statistics.model.YearDrivingDistanceStats
import kotlin.time.Instant

private const val MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY

fun calculateYearDrivingTimeStats(
    fromDate: LocalDate,
    toDateExclusive: LocalDate,
    reservations: List<Reservation>,
): YearDrivingTimeStats {
    fun getStats(durations: List<Int>) = object {
        val avg = durations.averageOrNullInt()
        val min = durations.minOrNull()
        val max = durations.maxOrNull()
    }

    val toStats = getStats(reservations.toCabinDurations(fromDate, toDateExclusive))
    val fromStats = getStats(reservations.fromCabinDurations(fromDate, toDateExclusive))

    return YearDrivingTimeStats(
        year = fromDate.year,
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

fun calculateMonthDrivingTimeStats(
    fromDate: LocalDate,
    toDateExclusive: LocalDate,
    reservations: List<Reservation>,
): MonthDrivingTimeStats {
    val prevFromDate = fromDate.minus(DatePeriod(months = 1))

    fun getStats(durations: List<Int>) = object {
        val avg = durations.averageOrNullInt()
        val min = durations.minOrNull()
        val max = durations.maxOrNull()
    }

    val toCabinStats = getStats(reservations.toCabinDurations(fromDate, toDateExclusive))
    val fromCabinStats = getStats(reservations.fromCabinDurations(fromDate, toDateExclusive))

    val avgToPrev = reservations.toCabinDurations(prevFromDate, fromDate).averageOrNullInt()
    val avgFromPrev = reservations.fromCabinDurations(prevFromDate, fromDate).averageOrNullInt()

    fun diff(current: Int?, prev: Int?) = if (current != null && prev != null) current - prev else null
    val diffTo = diff(toCabinStats.avg, avgToPrev)
    val diffFrom = diff(fromCabinStats.avg, avgFromPrev)

    return MonthDrivingTimeStats(
        monthNumber = fromDate.month.ordinal + 1,
        monthName = monthNameOf(fromDate.month),
        year = fromDate.year,
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

fun calculateYearDrivingMomentStats(
    fromDate: LocalDate,
    toDateExclusive: LocalDate,
    reservations: List<Reservation>,
): YearDrivingMomentStats {
    val avgDepHome = reservations.avgDepartureHomeMinutes(fromDate, toDateExclusive)
    val avgArrCabin = reservations.avgArrivalCabinMinutes(fromDate, toDateExclusive)
    val avgDepCabin = reservations.avgDepartureCabinMinutes(fromDate, toDateExclusive)
    val avgArrHome = reservations.avgArrivalHomeMinutes(fromDate, toDateExclusive)

    return YearDrivingMomentStats(
        year = fromDate.year,
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
    fromDate: LocalDate,
    toDateExclusive: LocalDate,
    reservations: List<Reservation>,
): MonthDrivingMomentStats {
    val avgDepHome = reservations.avgDepartureHomeMinutes(fromDate, toDateExclusive)
    val avgArrCabin = reservations.avgArrivalCabinMinutes(fromDate, toDateExclusive)
    val avgDepCabin = reservations.avgDepartureCabinMinutes(fromDate, toDateExclusive)
    val avgArrHome = reservations.avgArrivalHomeMinutes(fromDate, toDateExclusive)

    return MonthDrivingMomentStats(
        monthNumber = fromDate.month.ordinal + 1,
        monthName = monthNameOf(fromDate.month),
        year = fromDate.year,
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

fun calculateYearDrivingDistanceStats(
    fromDate: LocalDate,
    toDateExclusive: LocalDate,
    reservations: List<Reservation>,
): YearDrivingDistanceStats? = calculateDrivingDistanceStats(fromDate, toDateExclusive, reservations)
    ?.let { s ->
        YearDrivingDistanceStats(
            totalToCabinKm = s.totalToCabinKm,
            minToCabinKm = s.minToCabinKm,
            maxToCabinKm = s.maxToCabinKm,
            avgToCabinKm = s.avgToCabinKm,
            totalFromCabinKm = s.totalFromCabinKm,
            minFromCabinKm = s.minFromCabinKm,
            maxFromCabinKm = s.maxFromCabinKm,
            avgFromCabinKm = s.avgFromCabinKm,
            totalAtCabinKm = s.totalAtCabinKm,
            minAvgSpeedToCabinKmh = s.minAvgSpeedToCabinKmh,
            maxAvgSpeedToCabinKmh = s.maxAvgSpeedToCabinKmh,
            avgAvgSpeedToCabinKmh = s.avgAvgSpeedToCabinKmh,
            minAvgSpeedFromCabinKmh = s.minAvgSpeedFromCabinKmh,
            maxAvgSpeedFromCabinKmh = s.maxAvgSpeedFromCabinKmh,
            avgAvgSpeedFromCabinKmh = s.avgAvgSpeedFromCabinKmh,
        )
    }

fun calculateMonthDrivingDistanceStats(
    fromDate: LocalDate,
    toDateExclusive: LocalDate,
    reservations: List<Reservation>,
): MonthDrivingDistanceStats? = calculateDrivingDistanceStats(fromDate, toDateExclusive, reservations)
    ?.let { s ->
        MonthDrivingDistanceStats(
            totalToCabinKm = s.totalToCabinKm,
            minToCabinKm = s.minToCabinKm,
            maxToCabinKm = s.maxToCabinKm,
            avgToCabinKm = s.avgToCabinKm,
            totalFromCabinKm = s.totalFromCabinKm,
            minFromCabinKm = s.minFromCabinKm,
            maxFromCabinKm = s.maxFromCabinKm,
            avgFromCabinKm = s.avgFromCabinKm,
            totalAtCabinKm = s.totalAtCabinKm,
            minAvgSpeedToCabinKmh = s.minAvgSpeedToCabinKmh,
            maxAvgSpeedToCabinKmh = s.maxAvgSpeedToCabinKmh,
            avgAvgSpeedToCabinKmh = s.avgAvgSpeedToCabinKmh,
            minAvgSpeedFromCabinKmh = s.minAvgSpeedFromCabinKmh,
            maxAvgSpeedFromCabinKmh = s.maxAvgSpeedFromCabinKmh,
            avgAvgSpeedFromCabinKmh = s.avgAvgSpeedFromCabinKmh,
        )
    }

// --- Private helpers ---

private data class DrivingDistanceRawStats(
    val totalToCabinKm: Double,
    val minToCabinKm: Double?,
    val maxToCabinKm: Double?,
    val avgToCabinKm: Double?,
    val totalFromCabinKm: Double,
    val minFromCabinKm: Double?,
    val maxFromCabinKm: Double?,
    val avgFromCabinKm: Double?,
    val totalAtCabinKm: Double,
    val minAvgSpeedToCabinKmh: Double?,
    val maxAvgSpeedToCabinKmh: Double?,
    val avgAvgSpeedToCabinKmh: Double?,
    val minAvgSpeedFromCabinKmh: Double?,
    val maxAvgSpeedFromCabinKmh: Double?,
    val avgAvgSpeedFromCabinKmh: Double?,
)

private fun calculateDrivingDistanceStats(
    fromDate: LocalDate,
    toDateExclusive: LocalDate,
    reservations: List<Reservation>,
): DrivingDistanceRawStats? {
    val matched = reservations.filter { r ->
        r.toCabinDrivingDepartureDate?.inPeriod(fromDate, toDateExclusive) == true ||
            r.fromCabinDrivingDepartureDate?.inPeriod(fromDate, toDateExclusive) == true ||
            r.atCabinVehicleTrips.any { it.startDate.inPeriod(fromDate, toDateExclusive) }
    }
    if (matched.isEmpty()) return null

    val toDistances = matched.mapNotNull { r ->
        r.toCabinDrivingDepartureDate?.takeIf { it.inPeriod(fromDate, toDateExclusive) }
            ?.let { r.toCabinDrivingDistanceKm }
    }
    val fromDistances = matched.mapNotNull { r ->
        r.fromCabinDrivingDepartureDate?.takeIf { it.inPeriod(fromDate, toDateExclusive) }
            ?.let { r.fromCabinDrivingDistanceKm }
    }
    val atTotal = matched.mapNotNull { r ->
        r.atCabinVehicleTrips.filter { it.startDate.inPeriod(fromDate, toDateExclusive) }
            .takeIf { it.isNotEmpty() }?.sumOf { it.distance }
    }.sum()
    val toSpeeds = matched.mapNotNull { r ->
        r.toCabinDrivingDepartureDate?.takeIf { it.inPeriod(fromDate, toDateExclusive) }
            ?.let { r.toCabinAvgSpeedKmh }
    }
    val fromSpeeds = matched.mapNotNull { r ->
        r.fromCabinDrivingDepartureDate?.takeIf { it.inPeriod(fromDate, toDateExclusive) }
            ?.let { r.fromCabinAvgSpeedKmh }
    }

    return DrivingDistanceRawStats(
        totalToCabinKm = toDistances.sum().round1(),
        minToCabinKm = toDistances.minOrNull()?.round1(),
        maxToCabinKm = toDistances.maxOrNull()?.round1(),
        avgToCabinKm = toDistances.takeIf { it.isNotEmpty() }?.average()?.round1(),
        totalFromCabinKm = fromDistances.sum().round1(),
        minFromCabinKm = fromDistances.minOrNull()?.round1(),
        maxFromCabinKm = fromDistances.maxOrNull()?.round1(),
        avgFromCabinKm = fromDistances.takeIf { it.isNotEmpty() }?.average()?.round1(),
        totalAtCabinKm = atTotal.round1(),
        minAvgSpeedToCabinKmh = toSpeeds.minOrNull()?.round1(),
        maxAvgSpeedToCabinKmh = toSpeeds.maxOrNull()?.round1(),
        avgAvgSpeedToCabinKmh = toSpeeds.takeIf { it.isNotEmpty() }?.average()?.round1(),
        minAvgSpeedFromCabinKmh = fromSpeeds.minOrNull()?.round1(),
        maxAvgSpeedFromCabinKmh = fromSpeeds.maxOrNull()?.round1(),
        avgAvgSpeedFromCabinKmh = fromSpeeds.takeIf { it.isNotEmpty() }?.average()?.round1(),
    )
}

private fun LocalDate.inPeriod(fromDate: LocalDate, toDateExclusive: LocalDate): Boolean =
    this in fromDate..<toDateExclusive

private fun List<Reservation>.toCabinDurations(fromDate: LocalDate, toDateExclusive: LocalDate): List<Int> =
    mapNotNull { reservation ->
        val departure = reservation.toCabinDrivingDepartureDate ?: return@mapNotNull null
        if (!departure.inPeriod(fromDate, toDateExclusive)) return@mapNotNull null
        reservation.toCabinDrivingDuration?.inWholeMinutes?.toInt()
    }

private fun List<Reservation>.fromCabinDurations(fromDate: LocalDate, toDateExclusive: LocalDate): List<Int> =
    mapNotNull { reservation ->
        val departure = reservation.fromCabinDrivingDepartureDate ?: return@mapNotNull null
        if (!departure.inPeriod(fromDate, toDateExclusive)) return@mapNotNull null
        reservation.fromCabinDrivingDuration?.inWholeMinutes?.toInt()
    }

private fun List<Reservation>.avgDepartureHomeMinutes(fromDate: LocalDate, toDateExclusive: LocalDate): Int? =
    mapNotNull { reservation ->
        val departure = reservation.toCabinDrivingDepartureDate ?: return@mapNotNull null
        if (!departure.inPeriod(fromDate, toDateExclusive)) return@mapNotNull null
        reservation.toCabinDrivingDepartureTime?.minutesOfDayOslo()
    }.averageOrNullInt()

private fun List<Reservation>.avgArrivalCabinMinutes(fromDate: LocalDate, toDateExclusive: LocalDate): Int? =
    mapNotNull { reservation ->
        val departure = reservation.toCabinDrivingDepartureDate ?: return@mapNotNull null
        if (!departure.inPeriod(fromDate, toDateExclusive)) return@mapNotNull null
        val departureTime = reservation.toCabinDrivingDepartureTime ?: return@mapNotNull null
        reservation.toCabinDrivingArrivalTime?.minutesSinceDepartureDayOslo(departureTime)
    }.averageOrNullInt()?.rem(MINUTES_PER_DAY)

private fun List<Reservation>.avgDepartureCabinMinutes(fromDate: LocalDate, toDateExclusive: LocalDate): Int? =
    mapNotNull { reservation ->
        val departure = reservation.fromCabinDrivingDepartureDate ?: return@mapNotNull null
        if (!departure.inPeriod(fromDate, toDateExclusive)) return@mapNotNull null
        reservation.fromCabinDrivingDepartureTime?.minutesOfDayOslo()
    }.averageOrNullInt()

private fun List<Reservation>.avgArrivalHomeMinutes(fromDate: LocalDate, toDateExclusive: LocalDate): Int? =
    mapNotNull { reservation ->
        val departure = reservation.fromCabinDrivingDepartureDate ?: return@mapNotNull null
        if (!departure.inPeriod(fromDate, toDateExclusive)) return@mapNotNull null
        val departureTime = reservation.fromCabinDrivingDepartureTime ?: return@mapNotNull null
        reservation.fromCabinDrivingArrivalTime?.minutesSinceDepartureDayOslo(departureTime)
    }.averageOrNullInt()?.rem(MINUTES_PER_DAY)

// Minutes from midnight (Oslo) of the departure day, so an arrival after midnight counts as e.g. 24:04, not 00:04.
private fun Instant.minutesSinceDepartureDayOslo(departureTime: Instant): Int =
    departureTime.toOsloDate().daysUntilSafe(toOsloDate()) * MINUTES_PER_DAY + minutesOfDayOslo()
