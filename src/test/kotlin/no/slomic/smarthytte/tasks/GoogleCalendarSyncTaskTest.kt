package no.slomic.smarthytte.tasks

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Events
import io.kotest.core.spec.style.StringSpec
import io.ktor.util.logging.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.slomic.smarthytte.services.GoogleCalendarService

class GoogleCalendarSyncTaskTest :
    StringSpec({

        val mockCalendarApiClient = mockk<Calendar>(relaxed = true)
        val mockEventsList = mockk<Calendar.Events.List>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val syncFromDateTime = DateTime("2024-12-29T00:00:00Z")

        val googleCalendarService = GoogleCalendarService(
            calendarApiClient = mockCalendarApiClient,
            logger = logger,
            calendarId = "test-calendar",
            syncFromDateTime = syncFromDateTime,
        )

        // Set up generic mocking for Calendar.Events.List no matter if its full or incremental sync
        every { mockCalendarApiClient.events().list(any()) } returns mockEventsList
        every { mockEventsList.setTimeMin(any()) } returns mockEventsList
        every { mockEventsList.setSyncToken(any()) } returns mockEventsList

        "should handle no new events" {
            every { mockEventsList.execute() } returns Events().apply { items = emptyList() }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            verify { logger.info("No new events to sync.") }
        }

        "should handle new events" {
            val newEvent = Event().apply {
                id = "1"
                summary = "New Event"
                description = "Test event"
                start = EventDateTime().apply { dateTime = DateTime("2024-12-30T10:00:00Z") }
                end = EventDateTime().apply { dateTime = DateTime("2024-12-30T11:00:00Z") }
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(newEvent) }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            verify {
                logger.info(
                    "Id: 1, " +
                        "Event: New Event, " +
                        "Description: Test event, " +
                        "Start: 2024-12-30T10:00:00.000Z, " +
                        "End: 2024-12-30T11:00:00.000Z",
                )
            }
        }

        "should handle changed events" {
            val changedEvent = Event().apply {
                id = "2"
                summary = "Updated Event"
                description = "Updated description"
                start = EventDateTime().apply { dateTime = DateTime("2024-12-31T10:00:00Z") }
                end = EventDateTime().apply { dateTime = DateTime("2024-12-31T11:00:00Z") }
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(changedEvent) }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            verify {
                logger.info(
                    "Id: 2, " +
                        "Event: Updated Event, " +
                        "Description: Updated description, " +
                        "Start: 2024-12-31T10:00:00.000Z, " +
                        "End: 2024-12-31T11:00:00.000Z",
                )
            }
        }

        "should handle deleted events" {
            val deletedEvent = Event().apply {
                id = "3"
                status = "cancelled"
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(deletedEvent) }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            verify { logger.info("Event with id 3 was deleted.") }
        }
    })
