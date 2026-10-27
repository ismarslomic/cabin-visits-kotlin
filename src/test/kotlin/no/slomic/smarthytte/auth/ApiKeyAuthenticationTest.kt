package no.slomic.smarthytte.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.configureApiRoutes
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.properties.ApiKeyProperties
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup

private const val TEST_API_KEY = "test-mirror-key"
private val testApiKeys = ApiKeyProperties("mirror:${sha256Hex(TEST_API_KEY)}").toConfig()

/** The routes MagicMirror may call with its API key. */
private val serviceRoutes = listOf("/api/stats", "/api/stats/guests/ola", "/api/guests/ola/avatar")

/** Routes that exist, but are not for services. */
private val userOnlyRoutes = listOf(
    "/api/me",
    "/api/stats/current-year",
    "/api/stats/years",
    "/api/stats/years/2020",
    "/api/stats/years/2020/months/1",
    "/api/stats/guests",
)

class ApiKeyAuthenticationTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guestRepository = SqliteGuestRepository()
        val statsService = StatsService(SqliteReservationRepository(), guestRepository, LocalDate(2020, 9, 1))

        suspend fun givenUser(id: String, role: AppRole?) {
            guestRepository.addOrUpdate(testGuest(id, firstName = "Test", appRole = role))
        }

        fun withApp(
            cloudflareAccess: CloudflareAccessConfig? = testCloudflareAccess,
            apiKeys: ApiKeyConfig = testApiKeys,
            block: suspend ApplicationTestBuilder.() -> Unit,
        ) = testApplication {
            application {
                configureApiRoutes(
                    guestRepository,
                    GuestProfileService(guestRepository),
                    statsService,
                    AppUserService(guestRepository),
                    cloudflareAccess,
                    apiKeys,
                )
            }
            block()
        }

        suspend fun ApplicationTestBuilder.get(path: String, apiKey: String? = null, token: String? = null) =
            client.get(path) {
                apiKey?.let { header(API_KEY_HEADER, it) }
                token?.let { header(ACCESS_JWT_HEADER, it) }
            }

        "service with a valid key should call the routes that allow services" {
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                get("/api/stats", TEST_API_KEY).status shouldBe HttpStatusCode.OK
                get("/api/stats/guests/ola", TEST_API_KEY).status shouldBe HttpStatusCode.OK
                // The guest has no avatar, so reaching the route is a 404 and not a 401 or 403
                get("/api/guests/ola/avatar", TEST_API_KEY).status shouldBe HttpStatusCode.NotFound
            }
        }

        "service should be denied with 403 on every other route" {
            withApp {
                userOnlyRoutes.forEach {
                    get(it, TEST_API_KEY).status shouldBe HttpStatusCode.Forbidden
                }
                client.post("/api/me") {
                    header(API_KEY_HEADER, TEST_API_KEY)
                    contentType(ContentType.Application.Json)
                    setBody("""{"firstName":"X"}""")
                }.status shouldBe HttpStatusCode.Forbidden
                client.post("/api/guests/ola") {
                    header(API_KEY_HEADER, TEST_API_KEY)
                    contentType(ContentType.Application.Json)
                    setBody("""{"firstName":"X"}""")
                }.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "wrong, empty or missing key should respond 401 on every protected route" {
            withApp {
                (serviceRoutes + userOnlyRoutes).forEach { path ->
                    listOf("wrong-key", "", TEST_API_KEY.uppercase(), "$TEST_API_KEY ").forEach {
                        get(path, apiKey = it).status shouldBe HttpStatusCode.Unauthorized
                    }
                    get(path).status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }

        "key should not authenticate when no API keys are configured" {
            withApp(apiKeys = ApiKeyConfig(emptyMap())) {
                get("/api/stats", TEST_API_KEY).status shouldBe HttpStatusCode.Unauthorized
                get("/api/stats", sha256Hex(TEST_API_KEY)).status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "the hash of a key should not be accepted as the key" {
            withApp {
                get("/api/stats", sha256Hex(TEST_API_KEY)).status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "service should still authenticate when Cloudflare Access is not configured" {
            givenUser("ola", AppRole.ADMIN)

            withApp(cloudflareAccess = null) {
                get("/api/stats", TEST_API_KEY).status shouldBe HttpStatusCode.OK
                get("/api/me", TEST_API_KEY).status shouldBe HttpStatusCode.Forbidden
                get("/api/stats", token = accessToken("ola@example.com")).status shouldBe
                    HttpStatusCode.ServiceUnavailable
                get("/api/stats").status shouldBe HttpStatusCode.ServiceUnavailable
            }
        }

        "users should keep access to the routes of services, and a user token should not be a service key" {
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                serviceRoutes.forEach {
                    get(it, token = accessToken("ola@example.com")).status shouldBe
                        if (it.endsWith("/avatar")) HttpStatusCode.NotFound else HttpStatusCode.OK
                }
                get("/api/stats", apiKey = accessToken("ola@example.com")).status shouldBe
                    HttpStatusCode.Unauthorized
            }
        }

        "a valid user token should be used when the key is wrong, and a valid key when the token is invalid" {
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                get("/api/stats", "wrong", accessToken("ola@example.com")).status shouldBe HttpStatusCode.OK
                get("/api/stats", TEST_API_KEY, tokenSignedByOtherKey()).status shouldBe HttpStatusCode.OK
                get("/api/stats", "wrong", tokenSignedByOtherKey()).status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "unknown person with a valid token should still get 403, and nothing without credentials leaks details" {
            withApp {
                val response = get("/api/stats", token = accessToken("nobody@example.com"))

                response.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "api key properties should parse several services and ignore blank entries" {
            val config = ApiKeyProperties(
                " mirror:${sha256Hex("a").uppercase()} , ,other:${sha256Hex("b")},",
            ).toConfig()

            config.principalFor("a") shouldBe ServicePrincipal("mirror")
            config.principalFor("b") shouldBe ServicePrincipal("other")
            config.principalFor("c").shouldBeNull()
            ApiKeyProperties("").toConfig().principalFor("a").shouldBeNull()
        }

        "api key properties should fail at startup for a malformed entry" {
            listOf(
                "mirror",
                ":${sha256Hex("a")}",
                "mirror:not-hex",
                "mirror:abcd",
                "mirror:${sha256Hex("a")}00",
            ).forEach {
                shouldThrow<IllegalArgumentException> { ApiKeyProperties(it).toConfig() }
            }
        }
    })
