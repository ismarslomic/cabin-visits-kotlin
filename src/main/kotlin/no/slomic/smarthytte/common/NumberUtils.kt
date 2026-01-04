package no.slomic.smarthytte.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rounds the value of the current [Double] to one decimal place using the HALF_UP rounding mode.
 *
 * Examples:
 * - `1.23.round1()` returns `1.2`
 * - `1.25.round1()` returns `1.3`
 * - `1.35.round1()` returns `1.4`
 * - `1.27.round1()` returns `1.3`
 *
 * @return a [Double] value rounded to one decimal place
 */
fun Double.round1(): Double = BigDecimal(this.toString()).setScale(1, RoundingMode.HALF_UP).toDouble()

/**
 * Computes the average of the integers in the list and rounds the result to one decimal place
 * using the HALF_UP rounding mode.
 *
 * If the list is empty, returns null.
 *
 * Examples of HALF_UP rounding:
 * - 1.25 -> 1.3
 * - 1.24 -> 1.2
 *
 * @return the average value of the list rounded to one decimal place as a [Double], or null if the list is empty
 */
fun List<Int>.averageRounded1OrNull(): Double? = if (isEmpty()) null else (sum().toDouble() / size).round1()

/**
 * Calculates the average of the integers in the list and returns it as an integer.
 * If the list is empty, returns null.
 *
 * @return The average value of the integers in the list as an integer or null if the list is empty.
 */
fun List<Int>.averageOrNullInt(): Int? = if (isEmpty()) null else (sum().toDouble() / size.toDouble()).toInt()
