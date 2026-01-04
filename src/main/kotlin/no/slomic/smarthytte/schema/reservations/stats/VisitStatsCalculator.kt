package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import no.slomic.smarthytte.common.MONTHS_IN_YEAR
import no.slomic.smarthytte.common.averageRounded1OrNull
import no.slomic.smarthytte.common.datesUntil
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.common.isoWeekId
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.countOccupiedDaysInWindow
import no.slomic.smarthytte.reservations.diffVisitsCurrentYearWithLast12Months
import no.slomic.smarthytte.reservations.findMonthWithLongestStay

private const val PERCENT_FACTOR: Double = 100.0
private const val DAYS_IN_MONTHLY_COMPARE_WINDOW: Int = 30
private const val DAY_OFFSET_PREVIOUS: Int = 1

fun buildYearStats(context: YearStatsContext): YearReservationStats {
    val year = context.year
    val jan1 = firstDayOfYear(year)

    val occupancy = computeYearOccupancy(context)
    val guestStats = computeYearGuestStats(context)

    return YearReservationStats(
        year = year,
        totalVisits = context.yearReservations.size,
        comparedToLast12Months = context.allReservations.diffVisitsCurrentYearWithLast12Months(
            currentYear = year,
            visitsCurrentYear = context.yearReservations.size,
        ),
        averageMonthlyVisits = (context.yearReservations.size.toDouble() / MONTHS_IN_YEAR.toDouble()).round1(),
        totalStayDays = occupancy.totalStayDays,
        comparedStayDaysToLast12Months =
        occupancy.totalStayDays - context.allReservations.countOccupiedDaysInWindow(
            jan1.minus(DatePeriod(months = MONTHS_IN_YEAR)),
            jan1,
        ),
        averageMonthlyStayDays = (occupancy.totalStayDays.toDouble() / MONTHS_IN_YEAR.toDouble()).round1(),
        percentDaysOccupied = occupancy.percentDaysOccupied,
        percentWeeksOccupied = occupancy.percentWeeksOccupied,
        percentMonthsOccupied = occupancy.percentMonthsOccupied,
        monthMostVisits = context.countsByMonth.maxByOrNull { it.value }
            ?.let { (m, c) -> MonthCount(monthNumber = m.value, monthName = monthNameOf(m), count = c) },
        monthFewestVisits = context.countsByMonth.minByOrNull { it.value }
            ?.let { (m, c) -> MonthCount(monthNumber = m.value, monthName = monthNameOf(m), count = c) },
        monthWithLongestStay = context.yearReservations.findMonthWithLongestStay()?.let {
            MonthStay(
                monthNumber = it.first.value,
                monthName = monthNameOf(it.first),
                days = it.second,
            )
        },
        months = context.monthStats,
        topGuestByDays = guestStats.topGuestByDays,
        newGuests = guestStats.newGuests,
        guests = guestStats.allGuestsSorted,
        drivingTime = computeYearDrivingStats(year, context.cabinTrips),
        drivingMoments = computeYearDrivingMomentStats(year, context.cabinTrips),
    )
}

fun buildMonthStats(context: MonthStatsContext): List<MonthReservationStats> = Month.entries.map { month ->
    val monthlyReservations = context.yearReservations.filter { it.startDate.month == month }
    val dates = MonthDates(context.year, month)
    val occupancy = computeMonthOccupancy(context.allReservations, dates)
    val comparison = calculateMonthlyVisitDeltas(
        context = context,
        month = month,
        dates = dates,
        totalVisits = monthlyReservations.size,
    )

    MonthReservationStats(
        monthNumber = month.value,
        monthName = monthNameOf(month),
        totalVisits = monthlyReservations.size,
        comparedToLast30Days = comparison.comparedToLast30Days,
        comparedToSameMonthLastYear = comparison.comparedToSameMonthLastYear,
        comparedToYearToDateAverage = comparison.comparedToYearToDateAverage,
        minStayDays = monthlyReservations.map { it.startDate.daysUntilSafe(it.endDate) }.minOrNull(),
        maxStayDays = monthlyReservations.map { it.startDate.daysUntilSafe(it.endDate) }.maxOrNull(),
        avgStayDays = monthlyReservations.map { it.startDate.daysUntilSafe(it.endDate) }
            .averageRounded1OrNull(),
        percentDaysOccupied = occupancy.percentDaysOccupied,
        percentWeeksOccupied = occupancy.percentWeeksOccupied,
        guests = calculateMonthlyGuestStats(context, dates, monthlyReservations),
        drivingTime = computeMonthDrivingStats(context.year, month, context.cabinTrips),
        drivingMoments = computeMonthDrivingMomentStats(context.year, month, context.cabinTrips),
    )
}

data class YearOccupancy(
    val totalStayDays: Int,
    val percentDaysOccupied: Double,
    val percentWeeksOccupied: Double,
    val percentMonthsOccupied: Double,
)

fun computeYearOccupancy(context: YearStatsContext): YearOccupancy {
    val jan1 = firstDayOfYear(context.year)
    val jan1Next = firstDayOfYearAfter(context.year)
    val daysInYear = jan1.daysUntilSafe(jan1Next)
    val allWeeksInYear = jan1.datesUntil(jan1Next).map { it.isoWeekId() }.toSet().size

    val occupiedDays: Set<LocalDate> = context.yearReservations
        .asSequence()
        .map { r ->
            val start = maxOf(r.startDate, jan1)
            val endEx = minOf(r.endDate, jan1Next)
            if (start < endEx) start.datesUntil(endEx).toList() else emptyList()
        }
        .flatten()
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

    val occupiedMonths: Int = occupiedDays.map { it.monthNumber }.toSet().size
    val percentMonthsOccupied = (occupiedMonths.toDouble() / MONTHS_IN_YEAR.toDouble() * PERCENT_FACTOR).round1()

    return YearOccupancy(totalStayDays, percentDaysOccupied, percentWeeksOccupied, percentMonthsOccupied)
}

data class MonthOccupancy(val percentDaysOccupied: Double, val percentWeeksOccupied: Double)

fun computeMonthOccupancy(allReservations: List<Reservation>, dates: MonthDates): MonthOccupancy {
    val allDaysInMonth = dates.firstOfMonth.datesUntil(dates.firstOfNextMonth).toList()
    val daysInMonth = allDaysInMonth.size
    val totalWeeksInMonth = allDaysInMonth.map { it.isoWeekId() }.toSet().size

    val occupiedDaysInMonth: Set<LocalDate> = allReservations
        .asSequence()
        .map { r ->
            val start = maxOf(r.startDate, dates.firstOfMonth)
            val endExclusive = minOf(r.endDate, dates.firstOfNextMonth)
            if (start < endExclusive) start.datesUntil(endExclusive).toList() else emptyList()
        }
        .flatten()
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

/**
 * Calculates the differences in visit counts for a given month compared to
 * the previous 30 days, the same month in the previous year, and the
 * year-to-date average.
 *
 * @param context The context containing statistics, reservations, and other
 *        data needed for the calculation.
 * @param month The month for which the visit deltas are being calculated.
 * @param dates The computed dates related to the given month, such as the
 *        first day of the month and the first day of the next month.
 * @param totalVisits The total number of visits recorded for the given month.
 * @return A `MonthlyVisitDeltas` object containing the calculated deltas:
 *         - Difference compared to the last 30 days.
 *         - Difference compared to the same month last year.
 *         - Difference compared to the year-to-date average.
 */
fun calculateMonthlyVisitDeltas(
    context: MonthStatsContext,
    month: Month,
    dates: MonthDates,
    totalVisits: Int,
): MonthlyVisitDeltas {
    val prev30DaysCount = context.allReservations.count {
        val lastDayPrev = dates.firstOfMonth.minus(DatePeriod(days = DAY_OFFSET_PREVIOUS))
        val startWindow = dates.firstOfMonth.minus(DatePeriod(days = DAYS_IN_MONTHLY_COMPARE_WINDOW))
        it.startDate in startWindow..lastDayPrev
    }

    val sameMonthLastYearCount = context.allReservations.count {
        it.startDate.year == (context.year - 1) && it.startDate.month == month
    }

    val yearToDateTotal = Month.entries
        .filter { it <= month }
        .sumOf { context.countsByMonth[it] ?: 0 }

    val yearToDateAverage = yearToDateTotal.toDouble() / month.value

    return MonthlyVisitDeltas(
        comparedToLast30Days = totalVisits - prev30DaysCount,
        comparedToSameMonthLastYear = totalVisits - sameMonthLastYearCount,
        comparedToYearToDateAverage = (totalVisits.toDouble() - yearToDateAverage).round1(),
    )
}

data class MonthlyVisitDeltas(
    val comparedToLast30Days: Int,
    val comparedToSameMonthLastYear: Int,
    val comparedToYearToDateAverage: Double,
)
