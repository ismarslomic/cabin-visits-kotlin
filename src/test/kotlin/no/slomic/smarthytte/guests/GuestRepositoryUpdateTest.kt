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

        "update should only change the properties that are set, and add or update should overwrite all of them" {
            repository.addOrUpdate(guest)
            repository.update(guest.id, GuestUpdate(firstName = "Johnny", role = AppRole.ADMIN))

            val overwritten = guest.copy(firstName = "FromFixture", lastName = "Changed", role = AppRole.GUEST_USER)
            repository.addOrUpdate(overwritten) shouldBe PersistenceResult.UPDATED

            repository.guestById(guest.id) shouldBe overwritten
        }

        "update with several properties should change them all in one version bump" {
            repository.addOrUpdate(guest)

            repository.update(guest.id, GuestUpdate(gender = Gender.FEMALE, isFamily = true, email = "new@example.no"))
                .shouldBe(PersistenceResult.UPDATED)

            repository.guestById(guest.id)!!.let {
                it.gender shouldBe Gender.FEMALE
                it.isFamily shouldBe true
                it.email shouldBe "new@example.no"
            }
        }
    })
