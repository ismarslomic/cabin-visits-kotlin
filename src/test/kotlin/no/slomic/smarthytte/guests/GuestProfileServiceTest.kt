package no.slomic.smarthytte.guests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.spyk
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.utils.TestDbSetup

private fun user(id: String, role: AppRole) = UserPrincipal(
    id = id,
    name = "Name $id",
    firstName = "Name",
    lastName = id,
    birthYear = 1980,
    gender = Gender.MALE,
    email = "$id@example.com",
    isFamily = false,
    role = role,
)

class GuestProfileServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository = SqliteGuestRepository()
        val service = GuestProfileService(repository)

        /** Updates with the version that is stored now, for tests that are not about the version check. */
        suspend fun updateGuest(actor: UserPrincipal, guestId: String, change: GuestChange): GuestUpdateResult {
            val stored = repository.guestById(guestId)

            return service.update(actor, guestId, change.toRequest(stored, stored?.version ?: 1))
        }

        suspend fun givenGuests() {
            repository.addOrUpdate(guest.copy(id = "ola", email = "ola@example.com", role = AppRole.GUEST_USER))
            repository.addOrUpdate(guest.copy(id = "kari", email = "kari@example.com", role = AppRole.ADMIN))
        }

        "non admin should not change any guest, not even their own, whether the guest exists or not" {
            givenGuests()

            listOf(AppRole.FAMILY_USER, AppRole.GUEST_USER).forEach { role ->
                listOf("ola", "kari", "unknown").forEach {
                    withClue("$role $it") {
                        updateGuest(user("ola", role), it, GuestChange(firstName = "X"))
                            .shouldBeInstanceOf<GuestUpdateResult.Forbidden>()
                    }
                }
            }
            repository.guestById("ola")!!.firstName shouldBe guest.firstName
            repository.guestById("kari")!!.firstName shouldBe guest.firstName
        }

        "admin should change all properties of another guest" {
            givenGuests()
            val update = GuestChange("A", "B", 1970, Gender.FEMALE, "a@example.com", true, AppRole.FAMILY_USER)

            val result = updateGuest(user("kari", AppRole.ADMIN), "ola", update)

            result.shouldBeInstanceOf<GuestUpdateResult.Updated>().guest shouldBe
                Guest("ola", "A", "B", 1970, "a@example.com", Gender.FEMALE, null, true, AppRole.FAMILY_USER)
        }

        "admin should change their own properties, except removing their own admin role or changing their own email" {
            givenGuests()
            val admin = user("kari", AppRole.ADMIN)

            updateGuest(admin, "kari", GuestChange(email = "kari@example.com", isFamily = true))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>()
            updateGuest(admin, "kari", GuestChange(email = "new@example.com"))
                .shouldBeInstanceOf<GuestUpdateResult.Forbidden>()
            repository.guestById("kari")!!.email shouldBe "kari@example.com"
            updateGuest(admin, "kari", GuestChange(role = AppRole.ADMIN))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>()
            updateGuest(admin, "kari", GuestChange(role = AppRole.GUEST_USER))
                .shouldBeInstanceOf<GuestUpdateResult.Forbidden>()
            repository.guestById("kari")!!.role shouldBe AppRole.ADMIN
        }

        "email used by another guest, with or without a role, should be a conflict" {
            givenGuests()
            repository.addOrUpdate(guest.copy(id = "external", email = "external@example.com", role = null))
            val admin = user("kari", AppRole.ADMIN)

            updateGuest(admin, "ola", GuestChange(email = "KARI@example.com"))
                .shouldBeInstanceOf<GuestUpdateResult.Conflict>()
            updateGuest(admin, "ola", GuestChange(email = "external@example.com"))
                .shouldBeInstanceOf<GuestUpdateResult.Conflict>()
            updateGuest(admin, "external", GuestChange(email = "ola@example.com"))
                .shouldBeInstanceOf<GuestUpdateResult.Conflict>()
            repository.guestById("ola")!!.email shouldBe "ola@example.com"
            repository.guestById("external")!!.email shouldBe "external@example.com"
        }

        "the same email in another case should not be a conflict for the guest itself" {
            givenGuests()

            updateGuest(user("kari", AppRole.ADMIN), "ola", GuestChange(email = "OLA@example.com"))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>()
        }

        "every role should change all their own properties with update own profile" {
            AppRole.entries.forEach { role ->
                withClue(role) {
                    repository.addOrUpdate(guest.copy(id = "me-$role", email = "me-$role@example.com", role = role))
                    val update = MeUpdate(1, " New ", "Name", 1999, Gender.FEMALE)

                    val result = service.updateOwnProfile(user("me-$role", role), update)

                    result.shouldBeInstanceOf<GuestUpdateResult.Updated>().guest shouldBe
                        guest.copy(
                            id = "me-$role",
                            firstName = "New",
                            lastName = "Name",
                            birthYear = 1999,
                            gender = Gender.FEMALE,
                            email = "me-$role@example.com",
                            role = role,
                        )
                }
            }
        }

        "update own profile should never change email, isFamily or role" {
            repository.addOrUpdate(guest.copy(id = "me", isFamily = true, role = AppRole.FAMILY_USER))

            service.updateOwnProfile(user("me", AppRole.FAMILY_USER), MeUpdate(1, "A", "B", 1970, Gender.MALE))

            repository.guestById("me")!!.let {
                it.email shouldBe guest.email
                it.isFamily shouldBe true
                it.role shouldBe AppRole.FAMILY_USER
            }
        }

        "update own profile should be invalid for values that are not valid, and nothing should be changed" {
            givenGuests()
            val me = user("ola", AppRole.GUEST_USER)
            val valid = MeUpdate(1, "A", "B", 1970, Gender.MALE)
            val updates = mapOf(
                "blank firstName" to valid.copy(firstName = "  "),
                "too long lastName" to valid.copy(lastName = "x".repeat(21)),
                "birthYear before 1900" to valid.copy(birthYear = 1899),
                "birthYear in the future" to valid.copy(birthYear = 3000),
            )

            updates.forEach { (reason, update) ->
                withClue(reason) {
                    service.updateOwnProfile(me, update).shouldBeInstanceOf<GuestUpdateResult.Invalid>()
                }
            }
            repository.guestById("ola") shouldBe
                guest.copy(id = "ola", email = "ola@example.com", role = AppRole.GUEST_USER)
        }

        "update own profile should get not found when the guest does not exist" {
            service.updateOwnProfile(user("unknown", AppRole.GUEST_USER), MeUpdate(1, "A", "B", 1970, Gender.MALE))
                .shouldBeInstanceOf<GuestUpdateResult.NotFound>()
        }

        "admin should get not found for an unknown guest" {
            updateGuest(user("kari", AppRole.ADMIN), "unknown", GuestChange(firstName = "X")) shouldBe
                GuestUpdateResult.NotFound
        }

        "invalid values should be rejected and nothing should be changed" {
            givenGuests()
            val admin = user("kari", AppRole.ADMIN)
            val updates = mapOf(
                "blank firstName" to GuestChange(firstName = "  "),
                "too long lastName" to GuestChange(lastName = "x".repeat(21)),
                "birthYear before 1900" to GuestChange(birthYear = 1899),
                "birthYear in the future" to GuestChange(birthYear = 3000),
                "email without @" to GuestChange(email = "nope"),
                "email with spaces" to GuestChange(email = "a b@example.com"),
                "too long email" to GuestChange(email = "x".repeat(250) + "@example.com"),
            )

            updates.forEach { (reason, update) ->
                withClue(reason) {
                    updateGuest(admin, "ola", update).shouldBeInstanceOf<GuestUpdateResult.Invalid>()
                }
            }
            repository.guestById("ola") shouldBe
                guest.copy(id = "ola", email = "ola@example.com", role = AppRole.GUEST_USER)
        }

        "admin should be allowed to keep their own email written in another case" {
            givenGuests()

            updateGuest(user("kari", AppRole.ADMIN), "kari", GuestChange(email = " KARI@Example.com "))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>()
        }

        "database should have the last word when another guest gets the email after the conflict check" {
            givenGuests()
            val racing = spyk(repository)
            coEvery { racing.guestsByEmail(any()) } returns emptyList()

            GuestProfileService(racing)
                .update(
                    user("kari", AppRole.ADMIN),
                    "ola",
                    GuestChange(email = "kari@example.com").toRequest(repository.guestById("ola"), 1),
                )
                .shouldBeInstanceOf<GuestUpdateResult.Conflict>()
            repository.guestById("ola")!!.email shouldBe "ola@example.com"
        }

        "update with a stale version should be a version mismatch and change nothing" {
            givenGuests()
            val admin = user("kari", AppRole.ADMIN)
            updateGuest(admin, "ola", GuestChange(firstName = "First")).shouldBeInstanceOf<GuestUpdateResult.Updated>()

            service.update(admin, "ola", GuestChange(firstName = "Second").toRequest(repository.guestById("ola"), 1))
                .shouldBeInstanceOf<GuestUpdateResult.VersionMismatch>()
            service.updateOwnProfile(user("ola", AppRole.GUEST_USER), MeUpdate(1, "Third", "B", 1970, Gender.MALE))
                .shouldBeInstanceOf<GuestUpdateResult.VersionMismatch>()
            repository.guestById("ola")!!.firstName shouldBe "First"
        }

        "update with the current version should bump it, and an update that changes nothing should keep it" {
            givenGuests()
            val admin = user("kari", AppRole.ADMIN)

            val changed = updateGuest(admin, "ola", GuestChange(firstName = "Changed"))
            val unchanged = updateGuest(admin, "ola", GuestChange(firstName = "Changed"))

            changed.shouldBeInstanceOf<GuestUpdateResult.Updated>().guest.version shouldBe 2
            unchanged.shouldBeInstanceOf<GuestUpdateResult.Updated>().guest.version shouldBe 2
        }

        "the version should be checked after not found and before the email conflict" {
            givenGuests()
            val admin = user("kari", AppRole.ADMIN)

            service.update(admin, "unknown", GuestChange(firstName = "X").toRequest(repository.guestById("unknown"), 9))
                .shouldBeInstanceOf<GuestUpdateResult.NotFound>()
            service.update(
                admin,
                "ola",
                GuestChange(email = "kari@example.com").toRequest(repository.guestById("ola"), 9),
            )
                .shouldBeInstanceOf<GuestUpdateResult.VersionMismatch>()
        }

        "the database should have the last word on the version when it changes after the service read it" {
            givenGuests()
            val racing = spyk(repository)
            coEvery { racing.update("ola", any(), any(), any()) } returns GuestUpdateOutcome.VersionMismatch

            GuestProfileService(racing)
                .update(
                    user("kari", AppRole.ADMIN),
                    "ola",
                    GuestChange(firstName = "X").toRequest(repository.guestById("ola"), 1),
                )
                .shouldBeInstanceOf<GuestUpdateResult.VersionMismatch>()
        }

        "the guest that disappears between the check and the write should be not found" {
            givenGuests()
            val racing = spyk(repository)
            coEvery { racing.update("ola", any(), any(), any()) } returns GuestUpdateOutcome.NotFound

            GuestProfileService(racing)
                .update(
                    user("kari", AppRole.ADMIN),
                    "ola",
                    GuestChange(firstName = "X").toRequest(repository.guestById("ola"), 1),
                )
                .shouldBeInstanceOf<GuestUpdateResult.NotFound>()
        }

        "the last admin with an email should keep the role, but not when there is another admin with an email" {
            repository.addOrUpdate(guest.copy(id = "a", email = "a@example.com", role = AppRole.ADMIN))
            repository.addOrUpdate(guest.copy(id = "b", email = "b@example.com", role = AppRole.ADMIN))
            repository.addOrUpdate(guest.copy(id = "c", email = null, role = AppRole.ADMIN))
            val actor = user("a", AppRole.ADMIN)

            updateGuest(actor, "b", GuestChange(role = AppRole.FAMILY_USER))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>()

            // "c" has no email and "a" is the actor but not the target: removing "a" would leave nobody with an email
            repository.addOrUpdate(guest.copy(id = "d", email = "d@example.com", role = AppRole.FAMILY_USER))
            val result = updateGuest(user("c", AppRole.ADMIN), "a", GuestChange(role = AppRole.FAMILY_USER))

            result shouldBe GuestUpdateResult.Conflict(LAST_ADMIN_MESSAGE)
            repository.guestById("a")!!.role shouldBe AppRole.ADMIN
        }

        "email should be stored trimmed and in lowercase, and a blank email should clear it" {
            givenGuests()
            val admin = user("kari", AppRole.ADMIN)

            updateGuest(admin, "ola", GuestChange(email = "  New.Name@Example.COM "))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>().guest.email shouldBe "new.name@example.com"

            listOf("   ", "").forEach { blank ->
                repository.addOrUpdate(guest.copy(id = "ola", email = "ola@example.com", role = AppRole.GUEST_USER))
                val stored = repository.guestById("ola")!!

                val result = service.update(
                    admin,
                    "ola",
                    GuestChange().applyTo(stored).copy(email = blank).toRequest(stored.version),
                )

                withClue("'$blank'") {
                    result.shouldBeInstanceOf<GuestUpdateResult.Updated>().guest.email shouldBe null
                }
            }
        }

        "null role and email should be cleared by a full update, and only the given properties are replaced" {
            givenGuests()
            val stored = repository.guestById("ola")!!

            val result = service.update(
                user("kari", AppRole.ADMIN),
                "ola",
                GuestChange().applyTo(stored).copy(email = null, role = null).toRequest(stored.version),
            )

            result.shouldBeInstanceOf<GuestUpdateResult.Updated>().guest.let {
                it.email shouldBe null
                it.role shouldBe null
                it.version shouldBe 2
                it.firstName shouldBe stored.firstName
            }
        }

        "an admin should not clear their own role or email" {
            repository.addOrUpdate(guest.copy(id = "kari", email = "kari@example.com", role = AppRole.ADMIN))
            val self = repository.guestById("kari")!!
            val kari = user("kari", AppRole.ADMIN)

            service.update(kari, "kari", GuestChange().applyTo(self).copy(role = null).toRequest(self.version))
                .shouldBeInstanceOf<GuestUpdateResult.Forbidden>()
            service.update(kari, "kari", GuestChange().applyTo(self).copy(email = null).toRequest(self.version))
                .shouldBeInstanceOf<GuestUpdateResult.Forbidden>()
        }

        "the last admin with an email should keep the role and the email, also when cleared with null" {
            repository.addOrUpdate(guest.copy(id = "kari", email = "kari@example.com", role = AppRole.ADMIN))
            // An admin without an email cannot log in, so it does not count
            repository.addOrUpdate(guest.copy(id = "tom", email = null, role = AppRole.ADMIN))
            val tom = user("tom", AppRole.ADMIN)
            val kari = repository.guestById("kari")!!

            listOf(GuestChange().applyTo(kari).copy(role = null), GuestChange().applyTo(kari).copy(email = null))
                .forEach { clear ->
                    service.update(tom, "kari", clear.toRequest(kari.version)) shouldBe
                        GuestUpdateResult.Conflict(LAST_ADMIN_MESSAGE)
                }

            repository.addOrUpdate(guest.copy(id = "per", email = "per@example.com", role = AppRole.ADMIN))
            service.update(tom, "kari", GuestChange().applyTo(kari).copy(role = null).toRequest(kari.version))
                .shouldBeInstanceOf<GuestUpdateResult.Updated>()
        }
    })
