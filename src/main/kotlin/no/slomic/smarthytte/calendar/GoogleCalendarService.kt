package no.slomic.smarthytte.calendar

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.Events
import io.ktor.util.logging.Logger
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import no.slomic.smarthytte.common.readSummaryToGuestFromJsonFile
import no.slomic.smarthytte.common.truncatedToMillis
import kotlin.time.Duration.Companion.nanoseconds

class GoogleCalendarService(
    private val calendarApiClient: Calendar,
    private val calendarRepository: CalendarEventRepository,
    private val logger: Logger,
    private val calendarId: String,
    private val syncFromDateTime: DateTime,
    summaryToGuestFilePath: String,
) {
    private var syncTokenKey: String? = null
    private val calendarTimezone: TimeZone = TimeZone.of("CET")
    private val mapping: Map<String, List<String>> = readSummaryToGuestFromJsonFile(summaryToGuestFilePath)

    companion object {
        private const val STATUS_CODE_GONE = 410
    }

    suspend fun synchronizeCalendarEvents() {
        val request: Calendar.Events.List

        // Load the sync token stored from the last execution, if any.
        if (syncTokenKey == null) {
            logger.info("Performing full sync for calendar $calendarId from date $syncFromDateTime.")
            request = createFullSyncRequest()
        } else {
            logger.info("Performing incremental sync for calendar $calendarId.")
            request = createIncrementalSyncRequest()
        }

        // Retrieve the events, one page at a time.
        var pageToken: String? = null
        var events: Events? = null
        do {
            request.setPageToken(pageToken)

            try {
                events = request.execute()
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == STATUS_CODE_GONE) {
                    // A 410 status code, "Gone", indicates that the sync token is invalid.
                    logger.info("Invalid sync token, clearing event store and re-syncing.")
                    syncTokenKey = null
                    synchronizeCalendarEvents()
                } else {
                    throw e
                }
            }

            val eventItems: List<Event>? = events?.items
            if (eventItems.isNullOrEmpty()) {
                logger.info("No new events to sync.")
            } else {
                storeUpdates(eventItems)
            }

            pageToken = events?.nextPageToken
        } while (pageToken != null)

        // Store the sync token from the last request to be used during the next execution.
        syncTokenKey = events?.nextSyncToken

        logger.info("Sync complete.")
    }

    private suspend fun storeUpdates(eventItems: List<Event>) {
        for (event in eventItems) {
            if (event.status != "cancelled") {
                val calendarEvent = CalendarEvent(
                    id = event.id,
                    summary = event.summary,
                    description = event.description,
                    start = startToInstant(event.start.date, event.start.dateTime),
                    end = endToInstant(event.end.date, event.end.dateTime),
                    guestIds = summaryToGuestIds(event.summary),
                    sourceCreated = event.created?.let { Instant.parse(it.toStringRfc3339()) },
                    sourceUpdated = event.updated?.let { Instant.parse(it.toStringRfc3339()) },
                )

                calendarRepository.addOrUpdate(calendarEvent)
            } else {
                calendarRepository.deleteEvent(event.id)
            }
        }

        logger.info("Saved ${eventItems.size} event(s)")
    }

    private fun createFullSyncRequest() = calendarApiClient
        .events()
        .list(calendarId)
        .setTimeMin(syncFromDateTime)

    private fun createIncrementalSyncRequest() = calendarApiClient
        .events()
        .list(calendarId)
        .setSyncToken(syncTokenKey)

    /**
     * Note! The start time is the **inclusive** start time of the event
     */
    private fun startToInstant(date: DateTime?, dateTime: DateTime?): Instant {
        require(date != null || dateTime != null) { "Both start date and start datetime cannot be null" }

        return if (date != null) {
            LocalDate.parse(date.toStringRfc3339()).atStartOfDayIn(calendarTimezone).truncatedToMillis()
        } else {
            Instant.parse(dateTime!!.toStringRfc3339())
        }
    }

    /**
     * Note! The end time is the **exclusive** end time of the event
     */
    private fun endToInstant(date: DateTime?, dateTime: DateTime?): Instant {
        require(date != null || dateTime != null) { "Both end date and end datetime cannot be null" }

        return if (date != null) {
            LocalDate.parse(date.toStringRfc3339()).atStartOfDayIn(calendarTimezone).minus(1.nanoseconds)
                .truncatedToMillis()
        } else {
            Instant.parse(dateTime!!.toStringRfc3339())
        }
    }

    /**
     * Extract guest names from the Calendar event summary to a list of Guest ids. Returns empty list if none of the
     * guests could be extracted.
     */
    private fun summaryToGuestIds(summary: String): List<String> = mapping[summary] ?: listOf()
}
