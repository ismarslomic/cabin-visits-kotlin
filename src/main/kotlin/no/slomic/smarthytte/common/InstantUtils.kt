@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

val osloTimeZone: TimeZone = TimeZone.of("Europe/Oslo")
val utcTimeZone: TimeZone = TimeZone.UTC

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

/**
 * Combines a [date] and [time] into a [LocalDateTime],
 * then converts it to an [Instant] using the specified [timeZone].
 *
 * This is useful for representing a specific date and time in a given time zone
 * as an exact moment on the UTC timeline.
 *
 * @param date the calendar date (year, month, day) without time or timezone
 * @param time the time-of-day (hour, minute, second) without date or timezone
 * @param timeZone the timezone to interpret the local date and time in
 * @return the [Instant] representing the exact UTC moment for the date and time in the specified zone
 *
 * Example usage:
 * ```
 * val instant = toInstant(LocalDate(2024, 6, 1), LocalTime(15, 0), TimeZone.of("Europe/Oslo"))
 * // instant is the UTC moment corresponding to 2024-06-01 15:00:00 in Oslo timezone
 * ```
 */
fun toInstant(date: LocalDate, time: LocalTime, timeZone: TimeZone) = LocalDateTime(date, time).toInstant(timeZone)

/**
 * Returns now as String in format yyyy-MM-ddTHH:mm:ssX
 */
fun nowIsoUtcString(): String {
    val now = Clock.System.now()
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX").withZone(UTC)
    return formatter.format(now.toJavaInstant())
}

/**
 * Returns Instant as String in ISO format yyyy-MM-ddTHH:mm:ssX
 */
fun Instant.toIsoUtcString(): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX").withZone(UTC)
    return formatter.format(this.toJavaInstant())
}

fun Instant.toUtcDate(): LocalDate = toLocalDateTime(utcTimeZone).date

fun Instant.toOsloDate(): LocalDate = toLocalDateTime(osloTimeZone).date

fun Instant.minutesOfDayOslo(): Int = toLocalDateTime(osloTimeZone).time.minutesOfDay()

/** The current UTC date according to [clock]; pass a fixed clock in tests. */
fun utcDateNow(clock: Clock = Clock.System): LocalDate = clock.now().toLocalDateTime(utcTimeZone).date

fun osloDateNow(): LocalDate = Clock.System.now().toLocalDateTime(osloTimeZone).date
