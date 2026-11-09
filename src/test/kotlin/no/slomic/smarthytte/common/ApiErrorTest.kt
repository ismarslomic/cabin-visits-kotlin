package no.slomic.smarthytte.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class ApiErrorTest :
    StringSpec({
        "api error should expose its code and message, and serialize both" {
            val error = ApiError(ApiError.NOT_FOUND, "The guest does not exist")

            error.code shouldBe "NOT_FOUND"
            error.message shouldBe "The guest does not exist"
            Json.encodeToString(ApiError.serializer(), error) shouldBe
                """{"code":"NOT_FOUND","message":"The guest does not exist"}"""
        }
    })
