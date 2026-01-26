package no.slomic.smarthytte.skistats

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class OAuthTokenResponseTest :
    StringSpec({
        "should deserialize OAuthTokenResponse with Instant fields" {
            val json = """
            {
                "user_id": "user123",
                "client_id": "client456",
                "environment": "prod",
                "agent_id": "agent789",
                "issuedAtUtc": "2026-01-26T22:12:31Z",
                "expiresAtUtc": "2026-01-26T22:42:31Z",
                "access_token": "abc",
                "token_type": "Bearer",
                "expires_in": 3600,
                "scope": "read",
                "refresh_token": "def"
            }
            """.trimIndent()

            val response = Json.decodeFromString<OAuthTokenResponse>(json)

            response.userId shouldBe "user123"
            response.issuedAtUtc shouldBe Instant.parse("2026-01-26T22:12:31Z")
            response.expiresAtUtc shouldBe Instant.parse("2026-01-26T22:42:31Z")
        }

        "should serialize OAuthTokenResponse with Instant fields" {
            val response = OAuthTokenResponse(
                userId = "user123",
                clientId = "client456",
                environment = "prod",
                agentId = "agent789",
                issuedAtUtc = Instant.parse("2026-01-26T22:12:31Z"),
                expiresAtUtc = Instant.parse("2026-01-26T22:42:31Z"),
                accessToken = "abc",
                tokenType = "Bearer",
                expiresIn = 3600,
                scope = "read",
                refreshToken = "def",
            )

            val actualJson = Json.encodeToString(response)
            val expectedJson = """
            {
                "user_id": "user123",
                "client_id": "client456",
                "environment": "prod",
                "agent_id": "agent789",
                "issuedAtUtc": "2026-01-26T22:12:31Z",
                "expiresAtUtc": "2026-01-26T22:42:31Z",
                "access_token": "abc",
                "token_type": "Bearer",
                "expires_in": 3600,
                "scope": "read",
                "refresh_token": "def"
            }
            """.trimIndent()

            actualJson shouldEqualJson expectedJson
        }

        "should deserialize OAuthTokenResponse with missing optional fields" {
            val json = """
                {
                    "access_token": "abc",
                    "refresh_token": "def"
                }
            """.trimIndent()

            val response = Json.decodeFromString<OAuthTokenResponse>(json)

            response.accessToken shouldBe "abc"
            response.refreshToken shouldBe "def"
            response.userId shouldBe null
            response.issuedAtUtc shouldBe null
        }

        "should fail to deserialize OAuthTokenResponse when access_token is missing" {
            val json = """
                {
                    "refresh_token": "def"
                }
            """.trimIndent()

            val exception = shouldThrow<SerializationException> {
                Json.decodeFromString<OAuthTokenResponse>(json)
            }
            exception.message shouldContain
                "Field 'access_token' is required for type with serial name 'no.slomic.smarthytte.skistats.OAuthTokenResponse', but it was missing"
        }

        "should fail to deserialize OAuthTokenResponse when refresh_token is missing" {
            val json = """
                {
                    "access_token": "abc"
                }
            """.trimIndent()

            val exception = shouldThrow<SerializationException> {
                Json.decodeFromString<OAuthTokenResponse>(json)
            }
            exception.message shouldContain
                "Field 'refresh_token' is required for type with serial name 'no.slomic.smarthytte.skistats.OAuthTokenResponse', but it was missing"
        }
    })
