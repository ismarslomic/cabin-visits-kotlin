package no.slomic.smarthytte.services

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.Events
import io.ktor.util.logging.Logger

class GoogleCalendarService(
    private val calendarApiClient: Calendar,
    private val logger: Logger,
    private val calendarId: String,
) {
    private var syncTokenKey: String? = null

    companion object {
        private const val STATUS_CODE_GONE = 410
    }

    fun synchronizeCalendarEvents(fromDate: String) {
        val request: Calendar.Events.List

        // Load the sync token stored from the last execution, if any.
        if (syncTokenKey == null) {
            logger.info("Performing full sync.")
            request = createFullSyncRequest(fromDate)
        } else {
            logger.info("Performing incremental sync.")
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
                    synchronizeCalendarEvents(fromDate)
                } else {
                    throw e
                }
            }

            val eventItems: List<Event>? = events?.items
            if (eventItems.isNullOrEmpty()) {
                logger.info("No new events to sync.")
            } else {
                logEventItems(eventItems)
            }

            pageToken = events?.nextPageToken
        } while (pageToken != null)

        // Store the sync token from the last request to be used during the next execution.
        syncTokenKey = events?.nextSyncToken

        logger.info("Sync complete.")
    }

    private fun logEventItems(eventItems: List<Event>) {
        for (event in eventItems) {
            if (event.status != "cancelled") {
                logger.info(
                    "Id: ${event.id}, " +
                        "Event: ${event.summary}, " +
                        "Description: ${event.description}, " +
                        "Start: ${event.start.dateTime ?: event.start.date}, " +
                        "End: ${event.end.dateTime ?: event.end.date}",
                )
            } else {
                logger.info("Event with id ${event.id} was deleted.")
            }
        }
    }

    private fun createFullSyncRequest(fromDateTime: String) = calendarApiClient
        .events()
        .list(calendarId)
        .setTimeMin(DateTime(fromDateTime))

    private fun createIncrementalSyncRequest() = calendarApiClient
        .events()
        .list(calendarId)
        .setSyncToken(syncTokenKey)
}
