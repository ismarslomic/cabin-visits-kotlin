package no.slomic.smarthytte.calendar

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.toUtcDate
import no.slomic.smarthytte.common.utcDateNow

data class CalendarEvent(
    val id: String,
    val start: Instant,
    val end: Instant,
    val guestIds: List<String>,
    val summary: String? = null,
    val description: String? = null,
    val sourceCreated: Instant? = null,
    val sourceUpdated: Instant? = null,
) {
    val hasStarted: Boolean
        get() = startDate <= utcDateNow()

    val hasEnded: Boolean
        get() = endDate <= utcDateNow()

    val startDate: LocalDate
        get() = start.toUtcDate()

    val endDate: LocalDate
        get() = end.toUtcDate()
}
