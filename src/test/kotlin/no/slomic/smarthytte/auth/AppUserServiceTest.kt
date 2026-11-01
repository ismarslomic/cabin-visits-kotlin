package no.slomic.smarthytte.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.utils.TestDbSetup

fun testGuest(
    id: String,
    email: String? = "$id@example.com",
    firstName: String = "First",
    lastName: String = id,
    role: AppRole? = null,
) = Guest(
    id = id,
    firstName = firstName,
    lastName = lastName,
    birthYear = 1980,
    email = email,
    gender = Gender.MALE,
    isFamily = true,
    role = role,
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
                    role = AppRole.FAMILY_USER,
                ),
            )

            repository.userByEmail("kari.nordmann@example.com") shouldBe UserPrincipal(
                id = "kari",
                name = "Kari kari",
                firstName = "Kari",
                lastName = "kari",
                birthYear = 1980,
                gender = Gender.MALE,
                email = "Kari.Nordmann@Example.com",
                isFamily = true,
                role = AppRole.FAMILY_USER,
            )
            repository.userByEmail(" KARI.NORDMANN@EXAMPLE.COM ")?.id shouldBe "kari"
        }

        "guest without a role should not be found, such as an external guest" {
            guestRepository.addOrUpdate(testGuest("ola"))

            repository.userByEmail("ola@example.com").shouldBeNull()
        }

        "blank or missing email should never be found" {
            guestRepository.addOrUpdate(testGuest("ola", email = "", role = AppRole.ADMIN))

            repository.userByEmail(null).shouldBeNull()
            repository.userByEmail("  ").shouldBeNull()
        }

        "unknown email should not be found" {
            guestRepository.addOrUpdate(testGuest("ola", role = AppRole.ADMIN))

            repository.userByEmail("unknown@example.com").shouldBeNull()
        }

        "guest without email should never be found" {
            guestRepository.addOrUpdate(testGuest("ola", email = null, role = AppRole.ADMIN))

            repository.userByEmail("").shouldBeNull()
        }

        "syncing a guest from file should add, change and revoke the role" {
            guestRepository.addOrUpdate(testGuest("ola", role = AppRole.GUEST_USER))
            repository.userByEmail("ola@example.com")?.role shouldBe AppRole.GUEST_USER

            guestRepository.addOrUpdate(testGuest("ola", firstName = "Changed", role = AppRole.ADMIN))
            repository.userByEmail("ola@example.com")?.role shouldBe AppRole.ADMIN
            repository.userByEmail("ola@example.com")?.name shouldBe "Changed ola"

            guestRepository.addOrUpdate(testGuest("ola"))
            repository.userByEmail("ola@example.com").shouldBeNull()
        }

        "syncing an unchanged guest with a role from file should be no action" {
            guestRepository.addOrUpdate(testGuest("ola", role = AppRole.ADMIN))

            guestRepository.addOrUpdate(testGuest("ola", role = AppRole.ADMIN)) shouldBe PersistenceResult.NO_ACTION
        }
    })
