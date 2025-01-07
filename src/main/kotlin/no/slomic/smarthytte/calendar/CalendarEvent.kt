package no.slomic.smarthytte.calendar

import kotlinx.datetime.Instant

data class CalendarEvent(
    val id: String,
    val start: Instant,
    val end: Instant,
    val guestIds: List<String>,
    val summary: String? = null,
    val description: String? = null,
    val sourceCreated: Instant? = null,
    val sourceUpdated: Instant? = null,
)
