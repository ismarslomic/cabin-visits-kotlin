package no.slomic.smarthytte.statistics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.auth.ACCESS_JWT_HEADER
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.accessToken
import no.slomic.smarthytte.auth.testCloudflareAccess
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.auth.tokenSignedByOtherKey
import no.slomic.smarthytte.configureApiRoutes
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.utils.TestDbSetup

class StatsRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guestRepository: GuestRepository = SqliteGuestRepository()
        val reservationRepository: ReservationRepository = SqliteReservationRepository()
        val service = StatsService(reservationRepository, guestRepository, LocalDate(2020, 9, 1))
        val userToken = accessToken("viewer@example.com")

        beforeTest {
            guestRepository.addOrUpdate(testGuest("viewer", appRole = AppRole.GUEST_USER))
        }

        "get guest profile should respond with the guest, visits and fun facts" {
            guestRepository.addOrUpdate(Guest("anna", "Anna", "Test", 1990, gender = Gender.FEMALE))

            testApplication {
                application {
                    configureApiRoutes(
                        guestRepository,
                        GuestProfileService(guestRepository),
                        service,
                        AppUserService(guestRepository),
                        testCloudflareAccess,
                    )
                }

                val response = client.get("/api/stats/guests/anna") { header(ACCESS_JWT_HEADER, userToken) }

                response.status shouldBe HttpStatusCode.OK
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body.getValue("guest").jsonObject.getValue("guestId").jsonPrimitive.content shouldBe "anna"
                body.getValue("visits").jsonArray.size shouldBe 0
                body.getValue("funFacts").jsonArray.size shouldBe 0
            }
        }

        "get guest profile for an unknown guest should respond not found" {
            testApplication {
                application {
                    configureApiRoutes(
                        guestRepository,
                        GuestProfileService(guestRepository),
                        service,
                        AppUserService(guestRepository),
                        testCloudflareAccess,
                    )
                }

                val response = client.get("/api/stats/guests/unknown") { header(ACCESS_JWT_HEADER, userToken) }

                response.status shouldBe HttpStatusCode.NotFound
                response.bodyAsText() shouldContain "Guest not found"
            }
        }

        "statistics should respond 401 without a valid token and 403 for a person without a role" {
            guestRepository.addOrUpdate(testGuest("external"))

            testApplication {
                application {
                    configureApiRoutes(
                        guestRepository,
                        GuestProfileService(guestRepository),
                        service,
                        AppUserService(guestRepository),
                        testCloudflareAccess,
                    )
                }

                listOf("/api/stats", "/api/stats/current-year", "/api/stats/years", "/api/stats/guests").forEach {
                    client.get(it).status shouldBe HttpStatusCode.Unauthorized
                    client.get(it) { header(ACCESS_JWT_HEADER, tokenSignedByOtherKey()) }.status shouldBe
                        HttpStatusCode.Unauthorized
                    client.get(it) { header(ACCESS_JWT_HEADER, accessToken("external@example.com")) }.status shouldBe
                        HttpStatusCode.Forbidden
                    client.get(it) { header(ACCESS_JWT_HEADER, userToken) }.status shouldBe HttpStatusCode.OK
                }
            }
        }

        "statistics should respond 503 when Cloudflare Access is not configured" {
            testApplication {
                application {
                    configureApiRoutes(
                        guestRepository,
                        GuestProfileService(guestRepository),
                        service,
                        AppUserService(guestRepository),
                        null,
                    )
                }

                client.get("/api/stats") { header(ACCESS_JWT_HEADER, userToken) }.status shouldBe
                    HttpStatusCode.ServiceUnavailable
            }
        }

        "get all guests statistics should still be served next to the guest profile" {
            testApplication {
                application {
                    configureApiRoutes(
                        guestRepository,
                        GuestProfileService(guestRepository),
                        service,
                        AppUserService(guestRepository),
                        testCloudflareAccess,
                    )
                }

                client.get("/api/stats/guests") { header(ACCESS_JWT_HEADER, userToken) }.status shouldBe
                    HttpStatusCode.OK
            }
        }
    })
