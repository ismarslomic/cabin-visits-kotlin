// TODO remove this after refactoring
@file:Suppress("ktlint:standard:filename")

package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import no.slomic.smarthytte.common.round1

private const val DAYS_IN_MONTHLY_COMPARE_WINDOW: Int = 30
private const val DAY_OFFSET_PREVIOUS: Int = 1

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
        comparedToYtdAvg = (totalVisits.toDouble() - yearToDateAverage).round1(),
    )
}

data class MonthlyVisitDeltas(
    val comparedToLast30Days: Int,
    val comparedToSameMonthLastYear: Int,
    val comparedToYtdAvg: Double,
)
