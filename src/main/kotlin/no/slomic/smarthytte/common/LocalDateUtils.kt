package no.slomic.smarthytte.common

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.until
import java.time.temporal.WeekFields

const val MONTHS_IN_YEAR: Int = 12

fun LocalDate.daysUntilSafe(endExclusive: LocalDate): Int =
    this.until(endExclusive, DateTimeUnit.DAY).let { if (it < 0) 0 else it.toInt() }

fun LocalDate.datesUntil(endExclusive: LocalDate): Sequence<LocalDate> = sequence {
    var d = this@datesUntil
    while (d < endExclusive) {
        yield(d)
        d = d.plus(DatePeriod(days = 1))
    }
}

// Returns ISO week-based year and week number.
// Handles year boundaries correctly (e.g. Dec 30 may be week 1 of next year).
fun LocalDate.isoWeekId(): Pair<Int, Int> {
    val javaLocalDate = java.time.LocalDate.of(this.year, this.month.ordinal + 1, this.day)
    val weekFields = WeekFields.ISO
    val weekBasedYear = javaLocalDate.get(weekFields.weekBasedYear())
    val weekOfYear = javaLocalDate.get(weekFields.weekOfWeekBasedYear())
    return weekBasedYear to weekOfYear
}

fun LocalTime.minutesOfDay(): Int = this.hour * MINUTES_PER_HOUR + this.minute

fun firstDayOfYear(year: Int): LocalDate = LocalDate(year = year, month = Month.JANUARY, day = 1)

fun firstDayOfYearAfter(year: Int): LocalDate = firstDayOfYear(year).plus(DatePeriod(years = 1))

fun firstDayOfYearBefore(year: Int): LocalDate = firstDayOfYear(year).minus(DatePeriod(years = 1))

fun lastDayOfYearBefore(year: Int): LocalDate = firstDayOfYear(year).minus(DatePeriod(days = 1))

fun lastYearInterval(year: Int): Pair<LocalDate, LocalDate> = firstDayOfYearBefore(year) to lastDayOfYearBefore(year)
