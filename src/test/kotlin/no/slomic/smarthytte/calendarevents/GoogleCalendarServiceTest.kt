package no.slomic.smarthytte.calendarevents

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Events
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.slomic.smarthytte.properties.GoogleCalendarProperties
import no.slomic.smarthytte.properties.GoogleCalendarPropertiesHolder
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.getResourceFilePath

class GoogleCalendarServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val mockCalendarApiClient = mockk<Calendar>(relaxed = true)
        val mockEventsList = mockk<Calendar.Events.List>(relaxed = true)
        val reservationRepository: ReservationRepository = SqliteReservationRepository()
        val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
        val googleCalendarPropertiesHolder = GoogleCalendarPropertiesHolder(
            googleCalendar = GoogleCalendarProperties(
                credentialsFilePath = "foo/path",
                calendarId = "test-calendar",
                syncFromDateTime = "2024-12-29T00:00:00Z",
                summaryToGuestFilePath = getResourceFilePath("summaryToGuestIds.json"),
                syncFrequencyMinutes = 5,
            ),
        )

        val googleCalendarService = GoogleCalendarService(
            reservationRepository = reservationRepository,
            syncCheckpointService = syncCheckpointService,
            googleCalendarPropertiesHolder = googleCalendarPropertiesHolder,
            calendarApiClient = mockCalendarApiClient,
        )

        // Set up generic mocking for Calendar.Events.List no matter if its full or incremental sync
        every { mockCalendarApiClient.events().list(any()) } returns mockEventsList
        every { mockEventsList.setTimeMin(any()) } returns mockEventsList
        every { mockEventsList.setSyncToken(any()) } returns mockEventsList

        "empty list of new events should not store events to database" {
            every { mockEventsList.execute() } returns Events().apply { items = emptyList() }

            runBlocking {
                googleCalendarService.fetchGoogleCalendarEvents()
            }

            reservationRepository.allReservations().shouldBeEmpty()
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
                googleCalendarService.fetchGoogleCalendarEvents()
            }

            reservationRepository.allReservations() shouldHaveSize 1
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
                googleCalendarService.fetchGoogleCalendarEvents()
            }

            val changedEvent = newEvent.apply {
                summary = "Updated Event"
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(changedEvent) }

            runBlocking {
                googleCalendarService.fetchGoogleCalendarEvents()
            }

            val allEvents = reservationRepository.allReservations()
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
                googleCalendarService.fetchGoogleCalendarEvents()
            }

            reservationRepository.allReservations() shouldHaveSize 1

            val deletedEvent = newEvent.apply {
                status = "cancelled"
            }

            every { mockEventsList.execute() } returns Events().apply { items = listOf(deletedEvent) }

            runBlocking {
                googleCalendarService.fetchGoogleCalendarEvents()
            }

            reservationRepository.allReservations().shouldBeEmpty()
        }
    })
