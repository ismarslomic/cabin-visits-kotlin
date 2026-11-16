package no.slomic.smarthytte.issues

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.ApiDependencies
import no.slomic.smarthytte.auth.ACCESS_JWT_HEADER
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.accessToken
import no.slomic.smarthytte.auth.testCloudflareAccess
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestAvatarService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestQueryService
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.reservations.ReservationService
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.configureTestApiRoutes

class IssueRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guestRepository = SqliteGuestRepository()
        val issueRepository = SqliteIssueRepository()

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
                        issueService = IssueService(issueRepository),
                    ),
                )
            }
            block()
        }

        suspend fun givenUser(id: String, role: AppRole?): String {
            guestRepository.addOrUpdate(testGuest(id, firstName = "Test", role = role))
            return accessToken("$id@example.com")
        }

        suspend fun report(code: IssueCode, key: String, message: String = "problem of $key", cause: String? = null) =
            issueRepository.record(code, key, message, cause, "system")

        suspend fun ApplicationTestBuilder.get(path: String, token: String?): HttpResponse = client.get(path) {
            token?.let { header(ACCESS_JWT_HEADER, it) }
        }

        suspend fun ApplicationTestBuilder.put(path: String, token: String?, body: String): HttpResponse =
            client.put(path) {
                token?.let { header(ACCESS_JWT_HEADER, it) }
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(body)
            }

        suspend fun HttpResponse.shouldBeError(status: HttpStatusCode, code: String) {
            this.status shouldBe status
            Json.parseToJsonElement(bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content shouldBe code
        }

        suspend fun HttpResponse.issues(): JsonArray = Json.parseToJsonElement(bodyAsText()).jsonArray

        fun JsonArray.keys() = map { it.jsonObject["key"]?.jsonPrimitive?.content }

        "list should return the issues with every property, the latest first" {
            val admin = givenUser("admin", AppRole.ADMIN)
            report(IssueCode.PLACE_MISSING, "PLACE_MISSING:1", "no city", "PlaceLookupException")
            report(IssueCode.JLR_AUTH_FAILED, "JLR_AUTH_FAILED")

            withApp {
                val response = get("/api/issues", admin)

                response.status shouldBe HttpStatusCode.OK
                val issues = response.issues()
                issues.keys() shouldBe listOf("JLR_AUTH_FAILED", "PLACE_MISSING:1")
                val place: JsonObject = issues[1].jsonObject
                place["code"]?.jsonPrimitive?.content shouldBe "PLACE_MISSING"
                place["severity"]?.jsonPrimitive?.content shouldBe "WARN"
                place["status"]?.jsonPrimitive?.content shouldBe "OPEN"
                place["message"]?.jsonPrimitive?.content shouldBe "no city"
                place["causeType"]?.jsonPrimitive?.content shouldBe "PlaceLookupException"
                place["occurrenceCount"]?.jsonPrimitive?.int shouldBe 1
                place["resolvedTime"] shouldBe JsonNull
                place["read"]?.jsonPrimitive?.boolean shouldBe false
                place["readTime"] shouldBe JsonNull
                place["version"]?.jsonPrimitive?.int shouldBe 1
                issues[0].jsonObject["severity"]?.jsonPrimitive?.content shouldBe "ERROR"
                place.keys shouldBe setOf(
                    "id", "code", "key", "severity", "status", "message", "causeType", "occurrenceCount",
                    "firstOccurredTime", "lastOccurredTime", "resolvedTime", "read", "readTime",
                    "version",
                )
            }
        }

        "list should filter on read, status and limit" {
            val admin = givenUser("admin", AppRole.ADMIN)
            report(IssueCode.PLACE_MISSING, "unread")
            report(IssueCode.PLACE_MISSING, "read")
            report(IssueCode.PLACE_MISSING, "resolved")
            issueRepository.resolve(IssueCode.PLACE_MISSING, "resolved", "system")
            val readId = checkNotNull(issueRepository.findByCodeAndKey(IssueCode.PLACE_MISSING, "read")).id
            issueRepository.setRead(readId, 1, true, "system")

            withApp {
                get("/api/issues?read=true", admin).issues().keys() shouldBe listOf("read")
                get("/api/issues?read=false&status=OPEN", admin).issues().keys() shouldBe listOf("unread")
                get("/api/issues?status=RESOLVED", admin).issues().keys() shouldBe listOf("resolved")
                get("/api/issues?limit=2", admin).issues().size shouldBe 2
                get("/api/issues?limit=500", admin).issues().size shouldBe 3
            }
        }

        "list should respond 400 for an invalid read, status or limit" {
            val admin = givenUser("admin", AppRole.ADMIN)

            withApp {
                listOf(
                    "read=yes",
                    "status=open",
                    "status=DONE",
                    "limit=0",
                    "limit=501",
                    "limit=-1",
                    "limit=many",
                ).forEach {
                    withClue(it) {
                        get("/api/issues?$it", admin).shouldBeError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
                    }
                }
            }
        }

        "list should return at most 100 issues by default" {
            val admin = givenUser("admin", AppRole.ADMIN)
            repeat(101) { report(IssueCode.PLACE_MISSING, "k$it") }

            withApp {
                get("/api/issues", admin).issues().size shouldBe 100
                get("/api/issues?limit=101", admin).issues().size shouldBe 101
            }
        }

        "unread count should count the issues that are open and not read" {
            val admin = givenUser("admin", AppRole.ADMIN)
            report(IssueCode.PLACE_MISSING, "unread")
            report(IssueCode.PLACE_MISSING, "read")
            report(IssueCode.PLACE_MISSING, "resolved")
            issueRepository.resolve(IssueCode.PLACE_MISSING, "resolved", "system")
            issueRepository.setRead(
                checkNotNull(issueRepository.findByCodeAndKey(IssueCode.PLACE_MISSING, "read")).id,
                1,
                true,
                "system",
            )

            withApp {
                val response = get("/api/issues/unread-count", admin)

                response.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(response.bodyAsText()).jsonObject["count"]?.jsonPrimitive?.int shouldBe 1
            }
        }

        "put should mark the issue read and unread, and respond with the issue" {
            val admin = givenUser("admin", AppRole.ADMIN)
            report(IssueCode.PLACE_MISSING, "k")
            val id = checkNotNull(issueRepository.findByCodeAndKey(IssueCode.PLACE_MISSING, "k")).id

            withApp {
                val read = put("/api/issues/$id", admin, """{"version":1,"read":true}""")
                read.status shouldBe HttpStatusCode.OK
                val readBody = Json.parseToJsonElement(read.bodyAsText()).jsonObject
                readBody["read"]?.jsonPrimitive?.boolean shouldBe true
                (readBody["readTime"] != JsonNull) shouldBe true
                readBody["version"]?.jsonPrimitive?.int shouldBe 2

                val unchanged = put("/api/issues/$id", admin, """{"version":2,"read":true}""")
                Json.parseToJsonElement(unchanged.bodyAsText()).jsonObject["version"]?.jsonPrimitive?.int shouldBe 2

                val unread = put("/api/issues/$id", admin, """{"version":2,"read":false}""")
                val unreadBody = Json.parseToJsonElement(unread.bodyAsText()).jsonObject
                unreadBody["read"]?.jsonPrimitive?.boolean shouldBe false
                unreadBody["readTime"] shouldBe JsonNull
                unreadBody["version"]?.jsonPrimitive?.int shouldBe 3
            }
            issueRepository.findById(id)?.readTime shouldBe null
        }

        "put should respond 404 for an unknown or non-numeric id, 412 for an outdated version" {
            val admin = givenUser("admin", AppRole.ADMIN)
            report(IssueCode.PLACE_MISSING, "k")
            val id = checkNotNull(issueRepository.findByCodeAndKey(IssueCode.PLACE_MISSING, "k")).id

            withApp {
                put("/api/issues/${id + 1}", admin, """{"version":1,"read":true}""")
                    .shouldBeError(HttpStatusCode.NotFound, "NOT_FOUND")
                put("/api/issues/abc", admin, """{"version":1,"read":true}""")
                    .shouldBeError(HttpStatusCode.NotFound, "NOT_FOUND")
                put("/api/issues/unread-count", admin, "not json").shouldBeError(HttpStatusCode.NotFound, "NOT_FOUND")
                put("/api/issues/$id", admin, """{"version":9,"read":true}""")
                    .shouldBeError(HttpStatusCode.PreconditionFailed, "VERSION_MISMATCH")
            }
        }

        "put should respond 400 for a body that is not valid or misses a property, 415 and 413" {
            val admin = givenUser("admin", AppRole.ADMIN)
            report(IssueCode.PLACE_MISSING, "k")
            val id = checkNotNull(issueRepository.findByCodeAndKey(IssueCode.PLACE_MISSING, "k")).id

            withApp {
                listOf(
                    "not json",
                    """{"read":true}""",
                    """{"version":1}""",
                    """{"version":1,"read":"yes"}""",
                    """{"version":40000,"read":true}""",
                )
                    .forEach {
                        withClue(it) {
                            put(
                                "/api/issues/$id",
                                admin,
                                it,
                            ).shouldBeError(HttpStatusCode.BadRequest, "INVALID_REQUEST")
                        }
                    }
                client.put("/api/issues/$id") { header(ACCESS_JWT_HEADER, admin) }.status shouldBe
                    HttpStatusCode.UnsupportedMediaType
                put(
                    "/api/issues/$id",
                    admin,
                    """{"version":1,"read":true,"x":"${"x".repeat(70_000)}"}""",
                ).status shouldBe
                    HttpStatusCode.PayloadTooLarge
            }
        }

        "every issue route should respond 401 without a token and 403 for a user that is not an admin" {
            val family = givenUser("family", AppRole.FAMILY_USER)
            val guest = givenUser("guest", AppRole.GUEST_USER)
            val noRole = givenUser("norole", null)
            val body = """{"version":1,"read":true}"""

            withApp {
                listOf("/api/issues", "/api/issues/unread-count").forEach { path ->
                    withClue(path) {
                        get(path, null).status shouldBe HttpStatusCode.Unauthorized
                        listOf(family, guest, noRole).forEach { get(path, it).status shouldBe HttpStatusCode.Forbidden }
                    }
                }
                put("/api/issues/1", null, body).status shouldBe HttpStatusCode.Unauthorized
                listOf(family, guest, noRole).forEach {
                    put("/api/issues/1", it, body).status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    })
