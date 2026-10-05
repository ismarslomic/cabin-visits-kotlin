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

// Hand-written instead of java.time with a Norwegian locale: the GraalVM native image only includes English locale
// data by default, so a Norwegian locale would silently fall back to English month names.
private val NORWEGIAN_SHORT_MONTH_NAMES =
    listOf("jan", "feb", "mars", "april", "mai", "juni", "juli", "aug", "sept", "okt", "nov", "des")

fun norwegianShortMonthNameOf(month: Month): String = NORWEGIAN_SHORT_MONTH_NAMES[month.ordinal]

private val NORWEGIAN_MONTH_NAMES = listOf(
    "januar",
    "februar",
    "mars",
    "april",
    "mai",
    "juni",
    "juli",
    "august",
    "september",
    "oktober",
    "november",
    "desember",
)

/** Full lower-case month name, e.g. "september". */
fun norwegianMonthNameOf(month: Month): String = NORWEGIAN_MONTH_NAMES[month.ordinal]

/** Short month and year, e.g. "sept 2020". */
fun LocalDate.norwegianShortMonthYear(): String = "${norwegianShortMonthNameOf(month)} $year"

fun previousMonth(currentYear: Int, currentMonth: Month): Pair<Int, Month> =
    firstDateOfThisMonth(currentYear, currentMonth)
        .minus(DatePeriod(months = 1))
        .run { Pair(year, month) }

fun firstDateOfThisMonth(year: Int, month: Month): LocalDate = LocalDate(year, month, 1)

fun firstDateOfNextMonth(currentYear: Int, currentMonth: Month): LocalDate =
    firstDateOfThisMonth(currentYear, currentMonth).plus(DatePeriod(months = 1))
