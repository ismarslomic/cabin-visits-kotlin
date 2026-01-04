package no.slomic.smarthytte.common

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.time.format.TextStyle
import java.util.Locale

/**
 * Returns the full name of the given month in English.
 *
 * @param month the month to retrieve the name for
 * @return the full name of the month in English, e.g. "January"
 */
fun monthNameOf(month: Month): String = month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ENGLISH)

/**
 * Calculates the previous month and its corresponding year based on the provided year and month.
 *
 * @param currentYear the current year
 * @param currentMonth the current month (as a [Month] enum)
 * @return a [Pair] where the first value is the year and the second value is the previous month (as a [Month] enum)
 */
fun previousMonth(currentYear: Int, currentMonth: Month): Pair<Int, Month> =
    firstDateOfThisMonth(currentYear, currentMonth)
        .minus(DatePeriod(months = 1))
        .run { Pair(year, month) }

/**
 * Returns the first day of the given month.
 *
 * @param year the year of the month
 * @param month (e.g., January and December)
 * @return the first day of the month as a LocalDate
 */
fun firstDateOfThisMonth(year: Int, month: Month): LocalDate = LocalDate(year, month, 1)

/**
 * Calculates the first date of the next month based on the provided current year and month.
 *
 * @param currentYear the current year as an integer (e.g., 2023).
 * @param currentMonth the current month (e.g., January and December).
 * @return a [LocalDate] representing the first day of the next month. Returns January [currentYear] + 1 if the
 * current month is December.
 */
fun firstDateOfNextMonth(currentYear: Int, currentMonth: Month): LocalDate =
    firstDateOfThisMonth(currentYear, currentMonth).plus(DatePeriod(months = 1))
