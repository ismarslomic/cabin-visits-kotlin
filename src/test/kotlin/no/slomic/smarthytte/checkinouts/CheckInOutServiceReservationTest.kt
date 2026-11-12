package no.slomic.smarthytte.checkinouts

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.spyk
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationGuest
import no.slomic.smarthytte.reservations.ReservationSystemService
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorQueryService
import no.slomic.smarthytte.sensors.checkinouts.SqliteCheckInOutSensorRepository
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTripQueryService
import kotlin.time.Instant

class CheckInOutServiceReservationTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guests = SqliteGuestRepository()
        val reservations = SqliteReservationRepository()

        fun serviceFor(repository: SqliteReservationRepository = reservations) = CheckInOutService(
            ReservationSystemService(repository),
            CheckInOutSensorQueryService(SqliteCheckInOutSensorRepository()),
            VehicleTripQueryService(SqliteVehicleTripRepository()),
        )

        suspend fun givenReservation(id: String, start: String, end: String) {
            guests.addOrUpdate(testGuest("a"))
            reservations.addOrUpdate(
                Reservation(
                    id = id,
                    startTime = Instant.parse(start),
                    endTime = Instant.parse(end),
                    guests = listOf("a").map(::ReservationGuest),
                ),
            )
        }

        "the per-reservation update should set check-in and check-out of a past reservation from its times" {
            givenReservation("past", "2020-01-10T16:00:00Z", "2020-01-12T13:00:00Z")

            serviceFor().updateCheckInOutStatusForReservation("past")

            val stored = reservations.reservationById("past")!!
            stored.checkIn?.time shouldBe Instant.parse("2020-01-10T16:00:00Z")
            stored.checkIn?.sourceName shouldBe CheckInOutSource.CALENDAR_EVENT
            stored.checkOut?.time shouldBe Instant.parse("2020-01-12T13:00:00Z")
        }

        "the per-reservation update should do nothing for a reservation that has not started or does not exist" {
            givenReservation("future", "2999-01-10T16:00:00Z", "2999-01-12T13:00:00Z")
            val service = serviceFor()

            service.updateCheckInOutStatusForReservation("future")
            service.updateCheckInOutStatusForReservation("nope")

            val stored = reservations.reservationById("future")!!
            stored.checkIn shouldBe null
            stored.checkOut shouldBe null
        }

        "the all-reservations update should go on with the other reservations when one fails" {
            givenReservation("broken", "2020-01-10T16:00:00Z", "2020-01-12T13:00:00Z")
            reservations.addOrUpdate(
                Reservation(
                    id = "fine",
                    startTime = Instant.parse("2020-02-10T16:00:00Z"),
                    endTime = Instant.parse("2020-02-12T13:00:00Z"),
                    guests = listOf("a").map(::ReservationGuest),
                ),
            )
            val failing = spyk(reservations)
            coEvery { failing.setCheckIn(any(), "broken", any(), any()) } throws IllegalStateException("boom")

            serviceFor(failing).updateCheckInOutStatusForAllReservations()

            coVerify { failing.setCheckIn(any(), "broken", any(), any()) }
            reservations.reservationById("broken")?.checkIn shouldBe null
            reservations.reservationById("fine")?.checkIn?.time shouldBe Instant.parse("2020-02-10T16:00:00Z")
        }
    })
