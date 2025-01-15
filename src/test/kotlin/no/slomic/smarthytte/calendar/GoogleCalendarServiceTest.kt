package no.slomic.smarthytte.calendar

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Events
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.util.logging.KtorSimpleLogger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.slomic.smarthytte.BaseDbTest

class GoogleCalendarServiceTest :
    BaseDbTest({
        fun getResourceFilePath(fileName: String) = this::class.java.classLoader.getResource(fileName)!!.toURI().path

        val mockCalendarApiClient = mockk<Calendar>(relaxed = true)
        val mockEventsList = mockk<Calendar.Events.List>(relaxed = true)
        val syncFromDateTime = DateTime("2024-12-29T00:00:00Z")
        val calendarRepository: CalendarEventRepository = SqliteCalendarEventRepository()
        val summaryToGuestFilePath = getResourceFilePath("summaryToGuestIds.json")

        val googleCalendarService = GoogleCalendarService(
            calendarApiClient = mockCalendarApiClient,
            logger = KtorSimpleLogger(GoogleCalendarService::class.java.name),
            calendarId = "test-calendar",
            syncFromDateTime = syncFromDateTime,
            calendarRepository = calendarRepository,
            summaryToGuestFilePath = summaryToGuestFilePath,
        )

        // Set up generic mocking for Calendar.Events.List no matter if its full or incremental sync
        every { mockCalendarApiClient.events().list(any()) } returns mockEventsList
        every { mockEventsList.setTimeMin(any()) } returns mockEventsList
        every { mockEventsList.setSyncToken(any()) } returns mockEventsList

        "empty list of new events should not store events to database" {
            every { mockEventsList.execute() } returns Events().apply { items = emptyList() }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            calendarRepository.allEvents().shouldBeEmpty()
        }

        "list with new events should be stored to database" {
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

            calendarRepository.allEvents() shouldHaveSize 1
        }

        "list with updated events should be stored to database" {
            val newEvent = Event().apply {
                id = "2"
                summary = "New Event"
                description = null
                start = EventDateTime().apply { dateTime = DateTime("2024-12-31T10:00:00Z") }
                end = EventDateTime().apply { dateTime = DateTime("2024-12-31T11:00:00Z") }
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(newEvent) }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            val changedEvent = newEvent.apply {
                summary = "Updated Event"
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(changedEvent) }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            val allEvents = calendarRepository.allEvents()
            allEvents shouldHaveSize 1
            allEvents.first().id shouldBe changedEvent.id
            allEvents.first().summary shouldBe changedEvent.summary
        }

        "list with deleted events should be deleted from database" {
            val newEvent = Event().apply {
                id = "1"
                summary = "New Event"
                description = null
                start = EventDateTime().apply { dateTime = DateTime("2024-12-31T10:00:00Z") }
                end = EventDateTime().apply { dateTime = DateTime("2024-12-31T11:00:00Z") }
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(newEvent) }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            calendarRepository.allEvents() shouldHaveSize 1

            val deletedEvent = newEvent.apply {
                status = "cancelled"
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(deletedEvent) }

            runBlocking {
                googleCalendarService.synchronizeCalendarEvents()
            }

            calendarRepository.allEvents().shouldBeEmpty()
        }
    })
