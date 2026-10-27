package no.slomic.smarthytte.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.utils.TestDbSetup

fun testGuest(
    id: String,
    email: String? = "$id@example.com",
    firstName: String = "First",
    lastName: String = id,
    appRole: AppRole? = null,
) = Guest(
    id = id,
    firstName = firstName,
    lastName = lastName,
    birthYear = 1980,
    email = email,
    gender = Gender.MALE,
    isFamily = true,
    appRole = appRole,
)

class AppUserServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guestRepository = SqliteGuestRepository()
        val repository = AppUserService(guestRepository)

        "guest with a role should be found by email, ignoring case" {
            guestRepository.addOrUpdate(
                testGuest(
                    "kari",
                    email = "Kari.Nordmann@Example.com",
                    firstName = "Kari",
                    appRole = AppRole.FAMILY_USER,
                ),
            )

            repository.userByEmail("kari.nordmann@example.com") shouldBe AppUser(
                id = "kari",
                name = "Kari kari",
                email = "Kari.Nordmann@Example.com",
                role = AppRole.FAMILY_USER,
            )
            repository.userByEmail(" KARI.NORDMANN@EXAMPLE.COM ")?.id shouldBe "kari"
        }

        "guest without a role should not be found, such as an external guest" {
            guestRepository.addOrUpdate(testGuest("ola"))

            repository.userByEmail("ola@example.com").shouldBeNull()
        }

        "blank or missing email should never be found" {
            guestRepository.addOrUpdate(testGuest("ola", email = "", appRole = AppRole.ADMIN))

            repository.userByEmail(null).shouldBeNull()
            repository.userByEmail("  ").shouldBeNull()
        }

        "unknown email should not be found" {
            guestRepository.addOrUpdate(testGuest("ola", appRole = AppRole.ADMIN))

            repository.userByEmail("unknown@example.com").shouldBeNull()
        }

        "guest without email should never be found" {
            guestRepository.addOrUpdate(testGuest("ola", email = null, appRole = AppRole.ADMIN))

            repository.userByEmail("").shouldBeNull()
        }

        "several guests with a role and the same email is ambiguous and should not be found" {
            guestRepository.addOrUpdate(testGuest("ola", email = "shared@example.com", appRole = AppRole.ADMIN))
            guestRepository.addOrUpdate(testGuest("kari", email = "shared@example.com", appRole = AppRole.GUEST_USER))

            repository.userByEmail("shared@example.com").shouldBeNull()
        }

        "guest without a role sharing the email should not make the guest with a role ambiguous" {
            guestRepository.addOrUpdate(testGuest("ola", email = "shared@example.com", appRole = AppRole.ADMIN))
            guestRepository.addOrUpdate(testGuest("kari", email = "shared@example.com"))

            repository.userByEmail("shared@example.com")?.id shouldBe "ola"
        }

        "syncing a guest from file should add, change and revoke the role" {
            guestRepository.addOrUpdate(testGuest("ola", appRole = AppRole.GUEST_USER))
            repository.userByEmail("ola@example.com")?.role shouldBe AppRole.GUEST_USER

            guestRepository.addOrUpdate(testGuest("ola", firstName = "Changed", appRole = AppRole.ADMIN))
            repository.userByEmail("ola@example.com")?.role shouldBe AppRole.ADMIN
            repository.userByEmail("ola@example.com")?.name shouldBe "Changed ola"

            guestRepository.addOrUpdate(testGuest("ola"))
            repository.userByEmail("ola@example.com").shouldBeNull()
        }

        "syncing an unchanged guest with a role from file should be no action" {
            guestRepository.addOrUpdate(testGuest("ola", appRole = AppRole.ADMIN))

            guestRepository.addOrUpdate(testGuest("ola", appRole = AppRole.ADMIN)) shouldBe PersistenceResult.NO_ACTION
        }
    })
