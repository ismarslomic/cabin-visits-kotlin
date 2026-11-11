package no.slomic.smarthytte.calendar

import kotlin.time.Instant

/** The start and end of a calendar event: the times of the reservation. */
data class CalendarEventTimes(val start: Instant, val end: Instant)

/** What Google answered to one call. */
sealed interface CalendarCallResult {
    data object Success : CalendarCallResult

    /**
     * The call failed. [status] is the HTTP status, or null when there was no answer (network, credentials).
     * [reason] is the Google error reason (for example `rateLimitExceeded`), null when the answer was not JSON.
     */
    data class Failure(val status: Int?, val reason: String?, val message: String) : CalendarCallResult
}

/**
 * The calls to Google Calendar that the outbox needs. `Calendar` is a concrete Google class, so the drain and the
 * tests use this seam. Events are timed, have no attendees and never send a notification mail.
 */
interface CalendarGateway {
    /** Creates the event with [eventId] and the fixed title. */
    suspend fun insert(eventId: String, times: CalendarEventTimes): CalendarCallResult

    /** Moves the event. [restore] also sets its status to confirmed, which brings back a cancelled event. */
    suspend fun patch(eventId: String, times: CalendarEventTimes, restore: Boolean): CalendarCallResult

    suspend fun delete(eventId: String): CalendarCallResult
}
