package no.slomic.smarthytte.calendar

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.util.Data
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.slomic.smarthytte.common.osloTimeZone
import java.io.IOException
import kotlin.time.Instant

private const val NO_NOTIFICATION = "none"
private const val CONFIRMED = "confirmed"

/** A Google Calendar client and the calendar it writes to. */
class CalendarClient(val calendar: Calendar, val calendarId: String)

/**
 * Writes to Google Calendar with a [CalendarClient] that [clientProvider] creates on the first call and again after a
 * failure, so a missing or invalid credentials file only fails the calls (they are retried by the outbox) and never
 * the application.
 */
class GoogleCalendarGateway(private val clientProvider: () -> CalendarClient, private val title: String) :
    CalendarGateway {
    @Volatile
    private var client: CalendarClient? = null

    override suspend fun insert(eventId: String, times: CalendarEventTimes): CalendarCallResult = call {
        calendar.events().insert(calendarId, insertEvent(eventId, title, times)).setSendUpdates(NO_NOTIFICATION)
            .execute()
    }

    override suspend fun patch(eventId: String, times: CalendarEventTimes, restore: Boolean): CalendarCallResult =
        call {
            calendar.events().patch(calendarId, eventId, patchEvent(times, restore)).setSendUpdates(NO_NOTIFICATION)
                .execute()
        }

    override suspend fun delete(eventId: String): CalendarCallResult = call {
        calendar.events().delete(calendarId, eventId).setSendUpdates(NO_NOTIFICATION).execute()
    }

    private suspend fun call(block: CalendarClient.() -> Unit): CalendarCallResult = withContext(Dispatchers.IO) {
        try {
            (client ?: clientProvider().also { client = it }).block()
            CalendarCallResult.Success
        } catch (e: GoogleJsonResponseException) {
            CalendarCallResult.Failure(e.statusCode, e.details?.errors?.firstOrNull()?.reason, e.message.orEmpty())
        } catch (e: IOException) {
            // No answer from Google: the network, or credentials that cannot be loaded
            client = null
            CalendarCallResult.Failure(null, null, e.message.orEmpty())
        }
    }
}

/** A new timed event. The id is the reservation id, which makes a retried insert idempotent. */
internal fun insertEvent(eventId: String, title: String, times: CalendarEventTimes): Event =
    Event().setId(eventId).setSummary(title).setStart(timed(times.start)).setEnd(timed(times.end))

/**
 * Only start and end change; the title stays. `date` is sent as null, because an old event can be an all-day event
 * (it has `start.date`) and a timed one must not have both. A patch merges nested objects, so without the null the
 * date would survive.
 */
internal fun patchEvent(times: CalendarEventTimes, restore: Boolean): Event {
    val event = Event()
        .setStart(timed(times.start).setDate(Data.NULL_DATE_TIME))
        .setEnd(timed(times.end).setDate(Data.NULL_DATE_TIME))

    return if (restore) event.setStatus(CONFIRMED) else event
}

private fun timed(instant: Instant): EventDateTime =
    EventDateTime().setDateTime(DateTime(instant.toEpochMilliseconds())).setTimeZone(osloTimeZone.id)
