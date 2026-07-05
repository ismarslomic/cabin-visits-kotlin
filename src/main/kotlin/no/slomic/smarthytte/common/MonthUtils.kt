package no.slomic.smarthytte.common
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.time.format.TextStyle
import java.util.Locale
import java.time.Month as JavaMonth

fun monthNameOf(month: Month): String =
    JavaMonth.of(month.ordinal + 1).getDisplayName(TextStyle.FULL_STANDALONE, Locale.ENGLISH)

fun previousMonth(currentYear: Int, currentMonth: Month): Pair<Int, Month> =
    firstDateOfThisMonth(currentYear, currentMonth)
        .minus(DatePeriod(months = 1))
        .run { Pair(year, month) }

fun firstDateOfThisMonth(year: Int, month: Month): LocalDate = LocalDate(year, month, 1)

fun firstDateOfNextMonth(currentYear: Int, currentMonth: Month): LocalDate =
    firstDateOfThisMonth(currentYear, currentMonth).plus(DatePeriod(months = 1))
