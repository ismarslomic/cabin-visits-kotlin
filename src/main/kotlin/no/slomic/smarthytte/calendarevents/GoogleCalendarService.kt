package no.slomic.smarthytte.calendarevents

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.Events
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.osloTimeZone
import no.slomic.smarthytte.common.readSummaryToGuestFromJsonFile
import no.slomic.smarthytte.common.toInstant
import no.slomic.smarthytte.properties.GoogleCalendarPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import java.io.FileInputStream

class GoogleCalendarService(
    private val reservationRepository: ReservationRepository,
    private val syncCheckpointService: SyncCheckpointService,
    private val googleCalendarPropertiesHolder: GoogleCalendarPropertiesHolder =
        loadProperties<GoogleCalendarPropertiesHolder>(),
    private val calendarApiClient: Calendar = createCalendarApiClient(googleCalendarPropertiesHolder),
) {
    private val logger: Logger = KtorSimpleLogger(GoogleCalendarService::class.java.name)
    private val googleProperties = googleCalendarPropertiesHolder.googleCalendar
    private val calendarId: String = googleProperties.calendarId
    private val syncFromDateTime: DateTime = DateTime(googleProperties.syncFromDateTime)
    private val summaryToGuestFilePath: String = googleProperties.summaryToGuestFilePath
    private val mapping: Map<String, List<String>> = readSummaryToGuestFromJsonFile(summaryToGuestFilePath)

    companion object {
        private const val STATUS_CODE_GONE = 410
    }

    suspend fun fetchGoogleCalendarEvents() {
        val request: Calendar.Events.List

        // Load the sync token stored from the last execution, if any.
        val syncTokenKey: String? = syncCheckpointService.checkpointForGoogleCalendarEvents()
        if (syncTokenKey == null) {
            logger.info("Performing full fetch for google calendar $calendarId from date $syncFromDateTime.")
            request = createFullSyncRequest()
        } else {
            logger.info("Performing incremental sync for google calendar $calendarId.")
            request = createIncrementalSyncRequest(syncTokenKey)
        }

        // Retrieve the events, one page at a time.
        var pageToken: String? = null
        val persistenceResults: MutableList<PersistenceResult> = mutableListOf()
        var events: Events? = null
        var totalNumberOfEvents = 0
        do {
            request.pageToken = pageToken

            try {
                events = request.execute()
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == STATUS_CODE_GONE) {
                    // A 410 status code, "Gone", indicates that the sync token is invalid.
                    logger.warn("Invalid sync token, clearing event store and re-syncing.")
                    syncCheckpointService.deleteCheckpointForGoogleCalendarEvents()
                    fetchGoogleCalendarEvents()
                } else {
                    throw e
                }
            }

            val eventItems: List<Event>? = events?.items
            totalNumberOfEvents += eventItems?.size ?: 0
            if (!eventItems.isNullOrEmpty()) {
                persistenceResults.addAll(storeReservations(eventItems))
            }

            pageToken = events?.nextPageToken
        } while (pageToken != null)

        // Store the sync token from the last request to be used during the next execution.
        if (events?.nextSyncToken != null) {
            syncCheckpointService.addOrUpdateCheckpointForGoogleCalendarEvents(value = events.nextSyncToken)
        }

        val addedCount = persistenceResults.count { it == PersistenceResult.ADDED }
        val deletedCount = persistenceResults.count { it == PersistenceResult.DELETED }
        val updatedCount = persistenceResults.count { it == PersistenceResult.UPDATED }
        val noActionCount = persistenceResults.count { it == PersistenceResult.NO_ACTION }

        logger.info(
            "Fetching google events complete. " +
                "Total google events in response: $totalNumberOfEvents, added: $addedCount, " +
                "deleted: $deletedCount, updated: $updatedCount, no actions: $noActionCount",
        )
    }

    private suspend fun storeReservations(eventItems: List<Event>): List<PersistenceResult> {
        val persistenceResults: MutableList<PersistenceResult> = mutableListOf()
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
                persistenceResults.add(reservationRepository.addOrUpdate(reservation))
            } else {
                persistenceResults.add(reservationRepository.deleteReservation(event.id))
            }
        }

        return persistenceResults
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

private fun createCalendarApiClient(googleCalendarPropertiesHolder: GoogleCalendarPropertiesHolder): Calendar {
    val googleProperties = googleCalendarPropertiesHolder.googleCalendar
    val googleServiceAccountKeys = FileInputStream(googleProperties.credentialsFilePath)

    // Load service account credentials
    val credentials: GoogleCredentials =
        ServiceAccountCredentials.fromStream(googleServiceAccountKeys)
            .createScoped(listOf(CalendarScopes.CALENDAR_READONLY))

    val requestInitializer = HttpCredentialsAdapter(credentials)

    // Build the Calendar API client
    return Calendar.Builder(
        /* transport = */
        GoogleNetHttpTransport.newTrustedTransport(),
        /* jsonFactory = */
        GsonFactory.getDefaultInstance(),
        /* httpRequestInitializer = */
        requestInitializer,
    ).setApplicationName("Cabin Visits Kotlin").build()
}
