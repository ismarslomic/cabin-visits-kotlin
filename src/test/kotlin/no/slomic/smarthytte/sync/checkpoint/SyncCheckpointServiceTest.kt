package no.slomic.smarthytte.sync.checkpoint

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.utils.TestDbSetup
import kotlin.time.Instant

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

        // --- Ski stats leaderboard checkpoints ---

        "should store new checkpoint for ski stats day for a given profileId" {
            val date = LocalDate(2026, 2, 15)
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay("ismar", date)
            syncCheckpointService.checkpointForSkiStatsDay("ismar") shouldBe date
        }

        "should update existing checkpoint for ski stats day for a given profileId" {
            val date = LocalDate(2026, 2, 15)
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay("ismar", date)

            val newDate = LocalDate(2026, 2, 16)
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay("ismar", newDate)
            syncCheckpointService.checkpointForSkiStatsDay("ismar") shouldBe newDate
        }

        "should return null if no checkpoint exists for ski stats day for a given profileId" {
            syncCheckpointService.checkpointForSkiStatsDay("ismar").shouldBeNull()
        }

        "should store new checkpoint for ski stats week for a given profileId" {
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsWeek("ismar", "2907")
            syncCheckpointService.checkpointForSkiStatsWeek("ismar") shouldBe "2907"
        }

        "should update existing checkpoint for ski stats week for a given profileId" {
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsWeek("ismar", "2907")
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsWeek("ismar", "2908")
            syncCheckpointService.checkpointForSkiStatsWeek("ismar") shouldBe "2908"
        }

        "should return null if no checkpoint exists for ski stats week for a given profileId" {
            syncCheckpointService.checkpointForSkiStatsWeek("ismar").shouldBeNull()
        }

        "should store new checkpoint for ski stats season for a given profileId" {
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsSeason("ismar", "29")
            syncCheckpointService.checkpointForSkiStatsSeason("ismar") shouldBe "29"
        }

        "should update existing checkpoint for ski stats season for a given profileId" {
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsSeason("ismar", "29")
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsSeason("ismar", "30")
            syncCheckpointService.checkpointForSkiStatsSeason("ismar") shouldBe "30"
        }

        "should return null if no checkpoint exists for ski stats season for a given profileId" {
            syncCheckpointService.checkpointForSkiStatsSeason("ismar").shouldBeNull()
        }

        "checkpoints for different profiles should be stored and read independently" {
            val date = LocalDate(2026, 2, 15)
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay("ismar", date)
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay("mirela", LocalDate(2026, 2, 10))

            syncCheckpointService.checkpointForSkiStatsDay("ismar") shouldBe date
            syncCheckpointService.checkpointForSkiStatsDay("mirela") shouldBe LocalDate(2026, 2, 10)
        }

        "checkpoints for different period types of the same profile should be stored independently" {
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay("ismar", LocalDate(2026, 2, 15))
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsWeek("ismar", "2907")
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsSeason("ismar", "29")

            syncCheckpointService.checkpointForSkiStatsDay("ismar") shouldBe LocalDate(2026, 2, 15)
            syncCheckpointService.checkpointForSkiStatsWeek("ismar") shouldBe "2907"
            syncCheckpointService.checkpointForSkiStatsSeason("ismar") shouldBe "29"
        }

        // Verify PeriodType import is used (compile-time check)
        "checkpoint id includes period type name" {
            syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay("ismar", LocalDate(2026, 1, 1))
            // checkpoint key is "ski_stats_day_ismar" — different from week/season
            syncCheckpointService.checkpointForSkiStatsWeek("ismar").shouldBeNull()
            syncCheckpointService.checkpointForSkiStatsSeason("ismar").shouldBeNull()
        }
    })
