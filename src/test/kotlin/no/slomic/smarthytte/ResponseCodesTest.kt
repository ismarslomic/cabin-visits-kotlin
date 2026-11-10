package no.slomic.smarthytte

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.auth.ACCESS_JWT_HEADER
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.CloudflareAccessConfig
import no.slomic.smarthytte.auth.accessToken
import no.slomic.smarthytte.auth.testCloudflareAccess
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.reservations.ReservationService
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.configureTestApiRoutes

/**
 * Status codes of the OpenAPI spec that the tests of the individual routes do not provoke: the 503 of every guarded
 * route, the 403 of the avatar read, the validation of the statistics path parameters and the unexpected server error
 * of the statistics routes. `ResponseCodeAudit` fails the run when a documented code is answered by no test.
 */
class ResponseCodesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guestRepository = SqliteGuestRepository()
        val statsService = StatsService(SqliteReservationRepository(), guestRepository, LocalDate(2020, 9, 1))

        fun withApp(
            cloudflareAccess: CloudflareAccessConfig? = testCloudflareAccess,
            stats: StatsService = statsService,
            block: suspend ApplicationTestBuilder.() -> Unit,
        ) = testApplication {
            application {
                configureTestApiRoutes(
                    ApiDependencies(
                        guestRepository = guestRepository,
                        guestProfileService = GuestProfileService(guestRepository),
                        guestAdminService = GuestAdminService(guestRepository),
                        statsService = stats,
                        reservationService = mockk<ReservationService>(),
                        appUserService = AppUserService(guestRepository),
                        cloudflareAccess = cloudflareAccess,
                    ),
                )
            }
            block()
        }

        suspend fun ApplicationTestBuilder.call(method: String, path: String, token: String?): HttpResponse {
            val configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
                token?.let { header(ACCESS_JWT_HEADER, it) }
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("{}")
            }
            return when (method) {
                "post" -> client.post(path, configure)
                "put" -> client.put(path, configure)
                else -> client.get(path, configure)
            }
        }

        "every guarded guest route should respond 503 when Cloudflare Access is not configured" {
            guestRepository.addOrUpdate(testGuest("admin", role = AppRole.ADMIN))

            withApp(cloudflareAccess = null) {
                val token = accessToken("admin@example.com")

                listOf(
                    "get" to "/api/me",
                    "put" to "/api/me",
                    "get" to "/api/guests",
                    "post" to "/api/guests",
                    "put" to "/api/guests/admin",
                    "get" to "/api/guests/admin/avatar",
                    "get" to "/api/stats",
                ).forEach { (method, path) ->
                    withClue("$method $path") {
                        call(method, path, token).status shouldBe HttpStatusCode.ServiceUnavailable
                    }
                }
            }
        }

        "get avatar should respond 403 for a person without a role" {
            guestRepository.addOrUpdate(testGuest("external"))

            withApp {
                call("get", "/api/guests/external/avatar", accessToken("external@example.com")).status shouldBe
                    HttpStatusCode.Forbidden
            }
        }

        "statistics for a year and a month should respond 200, and 400 for an invalid parameter" {
            guestRepository.addOrUpdate(testGuest("viewer", role = AppRole.GUEST_USER))

            withApp {
                val token = accessToken("viewer@example.com")

                call("get", "/api/stats/years/2024", token).status shouldBe HttpStatusCode.OK
                call("get", "/api/stats/years/2024/months/5", token).status shouldBe HttpStatusCode.OK

                listOf(
                    "/api/stats/years/abc",
                    "/api/stats/years/abc/months/5",
                    "/api/stats/years/2024/months/abc",
                    "/api/stats/years/2024/months/0",
                    "/api/stats/years/2024/months/13",
                ).forEach { path ->
                    withClue(path) { call("get", path, token).status shouldBe HttpStatusCode.BadRequest }
                }
            }
        }

        "statistics should respond 500 for an unexpected error" {
            guestRepository.addOrUpdate(testGuest("viewer", role = AppRole.GUEST_USER))
            val failing = mockk<StatsService>()
            coEvery { failing.getLiveStats() } throws IllegalStateException("boom")
            coEvery { failing.getCurrentYearStats() } throws IllegalStateException("boom")
            coEvery { failing.getAvailableYears() } throws IllegalStateException("boom")
            coEvery { failing.getAllGuestsStats() } throws IllegalStateException("boom")

            withApp(stats = failing) {
                val token = accessToken("viewer@example.com")

                listOf("/api/stats", "/api/stats/current-year", "/api/stats/years", "/api/stats/guests").forEach {
                    withClue(it) {
                        call("get", it, token).status shouldBe HttpStatusCode.InternalServerError
                    }
                }
            }
        }
    })
