package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.checkinouts.CABIN_CITY_NAME
import no.slomic.smarthytte.checkinouts.HOME_CITY_NAME
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.createTrip
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.hours

/** `created_by`/`updated_by`, and which writes bump `version`, for reservations (decisions 17 and 34). */
class ReservationRepositoryAuditTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository: ReservationRepository = SqliteReservationRepository()
        val guestRepository: GuestRepository = SqliteGuestRepository()

        suspend fun withGuests() {
            guestRepository.addOrUpdate(guestLena)
            guestRepository.addOrUpdate(guestCarlos)
        }

        "create should record the actor in created_by and leave the update columns empty" {
            repository.addOrUpdate(reservation, "ola")

            val stored = repository.reservationById(reservation.id).shouldNotBeNull()
            stored.createdBy shouldBe "ola"
            stored.createdTime.shouldNotBeNull()
            stored.updatedBy.shouldBeNull()
            stored.updatedTime.shouldBeNull()
            stored.version shouldBe 1
        }

        "create without an actor should record the system actor" {
            repository.addOrUpdate(reservation)

            repository.reservationById(reservation.id)!!.createdBy shouldBe SYSTEM_ACTOR
        }

        "update of the times should record the actor in updated_by, bump the version and keep created_by" {
            repository.addOrUpdate(reservation, "ola")
            repository.addOrUpdate(reservation.copy(endTime = reservation.endTime.plus(1.hours)), "kari") shouldBe
                PersistenceResult.UPDATED

            val stored = repository.reservationById(reservation.id)!!
            stored.createdBy shouldBe "ola"
            stored.updatedBy shouldBe "kari"
            stored.updatedTime.shouldNotBeNull()
            stored.version shouldBe 2
        }

        "update of the guests only should count as a change" {
            withGuests()
            repository.addOrUpdate(reservation.copy(guestIds = listOf(guestLena.id)), "ola")
            repository.addOrUpdate(
                reservation.copy(guestIds = listOf(guestLena.id, guestCarlos.id)),
                "kari",
            ) shouldBe PersistenceResult.UPDATED

            val stored = repository.reservationById(reservation.id)!!
            stored.guestIds shouldBe listOf(guestCarlos.id, guestLena.id).sorted()
            stored.version shouldBe 2
            stored.updatedBy shouldBe "kari"
        }

        "saving the same guests in another order should not change anything" {
            withGuests()
            repository.addOrUpdate(reservation.copy(guestIds = listOf(guestLena.id, guestCarlos.id)), "ola")
            repository.addOrUpdate(
                reservation.copy(guestIds = listOf(guestCarlos.id, guestLena.id)),
                "kari",
            ) shouldBe PersistenceResult.NO_ACTION

            val stored = repository.reservationById(reservation.id)!!
            stored.version shouldBe 1
            stored.updatedBy.shouldBeNull()
            stored.updatedTime.shouldBeNull()
        }

        "check in and check out should not bump the version or set the update columns" {
            repository.addOrUpdate(reservation, "ola")
            repository.setCheckIn(checkIn, reservation.id, reservation.startTime, reservation.endTime)
            repository.setCheckOut(checkOut, reservation.id, reservation.startTime, reservation.endTime)

            val stored = repository.reservationById(reservation.id)!!
            stored.checkIn.shouldNotBeNull()
            stored.checkOut.shouldNotBeNull()
            stored.version shouldBe 1
            stored.updatedBy.shouldBeNull()
            stored.updatedTime.shouldBeNull()
        }

        "check in and check out should be skipped when the times of the reservation changed since the snapshot" {
            repository.addOrUpdate(reservation, "ola")
            repository.addOrUpdate(reservation.copy(endTime = reservation.endTime.plus(1.hours)), "ola")

            // The snapshot still has the old end time
            repository.setCheckIn(checkIn, reservation.id, reservation.startTime, reservation.endTime) shouldBe
                PersistenceResult.NO_ACTION
            repository.setCheckOut(checkOut, reservation.id, reservation.startTime, reservation.endTime) shouldBe
                PersistenceResult.NO_ACTION

            val stored = repository.reservationById(reservation.id)!!
            stored.checkIn.shouldBeNull()
            stored.checkOut.shouldBeNull()
        }

        "trip link should be skipped when the reservation is gone or its times changed" {
            val trip = createTrip(HOME_CITY_NAME, CABIN_CITY_NAME, "2025-01-01T10:00:00Z", "2025-01-01T12:00:00Z")
            SqliteVehicleTripRepository().addOrUpdate(trip, SYSTEM_ACTOR)
            val type = ReservationVehicleTripType.TO_CABIN

            // Gone: without the check this would be a foreign key violation
            repository.addVehicleTripLink("gone", trip.id, type, reservation.startTime, reservation.endTime) shouldBe
                PersistenceResult.NO_ACTION

            repository.addOrUpdate(reservation, "ola")
            repository.addVehicleTripLink(
                reservation.id,
                trip.id,
                type,
                reservation.startTime.plus(1.hours),
                reservation.endTime,
            ) shouldBe PersistenceResult.NO_ACTION

            transaction { ReservationVehicleTripTable.selectAll().count() } shouldBe 0
        }
    })
