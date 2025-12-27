package no.slomic.smarthytte.common

import kotlin.math.round

const val ONE_DECIMAL_FACTOR: Double = 10.0

fun Double.round1(): Double = round(this * ONE_DECIMAL_FACTOR) / ONE_DECIMAL_FACTOR
