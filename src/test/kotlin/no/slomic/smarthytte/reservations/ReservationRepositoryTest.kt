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
    id = "event1",
    startTime = now.truncatedToMillis(),
    endTime = now.plus(2.days).truncatedToMillis(),
    guestIds = listOf(),
    summary = "Test event",
    description = null,
    sourceCreatedTime = now.minus(1.days).truncatedToMillis(),
    sourceUpdatedTime = now.minus(5.hours).truncatedToMillis(),
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

        "add or update with existing id should update the existing event" {
            repository.addOrUpdate(reservation)

            val updatedReservation: Reservation = reservation.copy(summary = "Test event 2")
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

        "add or update with existing id without property changes should not update the existing event" {
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
                val allEventGuests: List<ResultRow> = ReservationGuestTable.selectAll().toList()
                allEventGuests.shouldBeEmpty()
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
