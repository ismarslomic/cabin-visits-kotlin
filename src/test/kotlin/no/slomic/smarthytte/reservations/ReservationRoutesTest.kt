package no.slomic.smarthytte.reservations

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.ApiDependencies
import no.slomic.smarthytte.auth.ACCESS_JWT_HEADER
import no.slomic.smarthytte.auth.API_KEY_HEADER
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.CloudflareAccessConfig
import no.slomic.smarthytte.auth.accessToken
import no.slomic.smarthytte.auth.sha256Hex
import no.slomic.smarthytte.auth.testCloudflareAccess
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.auth.toConfig
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckInOutSource
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.properties.ApiKeyProperties
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.configureTestApiRoutes
import kotlin.time.Instant

private const val MIRROR_KEY = "test-mirror-key"

class ReservationRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guests = SqliteGuestRepository()
        val reservations = SqliteReservationRepository()

        fun withApp(
            cloudflareAccess: CloudflareAccessConfig? = testCloudflareAccess,
            block: suspend ApplicationTestBuilder.() -> Unit,
        ) = testApplication {
            application {
                configureTestApiRoutes(
                    ApiDependencies(
                        guestRepository = guests,
                        guestProfileService = GuestProfileService(guests),
                        guestAdminService = GuestAdminService(guests),
                        statsService = mockk<StatsService>(),
                        reservationService = ReservationService(reservations, guests, fixedClock),
                        appUserService = AppUserService(guests),
                        cloudflareAccess = cloudflareAccess,
                        apiKeys = ApiKeyProperties("mirror:${sha256Hex(MIRROR_KEY)}").toConfig(),
                    ),
                )
            }
            block()
        }

        suspend fun ApplicationTestBuilder.get(path: String, email: String? = null, apiKey: String? = null) =
            client.get(path) {
                email?.let { header(ACCESS_JWT_HEADER, accessToken(it)) }
                apiKey?.let { header(API_KEY_HEADER, it) }
            }

        fun ids(text: String) =
            Json.parseToJsonElement(text).jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }

        fun String.json(): JsonObject = Json.parseToJsonElement(this).jsonObject

        "list should respond with every reservation for admin, family user and the service user" {
            givenReservations(guests, reservations)
            guests.addOrUpdate(testGuest("family", role = no.slomic.smarthytte.guests.AppRole.FAMILY_USER))

            withApp {
                listOf(get("/api/reservations", "a@example.com"), get("/api/reservations", "family@example.com"))
                    .plus(get("/api/reservations", apiKey = MIRROR_KEY))
                    .forEach {
                        it.status shouldBe HttpStatusCode.OK
                        ids(it.bodyAsText()) shouldBe listOf("r1", "r2", "r0", "r3a", "r3b")
                    }
            }
        }

        "list should respond with the guests, status, check-in, version and audit fields" {
            givenReservations(guests, reservations)
            guests.saveAvatar("a", byteArrayOf(1, 2, 3), "a")
            val avatarTime = guests.avatarById("a")!!.versionTime.toEpochMilliseconds()
            val r1 = reservations.reservationById("r1")!!
            reservations.setCheckIn(
                CheckIn(Instant.parse("2026-03-30T17:00:00Z"), CheckInOutSource.CHECK_IN_SENSOR, "source-id"),
                "r1",
                r1.startTime,
                r1.endTime,
            )
            reservations.setCheckOut(
                CheckOut(Instant.parse("2026-04-02T12:00:00Z"), CheckInOutSource.VEHICLE_TRIP, "trip-id"),
                "r1",
                r1.startTime,
                r1.endTime,
            )

            withApp {
                val response = get("/api/reservations?to=2026-04-11", "a@example.com")

                val list = Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject }
                val first = list.first()
                first["status"]?.jsonPrimitive?.content shouldBe "COMPLETED"
                first["version"]?.jsonPrimitive?.content shouldBe "1"
                first["createdBy"]?.jsonPrimitive?.content shouldBe "system"
                first["updatedTime"]?.jsonPrimitive?.contentOrNull shouldBe null
                first.containsKey("updatedTime") shouldBe true
                first["checkIn"]?.jsonObject?.get("sourceName")?.jsonPrimitive?.content shouldBe "CHECK_IN_SENSOR"
                first["checkOut"]?.jsonObject?.get("sourceName")?.jsonPrimitive?.content shouldBe "VEHICLE_TRIP"
                // A reservation without check-out sends null, not a missing property
                list[1].containsKey("checkOut") shouldBe true
                list[1]["checkOut"]?.jsonPrimitive?.contentOrNull shouldBe null
                val guestList = first.getValue("guests").jsonArray.map { it.jsonObject }
                guestList.map { it["guestId"]?.jsonPrimitive?.content } shouldBe listOf("a", "b")
                guestList.first()["firstName"]?.jsonPrimitive?.content shouldBe "First"
                guestList.first()["avatarUrl"]?.jsonPrimitive?.content shouldBe "/api/guests/a/avatar?v=$avatarTime"
                guestList.last()["avatarUrl"]?.jsonPrimitive?.contentOrNull shouldBe null
                list.last()["status"]?.jsonPrimitive?.content shouldBe "ONGOING"
            }
        }

        "list should give a guest user only the reservations of their own guest, whole or part of the stay" {
            givenReservations(guests, reservations)

            withApp {
                val b = get("/api/reservations", "b@example.com")
                ids(b.bodyAsText()) shouldBe listOf("r1", "r2")
                val c = get("/api/reservations?from=2026-05-01", "c@example.com")
                ids(c.bodyAsText()) shouldBe listOf("r3a", "r3b")
                val outsider = get("/api/reservations", "outsider@example.com")
                outsider.status shouldBe HttpStatusCode.OK
                ids(outsider.bodyAsText()) shouldBe emptyList()
            }
        }

        "list should respond 400 for an invalid date and for to that is not after from" {
            givenReservations(guests, reservations)

            withApp {
                listOf(
                    "/api/reservations?from=2026-13-01",
                    "/api/reservations?to=yesterday",
                    "/api/reservations?from=2026-04-02&to=2026-04-02",
                    "/api/reservations?from=2026-04-03&to=2026-04-02",
                ).forEach {
                    withClue(it) {
                        val response = get(it, "a@example.com")

                        response.status shouldBe HttpStatusCode.BadRequest
                        response.bodyAsText().json()["code"]?.jsonPrimitive?.content shouldBe "INVALID_REQUEST"
                    }
                }
            }
        }

        "get should respond with the reservation, also for a guest user who is a guest of it" {
            givenReservations(guests, reservations)

            withApp {
                listOf("a@example.com", "b@example.com").forEach {
                    val response = get("/api/reservations/r1", it)

                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText().json()["id"]?.jsonPrimitive?.content shouldBe "r1"
                }
                get("/api/reservations/r1", apiKey = MIRROR_KEY).status shouldBe HttpStatusCode.OK
            }
        }

        "get should respond 404 for an unknown id and for a guest user who is not a guest of it" {
            givenReservations(guests, reservations)

            withApp {
                val unknown = get("/api/reservations/nope", "a@example.com")
                unknown.status shouldBe HttpStatusCode.NotFound
                unknown.bodyAsText().json()["code"]?.jsonPrimitive?.content shouldBe "NOT_FOUND"

                val other = get("/api/reservations/r3a", "b@example.com")
                other.status shouldBe HttpStatusCode.NotFound
                other.bodyAsText() shouldBe unknown.bodyAsText()
            }
        }

        "reservation routes should respond 401 without credentials and 403 for a person without a role" {
            guests.addOrUpdate(testGuest("external"))

            withApp {
                listOf("/api/reservations", "/api/reservations/r1").forEach {
                    withClue(it) {
                        get(it).status shouldBe HttpStatusCode.Unauthorized
                        get(it, "external@example.com").status shouldBe HttpStatusCode.Forbidden
                    }
                }
            }
        }

        "reservation routes should respond 503 when Cloudflare Access is not configured" {
            guests.addOrUpdate(testGuest("a", role = no.slomic.smarthytte.guests.AppRole.ADMIN))

            withApp(cloudflareAccess = null) {
                listOf("/api/reservations", "/api/reservations/r1").forEach {
                    withClue(it) { get(it, "a@example.com").status shouldBe HttpStatusCode.ServiceUnavailable }
                }
            }
        }
    })
