package no.slomic.smarthytte.common

import com.google.api.client.util.DateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.days

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
 * Creates a date time by using the provided [date] and [hour] with 00 as minutes and 00 as seconds in
 * the provided [timeZone] and returns an [Instant].
 *
 * Reduces the date time by [minusDays].
 */
fun toInstant(date: DateTime, hour: Int, timeZone: TimeZone, minusDays: Int = 0): Instant {
    val endDate = LocalDate.parse(date.toStringRfc3339())
    val endDateTime = endDate.atTime(hour = hour, minute = 0, second = 0)
    return endDateTime.toInstant(timeZone).minus(minusDays.days)
}

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

fun Instant.toUtcDate(): LocalDate = toLocalDateTime(utcTimeZone).date

fun Instant.toOsloDate(): LocalDate = toLocalDateTime(osloTimeZone).date

fun utcDateNow(): LocalDate = Clock.System.now().toLocalDateTime(utcTimeZone).date

fun osloDateNow(): LocalDate = Clock.System.now().toLocalDateTime(osloTimeZone).date
