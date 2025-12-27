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
