package no.slomic.smarthytte.skistats

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.common.osloTimeZone
import kotlin.time.Clock

object SkiStatsSchedule {
    private const val MORNING_START_HOUR = 8
    private const val MORNING_START_MINUTES = 0
    private const val EVENING_START_HOUR = 16
    private const val EVENING_START_MINUTES = 0

    /**
     * Determines if the current time is within schedule
     */
    fun shouldPollNow(
        timeZone: TimeZone = osloTimeZone,
        start: LocalTime = LocalTime(MORNING_START_HOUR, MORNING_START_MINUTES),
        end: LocalTime = LocalTime(EVENING_START_HOUR, EVENING_START_MINUTES),
    ): Boolean {
        val now = Clock.System.now().toLocalDateTime(timeZone)
        val dayOk = now.date.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val timeOk = now.time in start..end
        return dayOk && timeOk
    }
}
