package no.slomic.smarthytte.guests

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.reservations.ReservationGuestTable
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.reservation
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

val guest = Guest(
    id = "john",
    firstName = "John",
    lastName = "Doe",
    birthYear = 1980,
    email = "john.doe@example.no",
    gender = Gender.MALE,
)

class GuestRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val repository: GuestRepository = SqliteGuestRepository()

        "add or update with new id should add new guest" {
            val persistenceResult = repository.addOrUpdate(guest)
            persistenceResult shouldBe PersistenceResult.ADDED

            transaction {
                val allGuests: List<GuestEntity> = GuestEntity.all().toList()
                allGuests shouldHaveSize 1

                val readGuest: GuestEntity = allGuests.first()
                readGuest.shouldBeEqualToGuest(
                    other = guest,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "add or update with existing id should update the existing guest" {
            repository.addOrUpdate(guest)

            val updatedGuest = guest.copy(firstName = "John 2")
            val persistenceResult = repository.addOrUpdate(updatedGuest)
            persistenceResult shouldBe PersistenceResult.UPDATED

            transaction {
                val allGuests: List<GuestEntity> = GuestEntity.all().toList()
                allGuests shouldHaveSize 1

                val readGuest: GuestEntity = allGuests.first()
                readGuest.shouldBeEqualToGuest(
                    other = updatedGuest,
                    expectedVersion = 2,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = false,
                )
            }
        }

        "add or update with existing id without property changes should not update the existing guest" {
            repository.addOrUpdate(guest)

            val updatedGuest = guest
            val persistenceResult = repository.addOrUpdate(updatedGuest)
            persistenceResult shouldBe PersistenceResult.NO_ACTION

            transaction {
                val allGuests: List<GuestEntity> = GuestEntity.all().toList()
                allGuests shouldHaveSize 1

                val readGuest: GuestEntity = allGuests.first()
                readGuest.shouldBeEqualToGuest(
                    other = updatedGuest,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "delete should remove reservation guests from the intermediate table (cascade)" {
            val reservationRepository: ReservationRepository = SqliteReservationRepository()
            repository.addOrUpdate(guest)
            val guest2 = guest.copy(id = "john2", firstName = "John2", lastName = "Doe2")
            repository.addOrUpdate(guest2)

            val reservationWithGuest = reservation.copy(
                guestIds = listOf(guest.id, guest2.id),
            )
            reservationRepository.addOrUpdate(reservationWithGuest)
            reservationRepository.reservationById(reservationWithGuest.id).shouldNotBeNull()

            transaction {
                ReservationGuestTable.selectAll().toList() shouldHaveSize 2
                GuestEntity.findById(EntityID(guest.id, GuestTable))!!.delete()
                ReservationGuestTable.selectAll().toList() shouldHaveSize 1
                GuestEntity.findById(EntityID(guest2.id, GuestTable))!!.delete()
                ReservationGuestTable.selectAll().toList().shouldBeEmpty()
            }
        }
    })

private fun GuestEntity.shouldBeEqualToGuest(
    other: Guest,
    expectedVersion: Short,
    shouldCreatedTimeBeNull: Boolean,
    shouldUpdatedTimeBeNull: Boolean,
) {
    id.value shouldBe other.id
    firstName shouldBe other.firstName
    lastName shouldBe other.lastName
    birthYear shouldBe other.birthYear
    email shouldBe other.email
    gender shouldBe other.gender
    notionId shouldBe other.notionId

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
