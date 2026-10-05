package no.slomic.smarthytte.common

import java.math.BigDecimal
import java.math.RoundingMode

private const val THOUSANDS_GROUP_SIZE = 3

/** Rounds to one decimal, with halves rounded up (away from zero). */
fun Double.round1(): Double = BigDecimal(this.toString()).setScale(1, RoundingMode.HALF_UP).toDouble()

/** Average of the values with the decimals truncated, or null when the list is empty. */
fun List<Int>.averageOrNullInt(): Int? = if (isEmpty()) null else (sum().toDouble() / size.toDouble()).toInt()

/** Formats with a decimal comma, for example 1.5 becomes "1,5". */
fun Double.toNorwegianDecimal(): String = toString().replace('.', ',')

/** Formats with a space between each group of three digits, for example 1234567 becomes "1 234 567". */
fun Int.groupThousands(): String = toString().reversed().chunked(THOUSANDS_GROUP_SIZE).joinToString(" ").reversed()
