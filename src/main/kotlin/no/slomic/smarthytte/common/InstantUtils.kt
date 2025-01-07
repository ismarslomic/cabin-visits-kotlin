package no.slomic.smarthytte.common

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import java.time.temporal.ChronoUnit

/**
 * Truncates the Instant to millis.
 * Since Kotlin Instant does not provide this truncation, we need first to
 * convert the Kotlin Instant to Java Instant, then truncate, then convert back to Kotlin Instant.
 *
 * Example
 * * in: 2024-01-06T16:00:00.123456Z
 * * out: 2024-01-06T16:00:00.123Z
 */
fun Instant.truncatedToMillis(): Instant = this.toJavaInstant().truncatedTo(ChronoUnit.MILLIS).toKotlinInstant()
