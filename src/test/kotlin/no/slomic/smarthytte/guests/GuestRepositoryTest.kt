package no.slomic.smarthytte.guests

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.BaseDbTest
import no.slomic.smarthytte.common.UpsertStatus
import no.slomic.smarthytte.reservations.ReservationGuestTable
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.event
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
    BaseDbTest({
        val repository: GuestRepository = SqliteGuestRepository()

        "add or update with new id should add new guest" {
            val upsertStatus = repository.addOrUpdate(guest)
            upsertStatus shouldBe UpsertStatus.ADDED

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
            val upsertStatus = repository.addOrUpdate(updatedGuest)
            upsertStatus shouldBe UpsertStatus.UPDATED

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
            val upsertStatus = repository.addOrUpdate(updatedGuest)
            upsertStatus shouldBe UpsertStatus.NO_ACTION

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

        "delete should remove event guests from the intermediate table (cascade)" {
            val calenderEventRepository: ReservationRepository = SqliteReservationRepository()
            repository.addOrUpdate(guest)
            val guest2 = guest.copy(id = "john2", firstName = "John2", lastName = "Doe2")
            repository.addOrUpdate(guest2)

            val eventWithGuest = event.copy(
                guestIds = listOf(guest.id, guest2.id),
            )
            calenderEventRepository.addOrUpdate(eventWithGuest)
            calenderEventRepository.reservationById(eventWithGuest.id).shouldNotBeNull()

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
