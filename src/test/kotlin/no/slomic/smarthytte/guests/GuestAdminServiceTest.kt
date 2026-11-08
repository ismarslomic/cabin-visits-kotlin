package no.slomic.smarthytte.guests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.spyk
import no.slomic.smarthytte.utils.TestDbSetup

private val newGuest = GuestCreate(
    firstName = "Ola",
    lastName = "Nordmann",
    birthYear = 1980,
    gender = Gender.MALE,
)

class GuestAdminServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository = SqliteGuestRepository()
        val service = GuestAdminService(repository)

        "create should store the guest with a generated id, version 1 and the actor" {
            val result = service.create(newGuest.copy(email = " Ola@Example.COM ", role = AppRole.GUEST_USER), "kari")

            val guest = result.shouldBeInstanceOf<GuestCreateResult.Created>().guest
            guest.id shouldBe "ola.nordmann"
            guest.email shouldBe "ola@example.com"
            guest.role shouldBe AppRole.GUEST_USER
            guest.isFamily shouldBe false
            guest.version shouldBe 1
            guest.createdBy shouldBe "kari"
            guest.updatedBy shouldBe null
            repository.guestById("ola.nordmann")!!.email shouldBe "ola@example.com"
        }

        "create should trim the names and add a number when the id exists" {
            service.create(newGuest, "kari")

            val second = service.create(newGuest.copy(firstName = " Ola ", email = "second@example.com"), "kari")

            second.shouldBeInstanceOf<GuestCreateResult.Created>().guest.id shouldBe "ola.nordmann-2"
            repository.guestById("ola.nordmann-2")!!.firstName shouldBe "Ola"
        }

        "create should not use system as an id" {
            val result = service.create(newGuest.copy(firstName = "System", lastName = ""), "kari")

            result.shouldBeInstanceOf<GuestCreateResult.Invalid>()
            val created = service.create(newGuest.copy(firstName = "System", lastName = "X"), "kari")
            created.shouldBeInstanceOf<GuestCreateResult.Created>().guest.id shouldBe "system.x"
        }

        "create should treat a blank email as no email, also for several guests" {
            service.create(newGuest.copy(email = ""), "kari").shouldBeInstanceOf<GuestCreateResult.Created>()
            service.create(newGuest.copy(email = "   "), "kari").shouldBeInstanceOf<GuestCreateResult.Created>()

            repository.allGuests().map { it.email } shouldBe listOf(null, null)
        }

        "create should be a conflict when the email is used, ignoring case" {
            service.create(newGuest.copy(email = "ola@example.com"), "kari")

            service.create(newGuest.copy(firstName = "Other", email = "OLA@example.com"), "kari") shouldBe
                GuestCreateResult.EmailInUse
            repository.allGuests().size shouldBe 1
        }

        "create should be invalid for values that are not valid" {
            val invalid = mapOf(
                "blank firstName" to newGuest.copy(firstName = "  "),
                "too long lastName" to newGuest.copy(lastName = "x".repeat(21)),
                "birthYear before 1900" to newGuest.copy(birthYear = 1899),
                "birthYear in the future" to newGuest.copy(birthYear = 3000),
                "email without @" to newGuest.copy(email = "nope"),
                "too long email" to newGuest.copy(email = "x".repeat(250) + "@example.com"),
            )

            invalid.forEach { (reason, request) ->
                withClue(reason) { service.create(request, "kari").shouldBeInstanceOf<GuestCreateResult.Invalid>() }
            }
            repository.allGuests() shouldBe emptyList()
        }

        "database should have the last word when another guest gets the email after the check" {
            service.create(newGuest.copy(email = "ola@example.com"), "kari")
            val racing = spyk(repository)
            coEvery { racing.guestsByEmail(any()) } returns emptyList()

            GuestAdminService(
                racing,
            ).create(newGuest.copy(firstName = "Other", email = "ola@example.com"), "kari") shouldBe
                GuestCreateResult.EmailInUse
        }
    })
