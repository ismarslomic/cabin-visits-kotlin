package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckInOutSource
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.utils.TestDbSetup
import kotlin.time.Instant

class ReservationSystemServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guests = SqliteGuestRepository()
        val reservations = SqliteReservationRepository()
        val service = ReservationSystemService(reservations)

        "all and byId should return the stored reservations without a caller" {
            givenReservations(guests, reservations)

            service.all().map { it.id } shouldContainExactlyInAnyOrder listOf("r1", "r2", "r0", "r3a", "r3b")
            service.byId("r2")?.guestIds shouldBe listOf("b")
            service.byId("unknown") shouldBe null
        }

        "isWithinReservationWindow should be true from a day before the start to a day after the end" {
            givenReservations(guests, reservations)

            // r2 runs 2026-04-10 16:00 to 2026-04-12 13:00 UTC
            service.isWithinReservationWindow(Instant.parse("2026-04-09T15:59:59Z")) shouldBe false
            service.isWithinReservationWindow(Instant.parse("2026-04-09T16:00:00Z")) shouldBe true
            service.isWithinReservationWindow(Instant.parse("2026-04-13T13:00:00Z")) shouldBe true
            service.isWithinReservationWindow(Instant.parse("2026-04-13T13:00:01Z")) shouldBe false
        }

        "isWithinReservationWindow should be false without reservations, and use the clock by default" {
            service.isWithinReservationWindow() shouldBe false
        }

        "setCheckIn should store derived data for the snapshot and skip a reservation that changed" {
            givenReservations(guests, reservations)
            val r2 = checkNotNull(service.byId("r2"))
            val checkIn = CheckIn(r2.startTime, CheckInOutSource.CALENDAR_EVENT, r2.id)

            service.setCheckIn(checkIn, r2.id, r2.startTime, r2.endTime) shouldBe PersistenceResult.UPDATED
            service.setCheckIn(checkIn, r2.id, r2.startTime, r2.endTime) shouldBe PersistenceResult.NO_ACTION
            service.setCheckIn(checkIn, r2.id, Instant.parse("2026-04-01T00:00:00Z"), r2.endTime) shouldBe
                PersistenceResult.NO_ACTION
            service.byId(r2.id)?.checkIn?.time shouldBe r2.startTime
        }
    })
