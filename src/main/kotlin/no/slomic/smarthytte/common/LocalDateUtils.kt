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

const val MINUTES_PER_HOUR: Int = 60
const val MONTHS_IN_YEAR: Int = 12

/**
 * Calculates the number of days between the current date and the specified end date.
 * If the end date is before the current date, it returns 0.
 *
 * @param endExclusive the target date to calculate the days until (exclusive)
 * @return the number of days between the current date (inclusive) and the end date (exclusive),
 * or 0 if the end date is in the past
 */
fun LocalDate.daysUntilSafe(endExclusive: LocalDate): Int =
    this.until(endExclusive, DateTimeUnit.DAY).let { if (it < 0) 0 else it }

/**
 * Generates a sequence of dates from the current [LocalDate] instance up to but not including the specified end date.
 *
 * @param endExclusive the exclusive upper limit for the sequence of dates
 * @return a [Sequence] of [LocalDate] instances, starting from the current date and ending before [endExclusive]
 */
fun LocalDate.datesUntil(endExclusive: LocalDate): Sequence<LocalDate> = sequence {
    var d = this@datesUntil
    while (d < endExclusive) {
        yield(d)
        d = d.plus(DatePeriod(days = 1))
    }
}

/**
 * Calculates the ISO 8601 week-based year and week number for the current date.
 *
 * The most important reason to use isoWeekId() instead of just `date.year` and a simple week count is handling
 * the end of the year.
 *
 * Example: December 29, 2024 (Sunday)
 * - Calendar Year: 2024
 * - ISO Week Number: 52
 * - isoWeekId() returns: (2024, 52)
 *
 * Example: December 30, 2024 (Monday)
 * - Calendar Year: 2024
 * - ISO Week Number: 1 (This is the start of the first week of 2025 because ISO weeks start on Mondays and Week 1
 * is the week with the first Thursday of the year).
 * - isoWeekId() returns: (2025, 1)
 *
 * By returning a Pair(2025, 1), the function ensures that December 30, 2024, and January 1, 2025, are grouped
 * into the same unique week ID, even though they belong to different calendar years.
 *
 * This function returns a pair where the first element is the week-based year
 * (which may differ from the calendar year for dates near the end or beginning
 * of a year) and the second element is the ISO week number (ranging from 1 to 53).
 *
 * @return a [Pair] where the first value is the ISO week-based year and
 * the second value is the ISO week number.
 */
fun LocalDate.isoWeekId(): Pair<Int, Int> {
    val javaLocalDate = java.time.LocalDate.of(this.year, this.monthNumber, this.dayOfMonth)
    val weekFields = WeekFields.ISO
    val weekBasedYear = javaLocalDate.get(weekFields.weekBasedYear())
    val weekOfYear = javaLocalDate.get(weekFields.weekOfWeekBasedYear())

    return weekBasedYear to weekOfYear
}

/**
 * Calculates the total number of minutes that have elapsed since midnight for the given `LocalTime`.
 *
 * Example: `LocalTime(12, 30)` returns `750`
 *
 * @return the total minutes of the day, calculated as `hour * 60 + minute`.
 */
fun LocalTime.minutesOfDay(): Int = this.hour * MINUTES_PER_HOUR + this.minute

/**
 * Returns the first day of January in [year].
 *
 * @param year the year for which the January 1st date is to be retrieved
 * @return a [LocalDate] representing January 1st of the given year
 */
fun firstDayOfYear(year: Int): LocalDate = LocalDate(year = year, month = Month.JANUARY, dayOfMonth = 1)

/**
 * Return the first day of January for the year following the provided [year].
 *
 * @param year the base year as an integer (e.g., 2023).
 * @return a [LocalDate] representing January 1st of the year after the provided year.
 */
fun firstDayOfYearAfter(year: Int): LocalDate = firstDayOfYear(year).plus(DatePeriod(years = 1))

/**
 * Calculates the first day of the year preceding the given year.
 *
 * @param year the reference year for which the first day of the previous year is to be calculated
 * @return a [LocalDate] representing January 1st of the year before the given year
 */
fun firstDayOfYearBefore(year: Int): LocalDate = firstDayOfYear(year).minus(DatePeriod(years = 1))

/**
 * Calculates the last day of the year preceding the given year.
 *
 * @param year the year for which the last day of the previous year is to be calculated
 * @return a [LocalDate] representing December 31st of the year before the given year
 */
fun lastDayOfYearBefore(year: Int): LocalDate = firstDayOfYear(year).minus(DatePeriod(days = 1))

/**
 * Returns an interval (start and end dates) representing the 12 months immediately preceding
 * the start of the given [year].
 *
 * @param year the year to calculate the previous 12-month interval for
 * @return a [Pair] where the first element is the start date (Jan 1st of the previous year)
 * and the second element is the end date (Dec 31st of the previous year)
 */
fun lastYearInterval(year: Int): Pair<LocalDate, LocalDate> = firstDayOfYearBefore(year) to lastDayOfYearBefore(year)
