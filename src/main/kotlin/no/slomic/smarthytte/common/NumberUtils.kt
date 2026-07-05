package no.slomic.smarthytte.common

import java.math.BigDecimal
import java.math.RoundingMode

fun Double.round1(): Double = BigDecimal(this.toString()).setScale(1, RoundingMode.HALF_UP).toDouble()

fun List<Int>.averageOrNullInt(): Int? = if (isEmpty()) null else (sum().toDouble() / size.toDouble()).toInt()
