package no.slomic.smarthytte.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

class UpdateGuestRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guestRepository = SqliteGuestRepository()

        suspend fun givenUser(id: String, role: AppRole?) {
            guestRepository.addOrUpdate(testGuest(id, firstName = "Test", appRole = role))
        }

        fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
            application {
                configureApiRoutes(
                    guestRepository,
                    GuestProfileService(guestRepository),
                    mockk<StatsService>(),
                    AppUserService(guestRepository),
                    testCloudflareAccess,
                )
            }
            block()
        }

        suspend fun ApplicationTestBuilder.post(path: String, token: String?, body: String): HttpResponse =
            client.post(path) {
                token?.let { header(ACCESS_JWT_HEADER, it) }
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(body)
            }

        "post me should change the own profile and respond with the updated user" {
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val response =
                    post("/api/me", accessToken("ola@example.com"), """{"firstName":"Ola","birthYear":1990}""")

                response.status shouldBe HttpStatusCode.OK
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["name"]?.jsonPrimitive?.content shouldBe "Ola ola"
                body["role"]?.jsonPrimitive?.content shouldBe "GUEST_USER"
                guestRepository.guestById("ola")!!.birthYear shouldBe 1990
            }
        }

        "post me should respond 403 when a non admin changes email, isFamily or appRole" {
            givenUser("ola", AppRole.FAMILY_USER)

            withApp {
                val token = accessToken("ola@example.com")

                post("/api/me", token, """{"appRole":"ADMIN"}""").status shouldBe HttpStatusCode.Forbidden
                post("/api/me", token, """{"email":"new@example.com"}""").status shouldBe HttpStatusCode.Forbidden
                post("/api/me", token, """{"isFamily":false}""").status shouldBe HttpStatusCode.Forbidden
                guestRepository.guestById("ola")!!.appRole shouldBe AppRole.FAMILY_USER
            }
        }

        "post me should let an admin change isFamily, but not their own email" {
            givenUser("ola", AppRole.ADMIN)

            withApp {
                val token = accessToken("ola@example.com")

                post("/api/me", token, """{"isFamily":false}""").status shouldBe HttpStatusCode.OK
                post("/api/me", token, """{"email":"new@example.com"}""").status shouldBe HttpStatusCode.Forbidden
                guestRepository.guestById("ola")!!.let {
                    it.email shouldBe "ola@example.com"
                    it.isFamily shouldBe false
                }
            }
        }

        "post me should respond 400 for a body that is not valid, and 401 without token" {
            givenUser("ola", AppRole.ADMIN)

            withApp {
                val token = accessToken("ola@example.com")

                post("/api/me", token, "not json").status shouldBe HttpStatusCode.BadRequest
                post("/api/me", token, """{"birthYear":"abc"}""").status shouldBe HttpStatusCode.BadRequest
                post("/api/me", token, """{"gender":"OTHER"}""").status shouldBe HttpStatusCode.BadRequest
                post("/api/me", token, """{"firstName":""}""").status shouldBe HttpStatusCode.BadRequest
                post("/api/me", token = null, "{}").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "post guest should let an admin change all properties of another guest" {
            givenUser("kari", AppRole.ADMIN)
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val body = """{"lastName":"Hansen","email":"new@example.com","isFamily":true,"appRole":"FAMILY_USER"}"""

                val response = post("/api/guests/ola", accessToken("kari@example.com"), body)

                response.status shouldBe HttpStatusCode.OK
                guestRepository.guestById("ola")!!.let {
                    it.lastName shouldBe "Hansen"
                    it.email shouldBe "new@example.com"
                    it.isFamily shouldBe true
                    it.appRole shouldBe AppRole.FAMILY_USER
                }
            }
        }

        "post guest should respond 409 when the email is used by another user" {
            givenUser("kari", AppRole.ADMIN)
            givenUser("ola", AppRole.GUEST_USER)

            withApp {
                val response =
                    post("/api/guests/ola", accessToken("kari@example.com"), """{"email":"kari@example.com"}""")

                response.status shouldBe HttpStatusCode.Conflict
                guestRepository.guestById("ola")!!.email shouldBe "ola@example.com"
            }
        }

        "post guest should respond 404 for an unknown guest" {
            givenUser("kari", AppRole.ADMIN)

            withApp {
                post("/api/guests/unknown", accessToken("kari@example.com"), "{}").status shouldBe
                    HttpStatusCode.NotFound
            }
        }

        "post guest should respond 403 for every role but admin, and 401 without token" {
            givenUser("ola", AppRole.GUEST_USER)
            givenUser("kari", AppRole.FAMILY_USER)

            withApp {
                val body = """{"firstName":"Hacked","appRole":"ADMIN"}"""

                post("/api/guests/kari", accessToken("ola@example.com"), body).status shouldBe HttpStatusCode.Forbidden
                post("/api/guests/kari", accessToken("kari@example.com"), body).status shouldBe HttpStatusCode.Forbidden
                post("/api/guests/kari", token = null, body).status shouldBe HttpStatusCode.Unauthorized
                // A denied request must not reach the handler
                guestRepository.guestById("kari")!!.let {
                    it.firstName shouldBe "Test"
                    it.appRole shouldBe AppRole.FAMILY_USER
                }
            }
        }
    })
