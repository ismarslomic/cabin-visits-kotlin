package no.slomic.smarthytte.guests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

class GuestRepositoryUpdateTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository: GuestRepository = SqliteGuestRepository()

        "guests by email should ignore case and whitespace" {
            repository.addOrUpdate(guest.copy(id = "a", email = "Shared@Example.no"))
            repository.addOrUpdate(guest.copy(id = "c", email = "other@example.no"))

            repository.guestsByEmail(" SHARED@example.NO ").map { it.id } shouldBe listOf("a")
            repository.guestsByEmail("unknown@example.no").shouldBeEmpty()
        }

        "database should reject a second guest with the same email, ignoring case" {
            repository.addOrUpdate(guest.copy(id = "a", email = "Shared@Example.no"))

            shouldThrow<ExposedSQLException> {
                repository.addOrUpdate(guest.copy(id = "b", email = "shared@example.no"))
            }
            shouldThrow<ExposedSQLException> {
                repository.addOrUpdate(guest.copy(id = "c", email = "other@example.no"))
                repository.update("c", GuestUpdate(email = "SHARED@example.no"))
            }
        }

        "database should accept many guests without an email" {
            repository.addOrUpdate(guest.copy(id = "a", email = null))
            repository.addOrUpdate(guest.copy(id = "b", email = null))

            repository.allGuests().map { it.id } shouldContainExactlyInAnyOrder listOf("a", "b")
        }

        "update should change only the properties that are set and bump the version" {
            repository.addOrUpdate(guest)

            val result = repository.update(guest.id, GuestUpdate(firstName = "Johnny", birthYear = 1981))

            result shouldBe PersistenceResult.UPDATED
            val stored = repository.guestById(guest.id)!!
            stored shouldBe guest.copy(firstName = "Johnny", birthYear = 1981)
        }

        "update with unchanged values, no values or an unknown guest should be no action" {
            repository.addOrUpdate(guest)

            repository.update(guest.id, GuestUpdate(firstName = guest.firstName)) shouldBe PersistenceResult.NO_ACTION
            repository.update(guest.id, GuestUpdate()) shouldBe PersistenceResult.NO_ACTION
            repository.update("unknown", GuestUpdate(firstName = "X")) shouldBe PersistenceResult.NO_ACTION
        }

        "sync from file should not overwrite properties changed through the API" {
            repository.addOrUpdate(guest)
            repository.update(guest.id, GuestUpdate(firstName = "Johnny", role = AppRole.ADMIN))

            val fromFile = guest.copy(firstName = "FromFile", lastName = "Changed", role = AppRole.GUEST_USER)
            repository.addOrUpdate(fromFile) shouldBe PersistenceResult.UPDATED

            val stored = repository.guestById(guest.id)!!
            stored.firstName shouldBe "Johnny"
            stored.role shouldBe AppRole.ADMIN
            stored.lastName shouldBe "Changed"
        }

        "sync from file should still update properties that were never changed through the API" {
            repository.addOrUpdate(guest)
            repository.update(guest.id, GuestUpdate(firstName = "Johnny"))

            repository.addOrUpdate(guest.copy(firstName = "FromFile")) shouldBe PersistenceResult.NO_ACTION
            repository.addOrUpdate(guest.copy(firstName = "FromFile", lastName = "Changed")) shouldBe
                PersistenceResult.UPDATED
            repository.guestById(guest.id)!!.lastName shouldBe "Changed"
        }

        "properties changed through the API in several updates should all be kept on sync" {
            repository.addOrUpdate(guest)
            repository.update(guest.id, GuestUpdate(firstName = "Johnny"))
            repository.update(guest.id, GuestUpdate(gender = Gender.FEMALE))

            repository.addOrUpdate(guest.copy(firstName = "FromFile", gender = Gender.MALE))

            repository.guestById(guest.id)!!.let {
                it.firstName shouldBe "Johnny"
                it.gender shouldBe Gender.FEMALE
            }
        }
    })
