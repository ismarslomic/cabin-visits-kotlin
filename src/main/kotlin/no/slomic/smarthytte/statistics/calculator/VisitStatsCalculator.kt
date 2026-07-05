package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import no.slomic.smarthytte.common.MONTHS_IN_YEAR
import no.slomic.smarthytte.common.datesUntil
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.firstDateOfNextMonth
import no.slomic.smarthytte.common.firstDateOfThisMonth
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.common.isoWeekId
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.countOccupiedDaysInWindow
import no.slomic.smarthytte.reservations.countOccupiedNightsInWindow
import no.slomic.smarthytte.statistics.model.DaysStats
import no.slomic.smarthytte.statistics.model.NightsStats

private const val PERCENT_FACTOR: Double = 100.0
private const val DAY_OFFSET_PREVIOUS: Int = 1

data class MonthDates(
    val year: Int,
    val month: Month,
    val firstOfMonth: LocalDate = firstDateOfThisMonth(year, month),
    val firstOfNextMonth: LocalDate = firstDateOfNextMonth(year, month),
) {
    val firstOfPreviousMonth: LocalDate = firstOfMonth.minus(DatePeriod(months = 1))
    val lastOfPreviousMonth: LocalDate = firstOfMonth.minus(DatePeriod(days = DAY_OFFSET_PREVIOUS))
}

data class YearOccupancy(val dayOccupancy: Double, val weekOccupancy: Double, val monthOccupancy: Double)

fun computeYearOccupancy(year: Int, allReservations: List<Reservation>): YearOccupancy {
    val jan1 = firstDayOfYear(year)
    val jan1Next = firstDayOfYearAfter(year)
    val daysInYear = jan1.daysUntilSafe(jan1Next)
    val allWeeksInYear = jan1.datesUntil(jan1Next).map { it.isoWeekId() }.toSet().size

    val occupiedDays: Set<LocalDate> = allReservations
        .asSequence().flatMap { r ->
            val start = maxOf(r.startDate, jan1)
            val endEx = minOf(r.endDate.plus(DatePeriod(days = 1)), jan1Next)
            if (start < endEx) start.datesUntil(endEx).toList() else emptyList()
        }
        .toSet()

    val dayOccupancy = if (daysInYear > 0) {
        (occupiedDays.size.toDouble() / daysInYear.toDouble() * PERCENT_FACTOR).round1()
    } else {
        0.0
    }
    val occupiedWeeksInYear = occupiedDays.map { it.isoWeekId() }.toSet().size
    val weekOccupancy = if (allWeeksInYear > 0) {
        (occupiedWeeksInYear.toDouble() / allWeeksInYear.toDouble() * PERCENT_FACTOR).round1()
    } else {
        0.0
    }
    val occupiedMonths = occupiedDays.map { it.month.ordinal + 1 }.toSet().size
    val monthOccupancy = (occupiedMonths.toDouble() / MONTHS_IN_YEAR.toDouble() * PERCENT_FACTOR).round1()

    return YearOccupancy(dayOccupancy, weekOccupancy, monthOccupancy)
}

data class MonthOccupancy(val percentDaysOccupied: Double, val percentWeeksOccupied: Double)

fun computeMonthOccupancy(allReservations: List<Reservation>, dates: MonthDates): MonthOccupancy {
    val allDaysInMonth = dates.firstOfMonth.datesUntil(dates.firstOfNextMonth).toList()
    val daysInMonth = allDaysInMonth.size
    val totalWeeksInMonth = allDaysInMonth.map { it.isoWeekId() }.toSet().size

    val occupiedDaysInMonth: Set<LocalDate> = allReservations
        .asSequence().flatMap { r ->
            val start = maxOf(r.startDate, dates.firstOfMonth)
            val endExclusive = minOf(r.endDate.plus(DatePeriod(days = 1)), dates.firstOfNextMonth)
            if (start < endExclusive) start.datesUntil(endExclusive).toList() else emptyList()
        }
        .toSet()

    val percentDaysOccupied = if (daysInMonth > 0) {
        (occupiedDaysInMonth.size.toDouble() / daysInMonth.toDouble() * PERCENT_FACTOR).round1()
    } else {
        0.0
    }
    val occupiedWeeksInMonth = occupiedDaysInMonth.map { it.isoWeekId() }.toSet().size
    val percentWeeksOccupied = if (totalWeeksInMonth > 0) {
        (occupiedWeeksInMonth.toDouble() / totalWeeksInMonth.toDouble() * PERCENT_FACTOR).round1()
    } else {
        0.0
    }
    return MonthOccupancy(percentDaysOccupied, percentWeeksOccupied)
}

data class MonthlyVisitDeltas(
    val comparedToPreviousMonth: Int,
    val comparedToSameMonthLastYear: Int,
    val comparedToYearToDateAverage: Double,
)

fun calculateMonthlyVisitDeltas(
    allReservations: List<Reservation>,
    countsByMonth: Map<Month, Int>,
    dates: MonthDates,
    totalVisits: Int,
): MonthlyVisitDeltas {
    val prevMonthCount = allReservations.count {
        it.startDate in dates.firstOfPreviousMonth..dates.lastOfPreviousMonth
    }
    val sameMonthLastYearCount = allReservations.count {
        it.startDate.year == (dates.year - 1) && it.startDate.month == dates.month
    }

    val yearToDateTotal = Month.entries
        .filter { it <= dates.month }
        .sumOf { countsByMonth[it] ?: 0 }
    val yearToDateAverage = yearToDateTotal.toDouble() / (dates.month.ordinal + 1)

    return MonthlyVisitDeltas(
        comparedToPreviousMonth = totalVisits - prevMonthCount,
        comparedToSameMonthLastYear = totalVisits - sameMonthLastYearCount,
        comparedToYearToDateAverage = (totalVisits.toDouble() - yearToDateAverage).round1(),
    )
}

fun calculateMonthlyDaysStats(allReservations: List<Reservation>, dates: MonthDates): DaysStats {
    val totalDays = allReservations.countOccupiedDaysInWindow(dates.firstOfMonth, dates.firstOfNextMonth)

    val overlappingReservations = allReservations.filter {
        it.startDate < dates.firstOfNextMonth && it.endDate >= dates.firstOfMonth
    }
    val perReservationDays = overlappingReservations.map {
        it.daysInPeriod(dates.firstOfMonth, dates.firstOfNextMonth)
    }
    val minDays = perReservationDays.minOrNull()
    val maxDays = perReservationDays.maxOrNull()
    val avgDays = perReservationDays.takeIf { it.isNotEmpty() }?.average()?.round1()

    val daysPrevMonth = allReservations.countOccupiedDaysInWindow(dates.firstOfPreviousMonth, dates.firstOfMonth)

    val sameMonthLastYearStart = dates.firstOfMonth.minus(DatePeriod(years = 1))
    val sameMonthLastYearEnd = dates.firstOfNextMonth.minus(DatePeriod(years = 1))
    val daysSameMonthLastYear =
        allReservations.countOccupiedDaysInWindow(sameMonthLastYearStart, sameMonthLastYearEnd)

    return DaysStats(
        totalDays = totalDays,
        minDays = minDays,
        maxDays = maxDays,
        avgDays = avgDays,
        comparedToPreviousMonth = totalDays - daysPrevMonth,
        comparedToSameMonthLastYear = totalDays - daysSameMonthLastYear,
    )
}

fun calculateMonthlyNightsStats(allReservations: List<Reservation>, dates: MonthDates): NightsStats {
    val totalNights = allReservations.countOccupiedNightsInWindow(dates.firstOfMonth, dates.firstOfNextMonth)

    val overlappingReservations = allReservations.filter {
        it.startDate < dates.firstOfNextMonth && it.endDate >= dates.firstOfMonth
    }
    val perReservationNights = overlappingReservations.map {
        it.nightsInPeriod(dates.firstOfMonth, dates.firstOfNextMonth)
    }
    val minNights = perReservationNights.minOrNull()
    val maxNights = perReservationNights.maxOrNull()
    val avgNights = perReservationNights.takeIf { it.isNotEmpty() }?.average()?.round1()

    val nightsPrevMonth = allReservations.countOccupiedNightsInWindow(dates.firstOfPreviousMonth, dates.firstOfMonth)

    val sameMonthLastYearStart = dates.firstOfMonth.minus(DatePeriod(years = 1))
    val sameMonthLastYearEnd = dates.firstOfNextMonth.minus(DatePeriod(years = 1))
    val nightsSameMonthLastYear =
        allReservations.countOccupiedNightsInWindow(sameMonthLastYearStart, sameMonthLastYearEnd)

    return NightsStats(
        totalNights = totalNights,
        minNights = minNights,
        maxNights = maxNights,
        avgNights = avgNights,
        comparedToPreviousMonth = totalNights - nightsPrevMonth,
        comparedToSameMonthLastYear = totalNights - nightsSameMonthLastYear,
    )
}
