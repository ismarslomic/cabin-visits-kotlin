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
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.days

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
fun nowInUtc(): String {
    val now = Clock.System.now()
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX").withZone(UTC)
    return formatter.format(now.toJavaInstant())
}

fun toInstant(date: LocalDate, time: LocalTime, timeZone: TimeZone) = LocalDateTime(date, time).toInstant(timeZone)
