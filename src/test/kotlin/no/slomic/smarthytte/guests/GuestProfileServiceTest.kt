package no.slomic.smarthytte.guests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.slomic.smarthytte.auth.AppRole
import no.slomic.smarthytte.auth.AppUser
import no.slomic.smarthytte.utils.TestDbSetup

private fun user(id: String, role: AppRole) = AppUser(id, "Name $id", "$id@example.com", role)

class GuestProfileServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository = SqliteGuestRepository()
        val service = GuestProfileService(repository)

        suspend fun givenGuests() {
            repository.addOrUpdate(guest.copy(id = "ola", email = "ola@example.com", appRole = AppRole.GUEST_USER))
            repository.addOrUpdate(guest.copy(id = "kari", email = "kari@example.com", appRole = AppRole.ADMIN))
        }

        "every role should change firstName, lastName, birthYear and gender of their own guest" {
            givenGuests()

            AppRole.entries.forEach { role ->
                withClue(role) {
                    repository.addOrUpdate(guest.copy(id = "me", appRole = role))
                    val update =
                        GuestUpdate(firstName = " New ", lastName = "Name", birthYear = 1999, gender = Gender.FEMALE)

                    val result = service.update(user("me", role), "me", update)

                    result.shouldBeInstanceOf<GuestUpdateResult.Updated>().guest.let {
                        it.firstName shouldBe "New"
                        it.lastName shouldBe "Name"
                        it.birthYear shouldBe 1999
                        it.gender shouldBe Gender.FEMALE
                    }
                }
            }
        }

        "non admin should not change email, isFamily or appRole of their own guest" {
            givenGuests()
            val updates = listOf(
                GuestUpdate(email = "new@example.com"),
                GuestUpdate(isFamily = true),
                GuestUpdate(appRole = AppRole.ADMIN),
            )

            updates.forEach { update ->
                withClue(update) {
                    service.update(user("ola", AppRole.FAMILY_USER), "ola", update)
                        .shouldBeInstanceOf<GuestUpdateResult.Forbidden>()
                }
            }
            repository.guestById("ola")!!.appRole shouldBe AppRole.GUEST_USER
        }

        "non admin should not change another guest, whether it exists or not" {
            givenGuests()

            listOf("kari", "unknown").forEach {
                service.update(user("ola", AppRole.GUEST_USER), it, GuestUpdate(firstName = "X"))
                    .shouldBeInstanceOf<GuestUpdateResult.Forbidden>()
            }
            repository.guestById("kari")!!.firstName shouldBe guest.firstName
        }

        "admin should change all properties of another guest" {
            givenGuests()
            val update = GuestUpdate("A", "B", 1970, Gender.FEMALE, "a@example.com", true, AppRole.FAMILY_USER)

            val result = service.update(user("kari", AppRole.ADMIN), "ola", update)

            result.shouldBeInstanceOf<GuestUpdateResult.Updated>().guest shouldBe
                Guest("ola", "A", "B", 1970, "a@example.com", Gender.FEMALE, null, true, AppRole.FAMILY_USER)
        }

        "admin should change all properties of their own guest, except removing their own admin role" {
            givenGuests()
            val admin = user("kari", AppRole.ADMIN)

            service.update(admin, "kari", GuestUpdate(email = "new@example.com", isFamily = true))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>()
            service.update(admin, "kari", GuestUpdate(appRole = AppRole.ADMIN))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>()
            service.update(admin, "kari", GuestUpdate(appRole = AppRole.GUEST_USER))
                .shouldBeInstanceOf<GuestUpdateResult.Forbidden>()
            repository.guestById("kari")!!.appRole shouldBe AppRole.ADMIN
        }

        "admin should get not found for an unknown guest" {
            service.update(user("kari", AppRole.ADMIN), "unknown", GuestUpdate(firstName = "X")) shouldBe
                GuestUpdateResult.NotFound
        }

        "invalid values should be rejected and nothing should be changed" {
            givenGuests()
            val admin = user("kari", AppRole.ADMIN)
            val updates = mapOf(
                "blank firstName" to GuestUpdate(firstName = "  "),
                "too long lastName" to GuestUpdate(lastName = "x".repeat(21)),
                "birthYear before 1900" to GuestUpdate(birthYear = 1899),
                "birthYear in the future" to GuestUpdate(birthYear = 3000),
                "email without @" to GuestUpdate(email = "nope"),
                "email with spaces" to GuestUpdate(email = "a b@example.com"),
            )

            updates.forEach { (reason, update) ->
                withClue(reason) {
                    service.update(admin, "ola", update).shouldBeInstanceOf<GuestUpdateResult.Invalid>()
                }
            }
            repository.guestById("ola") shouldBe
                guest.copy(id = "ola", email = "ola@example.com", appRole = AppRole.GUEST_USER)
        }
    })
