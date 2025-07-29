package no.slomic.smarthytte.sync.checkpoint

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import no.slomic.smarthytte.utils.TestDbSetup

class SyncCheckpointServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        lateinit var repository: SyncCheckpointRepository
        lateinit var syncCheckpointService: SyncCheckpointService
        val googleCalendarEventsCheckpoint = "google-calendar-checkpoint"
        val checkInOutCheckpointString = "2025-05-04T12:15:00.123456Z"
        val checkInOutCheckpointInstant: Instant = Instant.parse(checkInOutCheckpointString)

        beforeEach {
            repository = SqliteSyncCheckpointRepository()
            syncCheckpointService = SyncCheckpointService(repository)
        }

        "should store new checkpoint for google calendar events" {
            syncCheckpointService.addOrUpdateCheckpointForGoogleCalendarEvents(googleCalendarEventsCheckpoint)
            syncCheckpointService.checkpointForGoogleCalendarEvents() shouldBe googleCalendarEventsCheckpoint
        }

        "should update existing checkpoint for google calendar events" {
            syncCheckpointService.addOrUpdateCheckpointForGoogleCalendarEvents(googleCalendarEventsCheckpoint)
            syncCheckpointService.checkpointForGoogleCalendarEvents() shouldBe googleCalendarEventsCheckpoint

            val newCheckpoint = "new-checkpoint"
            syncCheckpointService.addOrUpdateCheckpointForGoogleCalendarEvents(newCheckpoint)
            syncCheckpointService.checkpointForGoogleCalendarEvents() shouldBe newCheckpoint
        }

        "should return null if no checkpoint exists for google calendar events" {
            syncCheckpointService.checkpointForGoogleCalendarEvents().shouldBeNull()
        }

        "should delete existing checkpoint for google calendar events" {
            syncCheckpointService.addOrUpdateCheckpointForGoogleCalendarEvents(googleCalendarEventsCheckpoint)
            syncCheckpointService.checkpointForGoogleCalendarEvents() shouldBe googleCalendarEventsCheckpoint
            syncCheckpointService.deleteCheckpointForGoogleCalendarEvents()
            syncCheckpointService.checkpointForGoogleCalendarEvents().shouldBeNull()
        }

        "should not throw exception if no existing checkpoint exists for google calendar events" {
            syncCheckpointService.deleteCheckpointForGoogleCalendarEvents()
        }

        "should store new checkpoint for check in/out sensor" {
            syncCheckpointService.addOrUpdateCheckpointForCheckInOutSensor(checkInOutCheckpointInstant)

            val actualCheckpointInstant: Instant? = syncCheckpointService.checkpointForCheckInOutSensor()
            val actualCheckpointString: String? = actualCheckpointInstant?.toString()

            actualCheckpointInstant shouldBe checkInOutCheckpointInstant
            actualCheckpointString shouldBe checkInOutCheckpointString
        }

        "should update existing checkpoint for check in/out sensor" {
            syncCheckpointService.addOrUpdateCheckpointForCheckInOutSensor(checkInOutCheckpointInstant)
            syncCheckpointService.checkpointForCheckInOutSensor() shouldBe checkInOutCheckpointInstant

            val newCheckpointString = "2025-05-04T13:20:00.123456Z"
            val newCheckpointInstant: Instant = Instant.parse(newCheckpointString)

            syncCheckpointService.addOrUpdateCheckpointForCheckInOutSensor(newCheckpointInstant)
            syncCheckpointService.checkpointForCheckInOutSensor() shouldBe newCheckpointInstant
        }

        "should return null if no checkpoint exists for check in/out sensor" {
            syncCheckpointService.checkpointForCheckInOutSensor().shouldBeNull()
        }
    })
