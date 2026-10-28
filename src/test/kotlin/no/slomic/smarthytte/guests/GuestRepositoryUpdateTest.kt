package no.slomic.smarthytte.guests

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.utils.TestDbSetup

class GuestRepositoryUpdateTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository: GuestRepository = SqliteGuestRepository()

        "guests by email should ignore case and whitespace, and find all guests sharing the email" {
            repository.addOrUpdate(guest.copy(id = "a", email = "Shared@Example.no"))
            repository.addOrUpdate(guest.copy(id = "b", email = "shared@example.no"))
            repository.addOrUpdate(guest.copy(id = "c", email = "other@example.no"))

            repository.guestsByEmail(" SHARED@example.NO ").map { it.id }.sorted() shouldBe listOf("a", "b")
            repository.guestsByEmail("unknown@example.no").shouldBeEmpty()
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
            repository.update(guest.id, GuestUpdate(firstName = "Johnny", appRole = AppRole.ADMIN))

            val fromFile = guest.copy(firstName = "FromFile", lastName = "Changed", appRole = AppRole.GUEST_USER)
            repository.addOrUpdate(fromFile) shouldBe PersistenceResult.UPDATED

            val stored = repository.guestById(guest.id)!!
            stored.firstName shouldBe "Johnny"
            stored.appRole shouldBe AppRole.ADMIN
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
