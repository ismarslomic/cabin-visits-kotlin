package no.slomic.smarthytte.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.Gender
import kotlin.time.Instant

class MeResponseTest :
    StringSpec({
        val user = UserPrincipal(
            id = "ola.nordmann",
            name = "Ola Nordmann",
            firstName = "Ola",
            lastName = "Nordmann",
            birthYear = 1980,
            gender = Gender.MALE,
            email = "ola@example.com",
            isFamily = true,
            role = AppRole.FAMILY_USER,
            version = 3,
        )

        "me response should carry every property of the user and a versioned avatarUrl" {
            val response = user.toMeResponse(Instant.fromEpochMilliseconds(1_762_700_000_000))

            response.id shouldBe "ola.nordmann"
            response.name shouldBe "Ola Nordmann"
            response.firstName shouldBe "Ola"
            response.lastName shouldBe "Nordmann"
            response.birthYear shouldBe 1980
            response.gender shouldBe Gender.MALE
            response.email shouldBe "ola@example.com"
            response.isFamily shouldBe true
            response.role shouldBe AppRole.FAMILY_USER
            response.avatarUrl shouldBe "/api/guests/ola.nordmann/avatar?v=1762700000000"
            response.version shouldBe 3
        }

        "me response should have no avatarUrl without an avatar" {
            user.toMeResponse(null).avatarUrl shouldBe null
        }
    })
