package no.slomic.smarthytte.statistics.calculator

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
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

private const val PERCENT_FACTOR: Double = 100.0
private const val DAYS_IN_MONTHLY_COMPARE_WINDOW: Int = 30
private const val DAY_OFFSET_PREVIOUS: Int = 1

data class MonthDates(
    val year: Int,
    val month: Month,
    val firstOfMonth: LocalDate = firstDateOfThisMonth(year, month),
    val firstOfNextMonth: LocalDate = firstDateOfNextMonth(year, month),
)

data class YearOccupancy(
    val totalStayDays: Int,
    val percentDaysOccupied: Double,
    val percentWeeksOccupied: Double,
    val percentMonthsOccupied: Double,
)

fun computeYearOccupancy(year: Int, yearReservations: List<Reservation>): YearOccupancy {
    val jan1 = firstDayOfYear(year)
    val jan1Next = firstDayOfYearAfter(year)
    val daysInYear = jan1.daysUntilSafe(jan1Next)
    val allWeeksInYear = jan1.datesUntil(jan1Next).map { it.isoWeekId() }.toSet().size

    val occupiedDays: Set<LocalDate> = yearReservations
        .asSequence().flatMap { r ->
            val start = maxOf(r.startDate, jan1)
            val endEx = minOf(r.endDate, jan1Next)
            if (start < endEx) start.datesUntil(endEx).toList() else emptyList()
        }
        .toSet()

    val totalStayDays = occupiedDays.size
    val percentDaysOccupied = if (daysInYear > 0) {
        (totalStayDays.toDouble() / daysInYear.toDouble() * PERCENT_FACTOR).round1()
    } else {
        0.0
    }
    val occupiedWeeksInYear = occupiedDays.map { it.isoWeekId() }.toSet().size
    val percentWeeksOccupied = if (allWeeksInYear > 0) {
        (occupiedWeeksInYear.toDouble() / allWeeksInYear.toDouble() * PERCENT_FACTOR).round1()
    } else {
        0.0
    }
    val occupiedMonths = occupiedDays.map { it.month.ordinal + 1 }.toSet().size
    val percentMonthsOccupied = (occupiedMonths.toDouble() / MONTHS_IN_YEAR.toDouble() * PERCENT_FACTOR).round1()

    return YearOccupancy(totalStayDays, percentDaysOccupied, percentWeeksOccupied, percentMonthsOccupied)
}

data class MonthOccupancy(val percentDaysOccupied: Double, val percentWeeksOccupied: Double)

fun computeMonthOccupancy(allReservations: List<Reservation>, dates: MonthDates): MonthOccupancy {
    val allDaysInMonth = dates.firstOfMonth.datesUntil(dates.firstOfNextMonth).toList()
    val daysInMonth = allDaysInMonth.size
    val totalWeeksInMonth = allDaysInMonth.map { it.isoWeekId() }.toSet().size

    val occupiedDaysInMonth: Set<LocalDate> = allReservations
        .asSequence().flatMap { r ->
            val start = maxOf(r.startDate, dates.firstOfMonth)
            val endExclusive = minOf(r.endDate, dates.firstOfNextMonth)
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
    val visitsComparedToLast30Days: Int,
    val visitsComparedToSameMonthLastYear: Int,
    val visitsComparedToYearToDateAverage: Double,
)

fun calculateMonthlyVisitDeltas(
    allReservations: List<Reservation>,
    countsByMonth: Map<Month, Int>,
    dates: MonthDates,
    totalVisits: Int,
): MonthlyVisitDeltas {
    val last30DaysRange = with(dates.firstOfMonth) {
        val startWindow = minus(DatePeriod(days = DAYS_IN_MONTHLY_COMPARE_WINDOW))
        val lastDayPrev = minus(DatePeriod(days = DAY_OFFSET_PREVIOUS))
        startWindow..lastDayPrev
    }

    val prev30DaysCount = allReservations.count { it.startDate in last30DaysRange }
    val sameMonthLastYearCount = allReservations.count {
        it.startDate.year == (dates.year - 1) && it.startDate.month == dates.month
    }

    val yearToDateTotal = Month.entries
        .filter { it <= dates.month }
        .sumOf { countsByMonth[it] ?: 0 }
    val yearToDateAverage = yearToDateTotal.toDouble() / (dates.month.ordinal + 1)

    return MonthlyVisitDeltas(
        visitsComparedToLast30Days = totalVisits - prev30DaysCount,
        visitsComparedToSameMonthLastYear = totalVisits - sameMonthLastYearCount,
        visitsComparedToYearToDateAverage = (totalVisits.toDouble() - yearToDateAverage).round1(),
    )
}
