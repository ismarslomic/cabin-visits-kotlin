package no.slomic.smarthytte.calendar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.createReservation
import no.slomic.smarthytte.reservations.updateReservation
import no.slomic.smarthytte.utils.TestDbSetup
import kotlin.time.Instant

class PendingCalendarOperationsTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        "pendingCalendarOperations should count the queued operations" {
            pendingCalendarOperations() shouldBe 0

            SqliteGuestRepository().addOrUpdate(testGuest("a"))
            val reservations = SqliteReservationRepository()
            reservations.createReservation(
                Instant.parse("2026-07-10T16:00:00Z"),
                Instant.parse("2026-07-12T13:00:00Z"),
                listOf("a"),
                "a",
            )
            reservations.createReservation(
                Instant.parse("2026-08-10T16:00:00Z"),
                Instant.parse("2026-08-12T13:00:00Z"),
                listOf("a"),
                "a",
            )

            pendingCalendarOperations() shouldBe 2
        }
    })
