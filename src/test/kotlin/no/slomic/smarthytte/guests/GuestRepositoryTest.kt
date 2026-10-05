package no.slomic.smarthytte.guests

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.reservations.ReservationGuestTable
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.reservation
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant

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

        "add or update should store isFamily" {
            val familyGuest = guest.copy(isFamily = true)
            repository.addOrUpdate(familyGuest)

            repository.guestById(guest.id) shouldBe familyGuest
        }

        "add or update with new id and avatar image should add guest and avatar image" {
            val image = byteArrayOf(1, 2, 3)
            val before = Clock.System.now().truncatedToMillis()

            repository.addOrUpdate(guest, image) shouldBe PersistenceResult.ADDED

            repository.avatarById(guest.id)?.image shouldBe image
            avatarUpdatedTime(guest.id) shouldBeGreaterThanOrEqualTo before
        }

        "add or update with new id without avatar image should not add avatar image" {
            repository.addOrUpdate(guest, guestAvatarImage = null) shouldBe PersistenceResult.ADDED

            repository.avatarById(guest.id).shouldBeNull()
            transaction { GuestAvatarTable.selectAll().toList().shouldBeEmpty() }
        }

        "add or update with existing id and new avatar image should add avatar image" {
            repository.addOrUpdate(guest)

            repository.addOrUpdate(guest, byteArrayOf(1, 2, 3)) shouldBe PersistenceResult.UPDATED

            repository.avatarById(guest.id)?.image shouldBe byteArrayOf(1, 2, 3)
        }

        "add or update with existing id and changed avatar image should update avatar image" {
            repository.addOrUpdate(guest, byteArrayOf(1, 2, 3))
            setOldAvatarUpdatedTime(guest.id)

            repository.addOrUpdate(guest, byteArrayOf(4, 5)) shouldBe PersistenceResult.UPDATED

            repository.avatarById(guest.id)?.image shouldBe byteArrayOf(4, 5)
            avatarUpdatedTime(guest.id) shouldBeGreaterThan oldTime
            transaction { GuestAvatarTable.selectAll().toList() shouldHaveSize 1 }
        }

        "add or update with existing id and same avatar image should not update avatar image" {
            val image = byteArrayOf(1, 2, 3)
            repository.addOrUpdate(guest, image)
            setOldAvatarUpdatedTime(guest.id)

            repository.addOrUpdate(guest, image) shouldBe PersistenceResult.NO_ACTION

            repository.avatarById(guest.id)?.image shouldBe image
            avatarUpdatedTime(guest.id) shouldBe oldTime
        }

        "add or update with existing id and no avatar image should delete stored avatar image" {
            repository.addOrUpdate(guest, byteArrayOf(1, 2, 3))

            repository.addOrUpdate(guest, guestAvatarImage = null) shouldBe PersistenceResult.UPDATED

            repository.avatarById(guest.id).shouldBeNull()
            transaction { GuestAvatarTable.selectAll().toList().shouldBeEmpty() }
        }

        "add or update with existing id, no avatar image and no stored avatar image should not update" {
            repository.addOrUpdate(guest)

            repository.addOrUpdate(guest, guestAvatarImage = null) shouldBe PersistenceResult.NO_ACTION
        }

        "add or update with changed avatar image should not change the guest row" {
            repository.addOrUpdate(guest, byteArrayOf(1, 2, 3))

            repository.addOrUpdate(guest, byteArrayOf(4, 5))

            transaction {
                val readGuest: GuestEntity = GuestEntity.findById(guest.id)!!
                readGuest.version shouldBe 1
                readGuest.updatedTime.shouldBeNull()
            }
        }

        "add or update with changed guest and same avatar image should update guest but keep avatar image" {
            val image = byteArrayOf(1, 2, 3)
            repository.addOrUpdate(guest, image)
            setOldAvatarUpdatedTime(guest.id)

            val updatedGuest = guest.copy(firstName = "John 2")
            repository.addOrUpdate(updatedGuest, image) shouldBe PersistenceResult.UPDATED

            repository.guestById(guest.id) shouldBe updatedGuest
            repository.avatarById(guest.id)?.image shouldBe image
            avatarUpdatedTime(guest.id) shouldBe oldTime
        }

        "avatar by id should return image and updated time" {
            val image = byteArrayOf(1, 2, 3)
            repository.addOrUpdate(guest, image)
            setOldAvatarUpdatedTime(guest.id)

            val avatar = repository.avatarById(guest.id).shouldNotBeNull()

            avatar.image shouldBe image
            avatar.updatedTime shouldBe oldTime
        }

        "all avatar updated times should only include guests with avatar image" {
            val guest2 = guest.copy(id = "john2", firstName = "John2", lastName = "Doe2")
            repository.addOrUpdate(guest, byteArrayOf(1, 2, 3))
            repository.addOrUpdate(guest2)
            setOldAvatarUpdatedTime(guest.id)

            repository.allAvatarUpdatedTimes() shouldBe mapOf(guest.id to oldTime)
        }

        "delete guest should delete avatar image (cascade)" {
            repository.addOrUpdate(guest, byteArrayOf(1, 2, 3))

            transaction {
                GuestAvatarTable.selectAll().toList() shouldHaveSize 1
                GuestEntity.findById(EntityID(guest.id, GuestTable))!!.delete()
                GuestAvatarTable.selectAll().toList().shouldBeEmpty()
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

private val oldTime: Instant = Instant.parse("2020-01-01T12:00:00Z")

private fun avatarUpdatedTime(guestId: String): Instant = transaction {
    GuestAvatarTable.selectAll().where { GuestAvatarTable.guest eq guestId }.single()[GuestAvatarTable.updatedTime]
}

private fun setOldAvatarUpdatedTime(guestId: String) {
    transaction {
        GuestAvatarTable.update({ GuestAvatarTable.guest eq guestId }) { it[updatedTime] = oldTime }
    }
}

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
    isFamily shouldBe other.isFamily

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
