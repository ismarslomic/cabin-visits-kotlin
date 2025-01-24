package no.slomic.smarthytte.calendarevents

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.Events
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.osloTimeZone
import no.slomic.smarthytte.common.readSummaryToGuestFromJsonFile
import no.slomic.smarthytte.common.toInstant
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository

class GoogleCalendarService(
    private val calendarApiClient: Calendar,
    private val reservationRepository: ReservationRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val calendarId: String,
    private val syncFromDateTime: DateTime,
    summaryToGuestFilePath: String,
) {
    private val logger: Logger = KtorSimpleLogger(GoogleCalendarService::class.java.name)
    private val mapping: Map<String, List<String>> = readSummaryToGuestFromJsonFile(summaryToGuestFilePath)

    companion object {
        private const val STATUS_CODE_GONE = 410
    }

    suspend fun synchronizeCalendarEvents() {
        val request: Calendar.Events.List

        // Load the sync token stored from the last execution, if any.
        val syncTokenKey = googleCalendarRepository.syncToken()
        if (syncTokenKey == null) {
            logger.info("Performing full sync for calendar $calendarId from date $syncFromDateTime.")
            request = createFullSyncRequest()
        } else {
            logger.info("Performing incremental sync for calendar $calendarId.")
            request = createIncrementalSyncRequest(syncTokenKey)
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
                    googleCalendarRepository.deleteSyncToken()
                    synchronizeCalendarEvents()
                } else {
                    throw e
                }
            }

            val eventItems: List<Event>? = events?.items
            if (eventItems.isNullOrEmpty()) {
                logger.info("No new events to sync.")
            } else {
                storeReservations(eventItems)
            }

            pageToken = events?.nextPageToken
        } while (pageToken != null)

        // Store the sync token from the last request to be used during the next execution.
        if (events?.nextSyncToken != null) {
            googleCalendarRepository.addOrUpdateSyncToken(events.nextSyncToken)
        }

        logger.info("Sync complete.")
    }

    private suspend fun storeReservations(eventItems: List<Event>) {
        for (event in eventItems) {
            if (event.status != "cancelled") {
                val reservation = Reservation(
                    id = event.id,
                    summary = event.summary,
                    description = event.description,
                    startTime = startToInstant(event.start.date, event.start.dateTime),
                    endTime = endToInstant(event.end.date, event.end.dateTime),
                    guestIds = summaryToGuestIds(event.summary),
                    sourceCreatedTime = event.created?.let { Instant.parse(it.toStringRfc3339()) },
                    sourceUpdatedTime = event.updated?.let { Instant.parse(it.toStringRfc3339()) },
                )

                reservationRepository.addOrUpdate(reservation)
            } else {
                reservationRepository.deleteReservation(event.id)
            }
        }

        logger.info("Saved ${eventItems.size} reservation(s)")
    }

    private fun createFullSyncRequest() = calendarApiClient
        .events()
        .list(calendarId)
        .setTimeMin(syncFromDateTime)

    private fun createIncrementalSyncRequest(syncTokenKey: String) = calendarApiClient
        .events()
        .list(calendarId)
        .setSyncToken(syncTokenKey)

    /**
     * Note! The start time is the **inclusive** start time of the event
     */
    private fun startToInstant(date: DateTime?, dateTime: DateTime?): Instant {
        require(date != null || dateTime != null) { "Both start date and start datetime cannot be null" }

        return if (date != null) {
            toInstant(date = date, hour = 18, timeZone = osloTimeZone)
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
            toInstant(date = date, hour = 15, timeZone = osloTimeZone, minusDays = 1)
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
