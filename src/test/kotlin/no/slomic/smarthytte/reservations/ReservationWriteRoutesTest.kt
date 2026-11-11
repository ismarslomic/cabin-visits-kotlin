package no.slomic.smarthytte.reservations

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.ApiDependencies
import no.slomic.smarthytte.auth.ACCESS_JWT_HEADER
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.CloudflareAccessConfig
import no.slomic.smarthytte.auth.accessToken
import no.slomic.smarthytte.auth.testCloudflareAccess
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestAvatarService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestQueryService
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.configureTestApiRoutes
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import java.sql.SQLException

private const val PATH = "/api/reservations"
private const val ADMIN = "a@example.com"
private fun body(start: String, end: String, guests: String = "\"a\"", version: Int? = null): String {
    val refs = guests.split(",").joinToString(",") { """{"guestId":$it}""" }
    val versionPart = version?.let { """"version":$it,""" } ?: ""
    return """{$versionPart"startTime":"$start","endTime":"$end","guests":[$refs]}"""
}

private val newBody = body("2026-07-10T16:00:00Z", "2026-07-12T13:00:00Z")

class ReservationWriteRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guests = SqliteGuestRepository()
        val reservations = SqliteReservationRepository()

        fun withApp(
            cloudflareAccess: CloudflareAccessConfig? = testCloudflareAccess,
            service: ReservationService =
                ReservationService(reservations, GuestQueryService(guests), GuestAvatarService(guests), fixedClock),
            block: suspend ApplicationTestBuilder.() -> Unit,
        ) = testApplication {
            application {
                configureTestApiRoutes(
                    ApiDependencies(
                        guestQueryService = GuestQueryService(guests),
                        guestAvatarService = GuestAvatarService(guests),
                        guestProfileService = GuestProfileService(guests),
                        guestAdminService = GuestAdminService(guests),
                        statsService = mockk<StatsService>(),
                        reservationService = service,
                        appUserService = AppUserService(GuestQueryService(guests)),
                        cloudflareAccess = cloudflareAccess,
                    ),
                )
            }
            block()
        }

        suspend fun ApplicationTestBuilder.send(
            method: String,
            path: String,
            email: String? = ADMIN,
            payload: String? = null,
            contentType: String = ContentType.Application.Json.toString(),
        ): HttpResponse {
            val configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
                email?.let { header(ACCESS_JWT_HEADER, accessToken(it)) }
                if (payload != null) {
                    header(HttpHeaders.ContentType, contentType)
                    setBody(payload)
                }
            }
            return when (method) {
                "post" -> client.post(path, configure)
                "put" -> client.put(path, configure)
                "delete" -> client.delete(path, configure)
                else -> client.get(path, configure)
            }
        }

        fun HttpResponse.code() = kotlinx.coroutines.runBlocking {
            Json.parseToJsonElement(bodyAsText()).jsonObject.getValue("code").jsonPrimitive.content
        }

        fun String.json(): JsonObject = Json.parseToJsonElement(this).jsonObject

        suspend fun givenFamilyUser() = guests.addOrUpdate(testGuest("family", role = AppRole.FAMILY_USER))

        "post should create the reservation: 201, Location, a generated id and the guests, for admin and family user" {
            givenReservations(guests, reservations)
            givenFamilyUser()

            withApp {
                val admin = send("post", PATH, payload = newBody)
                admin.status shouldBe HttpStatusCode.Created
                val created = admin.bodyAsText().json()
                val id = created.getValue("id").jsonPrimitive.content
                id shouldMatch Regex("[0-9a-f]{32}")
                admin.headers[HttpHeaders.Location] shouldBe "$PATH/$id"
                created.getValue("version").jsonPrimitive.content shouldBe "1"
                created.getValue("createdBy").jsonPrimitive.content shouldBe "a"
                send("get", "$PATH/$id").status shouldBe HttpStatusCode.OK

                val family = send(
                    "post",
                    PATH,
                    "family@example.com",
                    body("2026-08-10T16:00:00Z", "2026-08-12T13:00:00Z"),
                )
                family.status shouldBe HttpStatusCode.Created
            }
        }

        "post should respond 400 with a code for each broken rule, and for a malformed body" {
            givenReservations(guests, reservations)

            withApp {
                mapOf(
                    body("2026-07-10T16:00:00Z", "2026-07-10T16:00:00Z") to "END_BEFORE_START",
                    body("2026-07-01T10:00:00Z", "2026-09-01T10:00:00Z") to "TOO_LONG",
                    body("2029-07-10T16:00:00Z", "2029-07-12T13:00:00Z") to "TOO_FAR_AHEAD",
                    body("2026-07-10T16:00:00Z", "2026-07-12T13:00:00Z", "\"a\",\"a\"") to "DUPLICATE_GUEST",
                    body("2026-07-10T16:00:00Z", "2026-07-12T13:00:00Z", "\"a\",\"nobody\"") to "UNKNOWN_GUEST",
                    """{"startTime":"2026-07-10T16:00:00Z","endTime":"2026-07-12T13:00:00Z","guests":[]}""" to
                        "NO_GUESTS",
                    """{"startTime":"not a time"}""" to "INVALID_REQUEST",
                ).forEach { (payload, code) ->
                    withClue(code) {
                        val response = send("post", PATH, payload = payload)

                        response.status shouldBe HttpStatusCode.BadRequest
                        response.code() shouldBe code
                    }
                }
            }
        }

        "post should respond 409 OVERLAP, and the same body twice is a conflict the second time" {
            givenReservations(guests, reservations)

            withApp {
                val overlap = send("post", PATH, payload = body("2026-04-01T10:00:00Z", "2026-04-03T10:00:00Z"))
                overlap.status shouldBe HttpStatusCode.Conflict
                overlap.code() shouldBe "OVERLAP"

                send("post", PATH, payload = newBody).status shouldBe HttpStatusCode.Created
                send("post", PATH, payload = newBody).status shouldBe HttpStatusCode.Conflict
            }
        }

        "write routes should respond 415 for another content type and 413 for a body above 64 KB" {
            givenReservations(guests, reservations)
            val tooLarge = newBody + " ".repeat(70_000)

            withApp {
                listOf("post" to PATH, "put" to "$PATH/r1").forEach { (method, path) ->
                    withClue("$method $path") {
                        listOf("text/plain", "multipart/form-data; boundary=x").forEach {
                            send(method, path, payload = newBody, contentType = it).status shouldBe
                                HttpStatusCode.UnsupportedMediaType
                        }
                        send(method, path, payload = tooLarge).status shouldBe HttpStatusCode.PayloadTooLarge
                    }
                }
            }
        }

        "put should replace the times and guests, bump the version and answer 200 with the reservation" {
            givenReservations(guests, reservations)

            withApp {
                val response = send(
                    "put",
                    "$PATH/r3a",
                    payload = body("2026-06-05T10:00:00Z", "2026-06-07T10:00:00Z", "\"c\",\"a\"", version = 1),
                )

                response.status shouldBe HttpStatusCode.OK
                val updated = response.bodyAsText().json()
                updated.getValue("version").jsonPrimitive.content shouldBe "2"
                updated.getValue("updatedBy").jsonPrimitive.content shouldBe "a"
                updated.getValue("startTime").jsonPrimitive.content shouldBe "2026-06-05T10:00:00Z"
            }
        }

        "put with unchanged data should answer 200 with the same version" {
            givenReservations(guests, reservations)

            withApp {
                val response = send(
                    "put",
                    "$PATH/r2",
                    payload = body("2026-04-10T16:00:00Z", "2026-04-12T13:00:00Z", "\"b\"", version = 1),
                )

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText().json().getValue("version").jsonPrimitive.content shouldBe "1"
            }
        }

        "put should respond 404, 412, 409 and 400 for an unknown id, a stale version, an overlap and a broken rule" {
            givenReservations(guests, reservations)

            withApp {
                send("put", "$PATH/nope", payload = body("2026-07-10T16:00:00Z", "2026-07-12T13:00:00Z", version = 1))
                    .status shouldBe HttpStatusCode.NotFound

                val stale = send(
                    "put",
                    "$PATH/r3a",
                    payload = body("2026-07-10T16:00:00Z", "2026-07-12T13:00:00Z", version = 7),
                )
                stale.status shouldBe HttpStatusCode.PreconditionFailed
                stale.code() shouldBe "VERSION_MISMATCH"

                val overlap = send(
                    "put",
                    "$PATH/r3a",
                    payload = body("2026-04-01T10:00:00Z", "2026-04-03T10:00:00Z", version = 1),
                )
                overlap.status shouldBe HttpStatusCode.Conflict
                overlap.code() shouldBe "OVERLAP"

                val noGuests = send(
                    "put",
                    "$PATH/r3a",
                    payload = """{"version":1,"startTime":"2026-07-10T16:00:00Z","endTime":"2026-07-12T13:00:00Z",""" +
                        """"guests":[]}""",
                )
                noGuests.status shouldBe HttpStatusCode.BadRequest
                noGuests.code() shouldBe "NO_GUESTS"

                // The version is required
                send("put", "$PATH/r3a", payload = newBody).status shouldBe HttpStatusCode.BadRequest
            }
        }

        "delete should answer 204, and then the reservation is gone" {
            givenReservations(guests, reservations)

            withApp {
                send("delete", "$PATH/r1?version=1").status shouldBe HttpStatusCode.NoContent
                send("get", "$PATH/r1").status shouldBe HttpStatusCode.NotFound
            }
        }

        "delete should respond 400 without a usable version, 404 for an unknown id and 412 for a stale version" {
            givenReservations(guests, reservations)

            withApp {
                listOf("", "?version=", "?version=abc", "?version=99999").forEach {
                    withClue(it) {
                        val response = send("delete", "$PATH/r1$it")

                        response.status shouldBe HttpStatusCode.BadRequest
                        response.code() shouldBe "INVALID_REQUEST"
                    }
                }
                send("delete", "$PATH/nope?version=1").status shouldBe HttpStatusCode.NotFound
                val stale = send("delete", "$PATH/r1?version=5")
                stale.status shouldBe HttpStatusCode.PreconditionFailed
                stale.code() shouldBe "VERSION_MISMATCH"
                send("get", "$PATH/r1").status shouldBe HttpStatusCode.OK
            }
        }

        "write routes should respond 401 without a token, and 403 for a guest user and for a person without a role" {
            givenReservations(guests, reservations)
            guests.addOrUpdate(testGuest("external"))

            withApp {
                listOf(
                    Triple("post", PATH, newBody),
                    Triple("put", "$PATH/r1", body("2026-07-10T16:00:00Z", "2026-07-12T13:00:00Z", version = 1)),
                    Triple("delete", "$PATH/r1?version=1", null),
                ).forEach { (method, path, payload) ->
                    withClue("$method $path") {
                        send(method, path, email = null, payload = payload).status shouldBe
                            HttpStatusCode.Unauthorized
                        send(method, path, "b@example.com", payload).status shouldBe HttpStatusCode.Forbidden
                        send(method, path, "external@example.com", payload).status shouldBe HttpStatusCode.Forbidden
                    }
                }
                // Nothing was changed
                reservations.allReservations().size shouldBe 5
            }
        }

        "write routes should respond 503 when Cloudflare Access is not configured" {
            givenReservations(guests, reservations)

            withApp(cloudflareAccess = null) {
                listOf(
                    Triple("post", PATH, newBody),
                    Triple("put", "$PATH/r1", body("2026-07-10T16:00:00Z", "2026-07-12T13:00:00Z", version = 1)),
                    Triple("delete", "$PATH/r1?version=1", null),
                ).forEach { (method, path, payload) ->
                    withClue("$method $path") {
                        send(method, path, payload = payload).status shouldBe HttpStatusCode.ServiceUnavailable
                    }
                }
            }
        }

        "write routes should respond 503 TEMPORARILY_UNAVAILABLE when the calendar queue is full" {
            givenReservations(guests, reservations)
            val full =
                ReservationService(reservations, GuestQueryService(guests), GuestAvatarService(guests), fixedClock, {
                    200L
                })

            withApp(service = full) {
                val response = send("post", PATH, payload = newBody)

                response.status shouldBe HttpStatusCode.ServiceUnavailable
                response.code() shouldBe "TEMPORARILY_UNAVAILABLE"
                send("delete", "$PATH/r1?version=1").status shouldBe HttpStatusCode.ServiceUnavailable
            }
        }

        "write routes should respond 503 when the database stays busy" {
            givenReservations(guests, reservations)
            val busy = mockk<ReservationRepository>()
            val busyError = SQLException("wrapper", SQLiteException("busy", SQLiteErrorCode.SQLITE_BUSY))
            coEvery { busy.createReservation(any(), any(), any(), any()) } throws busyError
            coEvery { busy.reservationById("r1") } returns reservations.reservationById("r1")
            coEvery { busy.updateReservation(any(), any(), any(), any(), any(), any()) } throws busyError
            coEvery { busy.deleteReservation(any(), any(), any()) } throws busyError

            withApp(
                service = ReservationService(busy, GuestQueryService(guests), GuestAvatarService(guests), fixedClock),
            ) {
                listOf(
                    Triple("post", PATH, newBody),
                    Triple("put", "$PATH/r1", body("2026-03-30T16:00:00Z", "2026-04-03T13:00:00Z", version = 1)),
                    Triple("delete", "$PATH/r1?version=1", null),
                ).forEach { (method, path, payload) ->
                    withClue("$method $path") {
                        val response = send(method, path, payload = payload)

                        response.status shouldBe HttpStatusCode.ServiceUnavailable
                        response.code() shouldBe "TEMPORARILY_UNAVAILABLE"
                    }
                }
            }
        }
    })
