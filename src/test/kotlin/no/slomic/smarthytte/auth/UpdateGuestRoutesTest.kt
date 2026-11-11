package no.slomic.smarthytte.auth

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.options
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.ApiDependencies
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestAvatarService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestQueryService
import no.slomic.smarthytte.guests.GuestUpdateResult
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.reservations.ReservationService
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.configureTestApiRoutes

class UpdateGuestRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guestRepository = SqliteGuestRepository()

        suspend fun givenUser(id: String, role: AppRole?) {
            guestRepository.addOrUpdate(testGuest(id, firstName = "Test", role = role))
        }

        fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
            application {
                configureTestApiRoutes(
                    ApiDependencies(
                        guestQueryService = GuestQueryService(guestRepository),
                        guestAvatarService = GuestAvatarService(guestRepository),
                        guestProfileService = GuestProfileService(guestRepository),
                        guestAdminService = GuestAdminService(guestRepository),
                        statsService = mockk<StatsService>(),
                        reservationService = mockk<ReservationService>(),
                        appUserService = AppUserService(GuestQueryService(guestRepository)),
                        cloudflareAccess = testCloudflareAccess,
                    ),
                )
            }
            block()
        }

        suspend fun ApplicationTestBuilder.put(path: String, token: String?, body: String): HttpResponse =
            client.put(path) {
                token?.let { header(ACCESS_JWT_HEADER, it) }
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(body)
            }

        /** The full body of a guest as [givenUser] stores it (lastName is the id), with the given values replaced. */
        fun guestBody(
            version: Int = 1,
            id: String = "ola",
            firstName: String = "Test",
            email: String? = "$id@example.com",
            isFamily: Boolean = true,
            role: String? = "GUEST_USER",
            lastName: String = id,
        ) = """{"version":$version,"firstName":"$firstName","lastName":"$lastName","birthYear":1980,""" +
            """"gender":"MALE","email":${email?.let { "\"$it\"" }},"isFamily":$isFamily,""" +
            """"role":${role?.let { "\"$it\"" }}}"""

        val meBody =
            """{"version":1,"firstName":"Ola","lastName":"Hansen","birthYear":1990,"gender":"FEMALE"}"""

        "put me should change the own profile and respond with the updated user" {
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val response = put("/api/me", accessToken("ola@example.com"), meBody)

                response.status shouldBe HttpStatusCode.OK
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["name"]?.jsonPrimitive?.content shouldBe "Ola Hansen"
                body["firstName"]?.jsonPrimitive?.content shouldBe "Ola"
                body["lastName"]?.jsonPrimitive?.content shouldBe "Hansen"
                body["birthYear"]?.jsonPrimitive?.content shouldBe "1990"
                body["gender"]?.jsonPrimitive?.content shouldBe "FEMALE"
                body["role"]?.jsonPrimitive?.content shouldBe "GUEST_USER"
                guestRepository.guestById("ola")!!.let {
                    it.firstName shouldBe "Ola"
                    it.birthYear shouldBe 1990
                    it.gender shouldBe Gender.FEMALE
                }
            }
        }

        "put me should respond with the avatarUrl of the user" {
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val token = accessToken("ola@example.com")

                val withoutAvatar = put("/api/me", token, meBody)
                Json.parseToJsonElement(withoutAvatar.bodyAsText()).jsonObject["avatarUrl"] shouldBe JsonNull

                guestRepository.saveAvatar("ola", byteArrayOf(1), "ola")
                val withAvatar = put("/api/me", token, meBody.replace("\"version\":1", "\"version\":2"))
                withAvatar.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(withAvatar.bodyAsText()).jsonObject["avatarUrl"]?.jsonPrimitive?.content
                    ?.startsWith("/api/guests/ola/avatar?v=") shouldBe true
            }
        }

        "put me should ignore properties that cannot be changed through me, such as email, isFamily and role" {
            givenUser("ola", AppRole.FAMILY_USER)

            withApp {
                val body = meBody.replace(
                    "}",
                    ""","email":"new@example.com","isFamily":false,"role":"ADMIN","appRole":"ADMIN","unknown":1}""",
                )

                put("/api/me", accessToken("ola@example.com"), body).status shouldBe HttpStatusCode.OK
                guestRepository.guestById("ola")!!.let {
                    it.role shouldBe AppRole.FAMILY_USER
                    it.isFamily shouldBe true
                    it.email shouldBe "ola@example.com"
                }
            }
        }

        "put me should respond 400 when a required property is missing" {
            givenUser("ola", AppRole.ADMIN)

            withApp {
                val token = accessToken("ola@example.com")

                listOf("version", "firstName", "lastName", "birthYear", "gender").forEach { property ->
                    val body = Json.parseToJsonElement(meBody).jsonObject.filterKeys { it != property }
                    withClue(property) {
                        put("/api/me", token, JsonObject(body).toString()).status shouldBe HttpStatusCode.BadRequest
                    }
                }
                put("/api/me", token, "{}").status shouldBe HttpStatusCode.BadRequest
            }
        }

        "put me should respond 400 for a body that is not valid, and 401 without token" {
            givenUser("ola", AppRole.ADMIN)

            withApp {
                val token = accessToken("ola@example.com")

                put("/api/me", token, "not json").status shouldBe HttpStatusCode.BadRequest
                put("/api/me", token, meBody.replace("1990", "\"abc\"")).status shouldBe HttpStatusCode.BadRequest
                put("/api/me", token, meBody.replace("FEMALE", "OTHER")).status shouldBe HttpStatusCode.BadRequest
                put("/api/me", token, meBody.replace("Ola", "")).status shouldBe HttpStatusCode.BadRequest
                put("/api/me", token = null, meBody).status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "put guest should let an admin change all properties of another guest" {
            givenUser("kari", AppRole.ADMIN)
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val body =
                    guestBody(lastName = "Hansen", email = "new@example.com", role = "FAMILY_USER")

                val response = put("/api/guests/ola", accessToken("kari@example.com"), body)

                response.status shouldBe HttpStatusCode.OK
                guestRepository.guestById("ola")!!.let {
                    it.lastName shouldBe "Hansen"
                    it.email shouldBe "new@example.com"
                    it.isFamily shouldBe true
                    it.role shouldBe AppRole.FAMILY_USER
                }
            }
        }

        "put guest should respond 409 when the email is used by another user" {
            givenUser("kari", AppRole.ADMIN)
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val response =
                    put(
                        "/api/guests/ola",
                        accessToken("kari@example.com"),
                        guestBody(email = "kari@example.com"),
                    )

                response.status shouldBe HttpStatusCode.Conflict
                guestRepository.guestById("ola")!!.email shouldBe "ola@example.com"
            }
        }

        "put guest should respond 404 for an unknown guest" {
            givenUser("kari", AppRole.ADMIN)

            withApp {
                put("/api/guests/unknown", accessToken("kari@example.com"), guestBody()).status shouldBe
                    HttpStatusCode.NotFound
            }
        }

        "put guest should respond 403 for every role but admin, and 401 without token" {
            givenUser("ola", AppRole.GUEST_USER)
            givenUser("kari", AppRole.FAMILY_USER)

            withApp {
                val body = guestBody(id = "kari", firstName = "Hacked", role = "ADMIN")

                put("/api/guests/kari", accessToken("ola@example.com"), body).status shouldBe HttpStatusCode.Forbidden
                put("/api/guests/kari", accessToken("kari@example.com"), body).status shouldBe HttpStatusCode.Forbidden
                put("/api/guests/kari", token = null, body).status shouldBe HttpStatusCode.Unauthorized
                // A denied request must not reach the handler
                guestRepository.guestById("kari")!!.let {
                    it.firstName shouldBe "Test"
                    it.role shouldBe AppRole.FAMILY_USER
                }
            }
        }

        "put guest should respond 412 for a stale version, 400 for a missing one and keep the version for no change" {
            givenUser("kari", AppRole.ADMIN)
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val token = accessToken("kari@example.com")

                val updated = put("/api/guests/ola", token, guestBody(firstName = "New"))
                updated.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(updated.bodyAsText()).jsonObject["version"]?.jsonPrimitive?.content shouldBe "2"

                put("/api/guests/ola", token, guestBody(firstName = "Stale")).status shouldBe
                    HttpStatusCode.PreconditionFailed
                put(
                    "/api/guests/ola",
                    token,
                    guestBody(firstName = "NoVersion").replace("\"version\":1,", ""),
                ).status shouldBe
                    HttpStatusCode.BadRequest
                put("/api/guests/ola", token, """{"version":"abc"}""").status shouldBe HttpStatusCode.BadRequest

                val unchanged = put("/api/guests/ola", token, guestBody(version = 2, firstName = "New"))
                unchanged.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(unchanged.bodyAsText()).jsonObject["version"]?.jsonPrimitive?.content shouldBe
                    "2"
                guestRepository.guestById("ola")!!.firstName shouldBe "New"
            }
        }

        "put me should respond 412 for a stale version" {
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val token = accessToken("ola@example.com")

                put("/api/me", token, meBody).status shouldBe HttpStatusCode.OK
                put("/api/me", token, meBody).status shouldBe HttpStatusCode.PreconditionFailed
                put("/api/me", token, meBody.replace("\"version\":1", "\"version\":2")).status shouldBe
                    HttpStatusCode.OK
            }
        }

        "update routes should answer errors as json with a code and a message" {
            givenUser("kari", AppRole.ADMIN)
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val admin = accessToken("kari@example.com")
                val user = accessToken("ola@example.com")

                suspend fun HttpResponse.shouldBeError(status: HttpStatusCode, code: String) {
                    this.status shouldBe status
                    val body = Json.parseToJsonElement(bodyAsText()).jsonObject
                    body["code"]?.jsonPrimitive?.content shouldBe code
                    body["message"]?.jsonPrimitive?.content?.isNotBlank() shouldBe true
                }

                put("/api/guests/ola", admin, "not json").shouldBeError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
                put("/api/guests/ola", admin, guestBody(firstName = "")).shouldBeError(
                    HttpStatusCode.BadRequest,
                    "INVALID_GUEST",
                )
                put("/api/guests/unknown", admin, guestBody()).shouldBeError(HttpStatusCode.NotFound, "NOT_FOUND")
                put("/api/guests/ola", admin, guestBody(email = "kari@example.com")).shouldBeError(
                    HttpStatusCode.Conflict,
                    "EMAIL_IN_USE",
                )
                put("/api/guests/kari", admin, guestBody(id = "kari", role = "GUEST_USER")).shouldBeError(
                    HttpStatusCode.Forbidden,
                    "FORBIDDEN",
                )
                put("/api/guests/ola", admin, guestBody(version = 9)).shouldBeError(
                    HttpStatusCode.PreconditionFailed,
                    "VERSION_MISMATCH",
                )

                put("/api/me", user, "not json").shouldBeError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
                put(
                    "/api/me",
                    user,
                    meBody.replace("Ola", ""),
                ).shouldBeError(HttpStatusCode.BadRequest, "INVALID_GUEST")
                put("/api/me", user, meBody.replace("\"version\":1", "\"version\":9")).shouldBeError(
                    HttpStatusCode.PreconditionFailed,
                    "VERSION_MISMATCH",
                )
                client.put("/api/me") {
                    header(ACCESS_JWT_HEADER, user)
                    header(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                    setBody(meBody)
                }.shouldBeError(HttpStatusCode.UnsupportedMediaType, "UNSUPPORTED_MEDIA_TYPE")
                put("/api/me", user, """{"firstName":"${"x".repeat(70_000)}"}""").shouldBeError(
                    HttpStatusCode.PayloadTooLarge,
                    "BODY_TOO_LARGE",
                )
            }
        }

        "put me should respond 404 when the guest is gone before the update" {
            givenUser("ola", AppRole.GUEST_USER)
            val service = mockk<GuestProfileService>()
            coEvery { service.updateOwnProfile(any(), any()) } returns GuestUpdateResult.NotFound

            testApplication {
                application {
                    configureTestApiRoutes(
                        ApiDependencies(
                            guestQueryService = GuestQueryService(guestRepository),
                            guestAvatarService = GuestAvatarService(guestRepository),
                            guestProfileService = service,
                            guestAdminService = GuestAdminService(guestRepository),
                            statsService = mockk<StatsService>(),
                            reservationService = mockk<ReservationService>(),
                            appUserService = AppUserService(GuestQueryService(guestRepository)),
                            cloudflareAccess = testCloudflareAccess,
                        ),
                    )
                }

                val response = put("/api/me", accessToken("ola@example.com"), meBody)

                response.status shouldBe HttpStatusCode.NotFound
                Json.parseToJsonElement(response.bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content shouldBe
                    "NOT_FOUND"
            }
        }

        "write routes should respond 415 for a content type that is not json, and 413 for a large body" {
            givenUser("kari", AppRole.ADMIN)

            withApp {
                val token = accessToken("kari@example.com")

                listOf("/api/me", "/api/guests/kari").forEach { path ->
                    listOf(ContentType.Text.Plain, ContentType.MultiPart.FormData).forEach { type ->
                        withClue("$path $type") {
                            client.put(path) {
                                header(ACCESS_JWT_HEADER, token)
                                header(HttpHeaders.ContentType, type.toString())
                                setBody(meBody)
                            }.status shouldBe HttpStatusCode.UnsupportedMediaType
                        }
                    }
                    withClue(path) {
                        client.put(path) { header(ACCESS_JWT_HEADER, token) }.status shouldBe
                            HttpStatusCode.UnsupportedMediaType
                        put(path, token, """{"version":1,"firstName":"${"x".repeat(70_000)}"}""").status shouldBe
                            HttpStatusCode.PayloadTooLarge
                    }
                }
            }
        }

        "a preflight request should not be answered with cors headers" {
            withApp {
                val response = client.options("/api/guests/kari") {
                    header(HttpHeaders.Origin, "https://evil.example.com")
                    header(HttpHeaders.AccessControlRequestMethod, "POST")
                }

                response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBe null
                response.headers[HttpHeaders.AccessControlAllowMethods] shouldBe null
            }
        }

        "put guest should respond 400 when a writable property is missing, also email and role" {
            givenUser("kari", AppRole.ADMIN)
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val token = accessToken("kari@example.com")
                val full = Json.parseToJsonElement(guestBody()).jsonObject

                full.keys.forEach { property ->
                    withClue(property) {
                        val body = JsonObject(full.filterKeys { it != property }).toString()

                        put("/api/guests/ola", token, body).status shouldBe HttpStatusCode.BadRequest
                    }
                }
                put("/api/guests/ola", token, "{}").status shouldBe HttpStatusCode.BadRequest
            }
        }

        "put guest should clear email and role with null, and ignore properties that are not writable" {
            givenUser("kari", AppRole.ADMIN)
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val body = guestBody(email = null, role = null).dropLast(1) +
                    ""","id":"other","avatarUrl":"x","createdBy":"x","unknown":1}"""

                val response = put("/api/guests/ola", accessToken("kari@example.com"), body)

                response.status shouldBe HttpStatusCode.OK
                guestRepository.guestById("ola")!!.let {
                    it.email shouldBe null
                    it.role shouldBe null
                    it.version shouldBe 2
                }
                guestRepository.guestById("other") shouldBe null
            }
        }
    })
