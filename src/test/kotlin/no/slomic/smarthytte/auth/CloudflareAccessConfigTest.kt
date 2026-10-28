package no.slomic.smarthytte.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.NetworkException
import com.auth0.jwk.RateLimitReachedException
import com.auth0.jwk.SigningKeyNotFoundException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.slomic.smarthytte.configureApiRoutes
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.properties.CloudflareAccessProperties
import no.slomic.smarthytte.statistics.StatsService

class CloudflareAccessConfigTest :
    StringSpec({
        "properties should be configured only when team domain and audience are both set" {
            CloudflareAccessProperties("myteam.cloudflareaccess.com", "aud").isConfigured shouldBe true
            CloudflareAccessProperties("", "aud").isConfigured shouldBe false
            CloudflareAccessProperties("myteam.cloudflareaccess.com", " ").isConfigured shouldBe false
            CloudflareAccessProperties("", "").toConfig().shouldBeNull()
        }

        "issuer should be https and the team domain without scheme and trailing slash" {
            listOf("myteam.cloudflareaccess.com", "https://myteam.cloudflareaccess.com/").forEach {
                val config = CloudflareAccessProperties(it, "aud").toConfig()!!

                config.issuer shouldBe "https://myteam.cloudflareaccess.com"
                config.audience shouldBe "aud"
            }
        }

        "token should be rejected, not fail the request, when the signing keys cannot be fetched" {
            val failures = listOf(
                NetworkException("Cloudflare is unreachable", null),
                RateLimitReachedException(1),
                SigningKeyNotFoundException("Failed to parse the keys", null),
            )

            failures.forEach { failure ->
                val unreachable = JwkProvider { throw failure }

                testApplication {
                    application {
                        configureApiRoutes(
                            SqliteGuestRepository(),
                            mockk<GuestProfileService>(),
                            mockk<StatsService>(),
                            mockk<AppUserService>(),
                            testCloudflareAccess.copy(jwkProvider = unreachable),
                        )
                    }

                    val response = client.get("/api/me") { header(ACCESS_JWT_HEADER, accessToken()) }

                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    })
