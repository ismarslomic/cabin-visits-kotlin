package no.slomic.smarthytte.auth

import com.auth0.jwt.algorithms.Algorithm
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.configureApiRoutes
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup
import kotlin.time.Duration.Companion.hours

private const val ADMIN_ONLY_PATH = "/test/admin-only"

/** A valid token whose payload is replaced by the payload of another token, so the signature no longer matches. */
private fun tamperedToken(): String {
    val (header, _, signature) = accessToken("ola@example.com").split('.')
    val forgedPayload = accessToken("admin@example.com").split('.')[1]
    return "$header.$forgedPayload.$signature"
}

class AuthRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guestRepository = SqliteGuestRepository()

        suspend fun givenUser(id: String, role: AppRole?) {
            guestRepository.addOrUpdate(testGuest(id, firstName = "Test", appRole = role))
        }

        fun withApp(
            cloudflareAccess: CloudflareAccessConfig? = testCloudflareAccess,
            block: suspend ApplicationTestBuilder.() -> Unit,
        ) = testApplication {
            application {
                configureApiRoutes(
                    guestRepository,
                    GuestProfileService(guestRepository),
                    mockk<StatsService>(),
                    AppUserService(guestRepository),
                    cloudflareAccess,
                )
                routing {
                    authorized(AppRole.ADMIN) {
                        get(ADMIN_ONLY_PATH) { call.respondText("secret") }
                    }
                }
            }
            block()
        }

        suspend fun ApplicationTestBuilder.get(path: String, token: String?) =
            client.get(path) { token?.let { header(ACCESS_JWT_HEADER, it) } }

        "me should respond with the user and role for a valid token" {
            givenUser("ola", AppRole.ADMIN)

            withApp {
                val response = get("/api/me", accessToken("ola@example.com"))

                response.status shouldBe HttpStatusCode.OK
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["id"]?.jsonPrimitive?.content shouldBe "ola"
                body["name"]?.jsonPrimitive?.content shouldBe "Test ola"
                body["email"]?.jsonPrimitive?.content shouldBe "ola@example.com"
                body["role"]?.jsonPrimitive?.content shouldBe "ADMIN"
            }
        }

        "me should be available to every role" {
            givenUser("ola", AppRole.GUEST_USER)
            givenUser("kari", AppRole.FAMILY_USER)

            withApp {
                get("/api/me", accessToken("ola@example.com")).status shouldBe HttpStatusCode.OK
                get("/api/me", accessToken("kari@example.com")).status shouldBe HttpStatusCode.OK
            }
        }

        "me should respond 401 without the token header" {
            withApp { get("/api/me", token = null).status shouldBe HttpStatusCode.Unauthorized }
        }

        "me should respond 401 for tokens that are invalid" {
            givenUser("ola", AppRole.ADMIN)
            val tokens = mapOf(
                "not a jwt" to "not-a-jwt",
                "signed by another key" to tokenSignedByOtherKey(),
                "expired" to accessToken(expiresIn = (-1).hours),
                "not yet valid" to accessToken(notBeforeIn = 1.hours),
                "another application" to accessToken(audience = listOf("another-application")),
                "another issuer" to accessToken(issuer = "https://evil.cloudflareaccess.com"),
                "unknown key id" to accessToken(kid = "unknown"),
                "no key id" to accessToken(kid = null),
                "symmetric algorithm" to accessToken(algorithm = Algorithm.HMAC256("secret")),
                "unsigned" to accessToken(algorithm = Algorithm.none()),
                "tampered payload" to tamperedToken(),
            )

            withApp {
                tokens.forEach { (reason, token) ->
                    withClue(reason) { get("/api/me", token).status shouldBe HttpStatusCode.Unauthorized }
                }
            }
        }

        "me should accept a token for several applications when ours is one of them" {
            givenUser("ola", AppRole.ADMIN)

            withApp {
                val token = accessToken("ola@example.com", audience = listOf("other", TEST_AUDIENCE))

                get("/api/me", token).status shouldBe HttpStatusCode.OK
            }
        }

        "me should not trust the email in a header or a token that is not signed by Cloudflare" {
            givenUser("ola", AppRole.ADMIN)

            withApp {
                val forged = tokenSignedByOtherKey()
                val response = client.get("/api/me") {
                    header(ACCESS_JWT_HEADER, forged)
                    header("Cf-Access-Authenticated-User-Email", "ola@example.com")
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "me should respond 403 for a valid token whose email is not a known user" {
            givenUser("ola", AppRole.ADMIN)

            withApp { get("/api/me", accessToken("stranger@example.com")).status shouldBe HttpStatusCode.Forbidden }
        }

        "me should respond 403 for a known guest without a role" {
            givenUser("ola", role = null)

            withApp { get("/api/me", accessToken("ola@example.com")).status shouldBe HttpStatusCode.Forbidden }
        }

        "me should respond 401 for a valid token without email, such as a service token" {
            withApp { get("/api/me", accessToken(email = null)).status shouldBe HttpStatusCode.Unauthorized }
        }

        "route restricted to a role should respond 403 for users with another role" {
            givenUser("ola", AppRole.ADMIN)
            givenUser("kari", AppRole.FAMILY_USER)
            givenUser("per", AppRole.GUEST_USER)

            withApp {
                get(ADMIN_ONLY_PATH, accessToken("ola@example.com")).status shouldBe HttpStatusCode.OK
                get(ADMIN_ONLY_PATH, accessToken("kari@example.com")).status shouldBe HttpStatusCode.Forbidden
                get(ADMIN_ONLY_PATH, accessToken("per@example.com")).status shouldBe HttpStatusCode.Forbidden
            }
        }

        "route restricted to a role should respond 401 without token and 403 for unknown user" {
            withApp {
                get(ADMIN_ONLY_PATH, token = null).status shouldBe HttpStatusCode.Unauthorized
                get(ADMIN_ONLY_PATH, accessToken("stranger@example.com")).status shouldBe HttpStatusCode.Forbidden
            }
        }

        "role changes should apply to the next request" {
            givenUser("ola", AppRole.FAMILY_USER)

            withApp {
                get(ADMIN_ONLY_PATH, accessToken("ola@example.com")).status shouldBe HttpStatusCode.Forbidden
                guestRepository.addOrUpdate(testGuest("ola", firstName = "Test", appRole = AppRole.ADMIN))
                get(ADMIN_ONLY_PATH, accessToken("ola@example.com")).status shouldBe HttpStatusCode.OK
                guestRepository.addOrUpdate(testGuest("ola", firstName = "Test"))
                get(ADMIN_ONLY_PATH, accessToken("ola@example.com")).status shouldBe HttpStatusCode.Forbidden
            }
        }

        "protected routes should respond 503 and never accept a token when Cloudflare Access is not configured" {
            givenUser("ola", AppRole.ADMIN)

            withApp(cloudflareAccess = null) {
                get("/api/me", accessToken("ola@example.com")).status shouldBe HttpStatusCode.ServiceUnavailable
                get(ADMIN_ONLY_PATH, accessToken("ola@example.com")).status shouldBe HttpStatusCode.ServiceUnavailable
            }
        }

        "existing endpoints should stay available without authentication" {
            withApp {
                get("/", token = null).status shouldBe HttpStatusCode.OK
                get("/api/guests/unknown/avatar", token = null).status shouldBe HttpStatusCode.NotFound
            }
            withApp(cloudflareAccess = null) {
                get("/", token = null).status shouldBe HttpStatusCode.OK
            }
        }
    })
