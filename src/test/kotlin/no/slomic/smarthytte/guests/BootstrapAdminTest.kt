package no.slomic.smarthytte.guests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.properties.BootstrapAdminProperties
import no.slomic.smarthytte.utils.TestDbSetup

private val allSet = BootstrapAdminProperties(
    email = " Ola.Nordmann@Example.com ",
    firstName = "Ola",
    lastName = "Nordmann",
    birthYear = "1980",
    gender = "male",
)

private val noneSet = BootstrapAdminProperties("", "", "", "", "")

class BootstrapAdminTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository = SqliteGuestRepository()
        val bootstrap = BootstrapAdmin(repository)

        "no variables should do nothing" {
            bootstrap.run(noneSet) shouldBe BootstrapResult.NotConfigured

            repository.allGuests() shouldBe emptyList()
        }

        "all variables on an empty database should create the first admin with the system as actor" {
            val result = bootstrap.run(allSet)

            result shouldBe BootstrapResult.Created("ola.nordmann")
            (result as BootstrapResult.Created).guestId shouldBe "ola.nordmann"
            repository.guestById("ola.nordmann")!!.let {
                it.role shouldBe AppRole.ADMIN
                it.email shouldBe "ola.nordmann@example.com"
                it.firstName shouldBe "Ola"
                it.lastName shouldBe "Nordmann"
                it.birthYear shouldBe 1980
                it.gender shouldBe Gender.MALE
                it.isFamily shouldBe false
                it.createdBy shouldBe SYSTEM_ACTOR
            }
            repository.hasAdmin() shouldBe true
        }

        "an existing admin should make the bootstrap do nothing" {
            repository.addOrUpdate(testGuest("kari", role = AppRole.ADMIN))

            bootstrap.run(allSet) shouldBe BootstrapResult.AdminExists

            repository.allGuests().map { it.id } shouldBe listOf("kari")
        }

        "an admin without an email should also count as an admin" {
            repository.addOrUpdate(testGuest("kari", email = null, role = AppRole.ADMIN))

            bootstrap.run(allSet) shouldBe BootstrapResult.AdminExists
        }

        "a guest with the email should get the admin role and keep its other properties" {
            repository.addOrUpdate(testGuest("ola", email = "ola.nordmann@example.com", role = AppRole.GUEST_USER))

            val result = bootstrap.run(allSet)

            result shouldBe BootstrapResult.Promoted("ola")
            (result as BootstrapResult.Promoted).guestId shouldBe "ola"
            repository.allGuests().size shouldBe 1
            repository.guestById("ola")!!.let {
                it.role shouldBe AppRole.ADMIN
                it.firstName shouldBe "First"
                it.version shouldBe 2
                it.updatedBy shouldBe SYSTEM_ACTOR
            }
        }

        "a guest without a role and the email should be promoted, and a second run does nothing" {
            repository.addOrUpdate(testGuest("ola", email = "ola.nordmann@example.com"))

            bootstrap.run(allSet) shouldBe BootstrapResult.Promoted("ola")
            bootstrap.run(allSet) shouldBe BootstrapResult.AdminExists
        }

        "partly set variables should fail startup and name the missing ones" {
            val partly = allSet.copy(lastName = "", gender = " ")

            val error = shouldThrow<IllegalStateException> { bootstrap.run(partly) }

            error.message shouldContain "BOOTSTRAP_ADMIN_LAST_NAME"
            error.message shouldContain "BOOTSTRAP_ADMIN_GENDER"
            repository.allGuests() shouldBe emptyList()
        }

        "partly set variables should also fail when an admin exists" {
            repository.addOrUpdate(testGuest("kari", role = AppRole.ADMIN))

            shouldThrow<IllegalStateException> { bootstrap.run(allSet.copy(email = "")) }
        }

        "a birth year or gender that cannot be read should fail startup" {
            shouldThrow<IllegalStateException> { bootstrap.run(allSet.copy(birthYear = "abc")) }
                .message shouldContain "BOOTSTRAP_ADMIN_BIRTH_YEAR"
            shouldThrow<IllegalStateException> { bootstrap.run(allSet.copy(gender = "OTHER")) }
                .message shouldContain "BOOTSTRAP_ADMIN_GENDER"
        }

        "values that the guest validation rejects should fail startup" {
            shouldThrow<IllegalStateException> { bootstrap.run(allSet.copy(firstName = "x".repeat(21))) }
                .message shouldContain "firstName"
            shouldThrow<IllegalStateException> { bootstrap.run(allSet.copy(email = "not-an-email")) }
                .message shouldContain "email"
            shouldThrow<IllegalStateException> { bootstrap.run(allSet.copy(birthYear = "1800")) }
                .message shouldContain "birthYear"
            repository.allGuests() shouldBe emptyList()
        }

        "an email that another guest uses should not be created twice" {
            // The email is normalised before it is compared, so it matches the stored guest and promotes it
            repository.addOrUpdate(testGuest("ola", email = "ola.nordmann@example.com"))

            bootstrap.run(allSet.copy(email = "OLA.NORDMANN@EXAMPLE.COM")) shouldBe BootstrapResult.Promoted("ola")
        }
    })
