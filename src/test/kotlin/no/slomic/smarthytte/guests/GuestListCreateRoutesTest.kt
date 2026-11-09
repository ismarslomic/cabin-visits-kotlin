package no.slomic.smarthytte.guests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.ApiDependencies
import no.slomic.smarthytte.auth.ACCESS_JWT_HEADER
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.accessToken
import no.slomic.smarthytte.auth.testCloudflareAccess
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.configureTestApiRoutes

private const val CREATE_BODY =
    """{"firstName":"Ola","lastName":"Nordmann","birthYear":1980,"gender":"MALE","email":"Ola@Example.com"}"""

class GuestListCreateRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository = SqliteGuestRepository()

        suspend fun givenUsers() {
            repository.addOrUpdate(testGuest("admin", role = AppRole.ADMIN))
            repository.addOrUpdate(testGuest("family", role = AppRole.FAMILY_USER))
            repository.addOrUpdate(testGuest("visitor", role = AppRole.GUEST_USER))
        }

        fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
            application {
                configureTestApiRoutes(
                    ApiDependencies(
                        guestRepository = repository,
                        guestProfileService = GuestProfileService(repository),
                        guestAdminService = GuestAdminService(repository),
                        statsService = mockk<StatsService>(),
                        appUserService = AppUserService(repository),
                        cloudflareAccess = testCloudflareAccess,
                    ),
                )
            }
            block()
        }

        suspend fun ApplicationTestBuilder.post(token: String?, body: String?, type: ContentType?): HttpResponse =
            client.post("/api/guests") {
                token?.let { header(ACCESS_JWT_HEADER, it) }
                type?.let { header(HttpHeaders.ContentType, it.toString()) }
                body?.let { setBody(it) }
            }

        fun HttpResponse.jsonBody(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

        "list guests should respond with every guest, version and audit fields for admin and family user" {
            givenUsers()
            repository.saveAvatar("visitor", byteArrayOf(1, 2, 3), "admin")
            val avatarTime = repository.avatarById("visitor")!!.versionTime.toEpochMilliseconds()

            withApp {
                listOf("admin@example.com", "family@example.com").forEach { email ->
                    withClue(email) {
                        val response = client.get("/api/guests") { header(ACCESS_JWT_HEADER, accessToken(email)) }

                        response.status shouldBe HttpStatusCode.OK
                        val guests = Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject }
                        guests.map { it["id"]?.jsonPrimitive?.content } shouldBe listOf("admin", "family", "visitor")
                        val visitor = guests.single { it["id"]?.jsonPrimitive?.content == "visitor" }
                        visitor["email"]?.jsonPrimitive?.content shouldBe "visitor@example.com"
                        visitor["role"]?.jsonPrimitive?.content shouldBe "GUEST_USER"
                        visitor["version"]?.jsonPrimitive?.content shouldBe "1"
                        visitor["createdBy"]?.jsonPrimitive?.content shouldBe "system"
                        visitor["avatarUrl"]?.jsonPrimitive?.content shouldBe
                            "/api/guests/visitor/avatar?v=$avatarTime"
                        guests.first()["avatarUrl"]?.jsonPrimitive?.contentOrNull shouldBe null
                        visitor.containsKey("updatedTime") shouldBe true
                    }
                }
            }
        }

        "list guests should respond 403 for a guest user and 401 without token" {
            givenUsers()

            withApp {
                client.get("/api/guests") {
                    header(ACCESS_JWT_HEADER, accessToken("visitor@example.com"))
                }.status shouldBe
                    HttpStatusCode.Forbidden
                client.get("/api/guests").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "create guest should respond 201 with the generated id, version and the actor" {
            givenUsers()

            withApp {
                val response = post(accessToken("admin@example.com"), CREATE_BODY, ContentType.Application.Json)

                response.status shouldBe HttpStatusCode.Created
                val body = response.jsonBody(response.bodyAsText())
                body["id"]?.jsonPrimitive?.content shouldBe "ola.nordmann"
                body["email"]?.jsonPrimitive?.content shouldBe "ola@example.com"
                body["isFamily"]?.jsonPrimitive?.content shouldBe "false"
                body["version"]?.jsonPrimitive?.content shouldBe "1"
                body["createdBy"]?.jsonPrimitive?.content shouldBe "admin"
                repository.guestById("ola.nordmann")!!.createdBy shouldBe "admin"
            }
        }

        "create guest should respond 409 with a code when the email is used" {
            givenUsers()

            withApp {
                val body = CREATE_BODY.replace("Ola@Example.com", "ADMIN@example.com")

                val response = post(accessToken("admin@example.com"), body, ContentType.Application.Json)

                response.status shouldBe HttpStatusCode.Conflict
                response.jsonBody(response.bodyAsText())["code"]?.jsonPrimitive?.content shouldBe "EMAIL_IN_USE"
            }
        }

        "create guest should respond 400 with a code for a missing or invalid property or a body that is not json" {
            givenUsers()

            withApp {
                val token = accessToken("admin@example.com")
                val bodies = listOf(
                    """{"lastName":"Nordmann","birthYear":1980,"gender":"MALE"}""" to "INVALID_REQUEST",
                    "not json" to "INVALID_REQUEST",
                    CREATE_BODY.replace("1980", "1800") to "INVALID_GUEST",
                    CREATE_BODY.replace("\"Ola\"", "\"\"") to "INVALID_GUEST",
                )

                bodies.forEach { (body, code) ->
                    withClue(body) {
                        val response = post(token, body, ContentType.Application.Json)

                        response.status shouldBe HttpStatusCode.BadRequest
                        response.jsonBody(response.bodyAsText())["code"]?.jsonPrimitive?.content shouldBe code
                    }
                }
                repository.allGuests().size shouldBe 3
            }
        }

        "create guest should respond 415 and 413 as json with a code" {
            givenUsers()

            withApp {
                val token = accessToken("admin@example.com")

                val wrongType = post(token, CREATE_BODY, ContentType.Text.Plain)
                wrongType.status shouldBe HttpStatusCode.UnsupportedMediaType
                wrongType.jsonBody(wrongType.bodyAsText())["code"]?.jsonPrimitive?.content shouldBe
                    "UNSUPPORTED_MEDIA_TYPE"
                post(token, CREATE_BODY, ContentType.MultiPart.FormData).status shouldBe
                    HttpStatusCode.UnsupportedMediaType
                post(token, null, null).status shouldBe HttpStatusCode.UnsupportedMediaType

                val large =
                    post(token, CREATE_BODY.replace("Ola\"", "${"x".repeat(70_000)}\""), ContentType.Application.Json)
                large.status shouldBe HttpStatusCode.PayloadTooLarge
                large.jsonBody(large.bodyAsText())["code"]?.jsonPrimitive?.content shouldBe "BODY_TOO_LARGE"
            }
        }

        "create guest should respond 403 for every role but admin, and 401 without token" {
            givenUsers()

            withApp {
                listOf("family@example.com", "visitor@example.com").forEach {
                    post(accessToken(it), CREATE_BODY, ContentType.Application.Json).status shouldBe
                        HttpStatusCode.Forbidden
                }
                post(null, CREATE_BODY, ContentType.Application.Json).status shouldBe HttpStatusCode.Unauthorized
                repository.allGuests().size shouldBe 3
            }
        }
    })
