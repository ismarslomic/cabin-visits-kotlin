package no.slomic.smarthytte.guests

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.BaseDbTest
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
            val returnedGuest = repository.addOrUpdate(guest)
            returnedGuest.shouldBeEqualToComparingFields(guest)

            transaction {
                val allGuests: List<GuestEntity> = GuestEntity.all().toList()
                allGuests shouldHaveSize 1

                val readGuest: GuestEntity = allGuests.first()
                readGuest.id.value shouldBe guest.id
                readGuest.firstName shouldBe guest.firstName
                readGuest.lastName shouldBe guest.lastName
                readGuest.birthYear shouldBe guest.birthYear
                readGuest.email shouldBe guest.email
                readGuest.gender shouldBe guest.gender
                readGuest.createdTime.shouldNotBeNull()
                readGuest.updatedTime.shouldBeNull()
                readGuest.version shouldBe 1
            }
        }

        "add or update with existing id should update the existing guest" {
            repository.addOrUpdate(guest)

            val updatedGuest = guest.copy(firstName = "John 2")
            val returnedGuest = repository.addOrUpdate(updatedGuest)
            returnedGuest.shouldBeEqualToComparingFields(updatedGuest)

            transaction {
                val allGuests: List<GuestEntity> = GuestEntity.all().toList()
                allGuests shouldHaveSize 1

                val readGuest: GuestEntity = allGuests.first()
                readGuest.id.value shouldBe updatedGuest.id
                readGuest.firstName shouldBe updatedGuest.firstName
                readGuest.lastName shouldBe updatedGuest.lastName
                readGuest.birthYear shouldBe updatedGuest.birthYear
                readGuest.email shouldBe updatedGuest.email
                readGuest.gender shouldBe updatedGuest.gender
                readGuest.createdTime.shouldNotBeNull()
                readGuest.updatedTime.shouldNotBeNull()
                readGuest.version shouldBe 2
            }
        }

        "add or update with existing id without property changes should not update the existing guest" {
            repository.addOrUpdate(guest)

            val updatedGuest = guest
            val returnedGuest = repository.addOrUpdate(updatedGuest)
            returnedGuest.shouldBeEqualToComparingFields(updatedGuest)

            transaction {
                val allGuests: List<GuestEntity> = GuestEntity.all().toList()
                allGuests shouldHaveSize 1

                val readGuest: GuestEntity = allGuests.first()
                readGuest.id.value shouldBe updatedGuest.id
                readGuest.firstName shouldBe updatedGuest.firstName
                readGuest.lastName shouldBe updatedGuest.lastName
                readGuest.birthYear shouldBe updatedGuest.birthYear
                readGuest.email shouldBe updatedGuest.email
                readGuest.gender shouldBe updatedGuest.gender
                readGuest.createdTime.shouldNotBeNull()
                readGuest.updatedTime.shouldBeNull()
                readGuest.version shouldBe 1
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
