package no.slomic.smarthytte.reservations

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import no.slomic.smarthytte.BaseDbTest
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckInOutSource
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.guests.guest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

val now = Clock.System.now()

val reservation = Reservation(
    id = "reservation1",
    startTime = now.truncatedToMillis(),
    endTime = now.plus(2.days).truncatedToMillis(),
    guestIds = listOf(),
    summary = "Test reservation",
    description = null,
    sourceCreatedTime = now.minus(1.days).truncatedToMillis(),
    sourceUpdatedTime = now.minus(5.hours).truncatedToMillis(),
)

val checkIn = CheckIn(
    time = now,
    sourceName = CheckInOutSource.CHECK_IN_SENSOR,
    sourceId = "foo-source-id",
)

val checkOut = CheckOut(
    time = now,
    sourceName = CheckInOutSource.CHECK_IN_SENSOR,
    sourceId = "foo-source-id",
)

class ReservationRepositoryTest :
    BaseDbTest({
        val repository: ReservationRepository = SqliteReservationRepository()

        "add or update with new id should add new reservation" {
            val persistenceResult = repository.addOrUpdate(reservation)
            persistenceResult shouldBe PersistenceResult.ADDED

            transaction {
                val allReservations: List<ReservationEntity> = ReservationEntity.all().toList()
                allReservations shouldHaveSize 1

                val readReservation: ReservationEntity = allReservations.first()
                readReservation.shouldBeEqualToReservation(
                    other = reservation,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "add or update with existing id should update the existing reservation" {
            repository.addOrUpdate(reservation)

            val updatedReservation: Reservation = reservation.copy(summary = "Test reservation 2")
            val persistenceResult: PersistenceResult = repository.addOrUpdate(updatedReservation)
            persistenceResult shouldBe PersistenceResult.UPDATED

            transaction {
                val allReservations: List<ReservationEntity> = ReservationEntity.all().toList()
                allReservations shouldHaveSize 1

                val readReservation: ReservationEntity = allReservations.first()
                readReservation.shouldBeEqualToReservation(
                    other = updatedReservation,
                    expectedVersion = 2,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = false,
                )
            }
        }

        "add or update with existing id without property changes should not update the existing reservation" {
            repository.addOrUpdate(reservation)

            val updatedReservation: Reservation = reservation
            val persistenceResult: PersistenceResult = repository.addOrUpdate(updatedReservation)
            persistenceResult shouldBe PersistenceResult.NO_ACTION

            transaction {
                val allReservations: List<ReservationEntity> = ReservationEntity.all().toList()
                allReservations shouldHaveSize 1

                val readReservation: ReservationEntity = allReservations.first()
                readReservation.shouldBeEqualToReservation(
                    other = updatedReservation,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "delete should remove existing reservation and return DELETED as result" {
            repository.addOrUpdate(reservation)
            repository.allReservations() shouldHaveSize 1

            val persistenceResult = repository.deleteReservation(reservation.id)
            persistenceResult shouldBe PersistenceResult.DELETED
            repository.allReservations() shouldHaveSize 0
        }

        "deleting non-existing reservation should return NO_ACTION as result" {
            val persistenceResult = repository.deleteReservation("non-existing-id")
            persistenceResult shouldBe PersistenceResult.NO_ACTION
            repository.allReservations() shouldHaveSize 0
        }

        "reading reservation by existing id should read reservation" {
            repository.addOrUpdate(reservation)
            repository.reservationById(reservation.id)!!.shouldBeEqualToComparingFields(reservation)
        }

        "reading reservation by non existing id should return null" {
            repository.reservationById(reservation.id).shouldBeNull()
        }

        "setting check in status for non existing reservation should return NO_ACTION as result" {
            val persistenceResult = repository.setCheckIn(checkIn = checkIn, id = "non-existing-id")
            persistenceResult shouldBe PersistenceResult.NO_ACTION
        }

        "setting new check in status should return UPDATED as result and update reservations check in time" {
            repository.addOrUpdate(reservation)
            val persistenceResult = repository.setCheckIn(checkIn = checkIn, id = reservation.id)

            persistenceResult shouldBe PersistenceResult.UPDATED
            transaction {
                val updatedReservation = ReservationEntity.all().first()
                updatedReservation.checkInTime shouldBe checkIn.time.truncatedToMillis()
                updatedReservation.checkInSourceId shouldBe checkIn.sourceId
                updatedReservation.checkInSourceName shouldBe checkIn.sourceName
                updatedReservation.version shouldBe 2
                updatedReservation.updatedTime.shouldNotBeNull()
            }
        }

        "update check in status without change should return NO_ACTION as result without any updates" {
            repository.addOrUpdate(reservation)
            repository.setCheckIn(checkIn = checkIn, id = reservation.id)
            val persistenceResult = repository.setCheckIn(checkIn = checkIn, id = reservation.id)

            persistenceResult shouldBe PersistenceResult.NO_ACTION
            transaction {
                val updatedReservation = ReservationEntity.all().first()
                updatedReservation.version shouldBe 2
            }
        }

        "update check in status with change should return UPDATED and update reservations check in time" {
            repository.addOrUpdate(reservation)
            repository.setCheckIn(checkIn = checkIn, id = reservation.id)
            val changedCheckIn = checkIn.copy(time = now.plus(1.hours))
            val persistenceResult = repository.setCheckIn(checkIn = changedCheckIn, id = reservation.id)

            persistenceResult shouldBe PersistenceResult.UPDATED
            transaction {
                val updatedReservation = ReservationEntity.all().first()
                updatedReservation.checkInTime shouldBe changedCheckIn.time.truncatedToMillis()
                updatedReservation.checkInSourceId shouldBe changedCheckIn.sourceId
                updatedReservation.checkInSourceName shouldBe changedCheckIn.sourceName
                updatedReservation.version shouldBe 3
            }
        }

        "setting check out status for non existing reservation should return NO_ACTION as result" {
            val persistenceResult = repository.setCheckOut(checkOut = checkOut, id = "non-existing-id")
            persistenceResult shouldBe PersistenceResult.NO_ACTION
        }

        "setting new check out status should return UPDATED as result and update reservations check out time" {
            repository.addOrUpdate(reservation)
            val persistenceResult = repository.setCheckOut(checkOut = checkOut, id = reservation.id)

            persistenceResult shouldBe PersistenceResult.UPDATED
            transaction {
                val updatedReservation = ReservationEntity.all().first()
                updatedReservation.checkOutTime shouldBe checkOut.time.truncatedToMillis()
                updatedReservation.checkOutSourceId shouldBe checkOut.sourceId
                updatedReservation.checkOutSourceName shouldBe checkOut.sourceName
                updatedReservation.version shouldBe 2
                updatedReservation.updatedTime.shouldNotBeNull()
            }
        }

        "update check out status without change should return NO_ACTION as result without any updates" {
            repository.addOrUpdate(reservation)
            repository.setCheckOut(checkOut = checkOut, id = reservation.id)
            val persistenceResult = repository.setCheckOut(checkOut = checkOut, id = reservation.id)

            persistenceResult shouldBe PersistenceResult.NO_ACTION
            transaction {
                val updatedReservation = ReservationEntity.all().first()
                updatedReservation.version shouldBe 2
            }
        }

        "update check out status with change should return UPDATED and update reservations check out time" {
            repository.addOrUpdate(reservation)
            repository.setCheckOut(checkOut = checkOut, id = reservation.id)
            val changedCheckOut = checkOut.copy(time = now.plus(1.hours))
            val persistenceResult = repository.setCheckOut(checkOut = changedCheckOut, id = reservation.id)

            persistenceResult shouldBe PersistenceResult.UPDATED
            transaction {
                val updatedReservation = ReservationEntity.all().first()
                updatedReservation.checkOutTime shouldBe changedCheckOut.time.truncatedToMillis()
                updatedReservation.checkOutSourceId shouldBe changedCheckOut.sourceId
                updatedReservation.checkOutSourceName shouldBe changedCheckOut.sourceName
                updatedReservation.version shouldBe 3
            }
        }

        "adding guests to the reservation should store guests to intermediate table" {
            val guestRepository: GuestRepository = SqliteGuestRepository()
            guestRepository.addOrUpdate(guest)
            val guest2 = guest.copy(id = "john2", firstName = "John2", lastName = "Doe2")
            guestRepository.addOrUpdate(guest2)

            val reservationWithGuest = reservation.copy(
                guestIds = listOf(guest.id, guest2.id),
            )
            repository.addOrUpdate(reservationWithGuest)

            // We are currently not loading the guests when reading from database
            repository.reservationById(reservation.id)!!
                .shouldBeEqualToIgnoringFields(reservationWithGuest, Reservation::guestIds)

            transaction {
                val allReservationGuests: List<ResultRow> = ReservationGuestTable.selectAll().toList()
                allReservationGuests shouldHaveSize 2

                val firsGuest: ResultRow = allReservationGuests.first()
                firsGuest[ReservationGuestTable.reservation] = reservation.id
                firsGuest[ReservationGuestTable.guest] = guest.id

                val lastGuest: ResultRow = allReservationGuests.last()
                lastGuest[ReservationGuestTable.reservation] = reservation.id
                lastGuest[ReservationGuestTable.guest] = guest2.id
            }
        }

        "delete should remove reservation guests from the intermediate table (cascade)" {
            val guestRepository: GuestRepository = SqliteGuestRepository()
            guestRepository.addOrUpdate(guest)
            val guest2 = guest.copy(id = "john2", firstName = "John2", lastName = "Doe2")
            guestRepository.addOrUpdate(guest2)

            val reservationWithGuest = reservation.copy(
                guestIds = listOf(guest.id, guest2.id),
            )
            repository.addOrUpdate(reservationWithGuest)
            repository.reservationById(reservationWithGuest.id).shouldNotBeNull()

            transaction {
                val allReservationGuests: List<ResultRow> = ReservationGuestTable.selectAll().toList()
                allReservationGuests shouldHaveSize 2
            }

            repository.deleteReservation(reservationWithGuest.id)
            repository.reservationById(reservationWithGuest.id).shouldBeNull()

            transaction {
                val allReservationGuests: List<ResultRow> = ReservationGuestTable.selectAll().toList()
                allReservationGuests.shouldBeEmpty()
            }
        }
    })

private fun ReservationEntity.shouldBeEqualToReservation(
    other: Reservation,
    expectedVersion: Short,
    shouldCreatedTimeBeNull: Boolean,
    shouldUpdatedTimeBeNull: Boolean,
) {
    id.value shouldBe other.id
    startTime shouldBe other.startTime
    endTime shouldBe other.endTime
    guests.shouldBeEmpty()
    summary shouldBe other.summary
    description shouldBe other.description
    sourceCreatedTime shouldBe other.sourceCreatedTime
    sourceUpdatedTime shouldBe other.sourceUpdatedTime

    if (shouldCreatedTimeBeNull) {
        createdTime.shouldBeNull()
    } else {
        createdTime.shouldNotBeNull()
    }

    if (shouldUpdatedTimeBeNull) {
        updatedTime.shouldBeNull()
    } else {
        updatedTime.shouldNotBeNull()
    }

    version shouldBe expectedVersion
}
