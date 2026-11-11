package no.slomic.smarthytte.guests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
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
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.ApiDependencies
import no.slomic.smarthytte.auth.ACCESS_JWT_HEADER
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.CloudflareAccessConfig
import no.slomic.smarthytte.auth.accessToken
import no.slomic.smarthytte.auth.testCloudflareAccess
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.reservations.ReservationService
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.configureTestApiRoutes

private val jpegType = ContentType.Image.JPEG

class AvatarUploadRoutesTest :
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

        fun withApp(
            cloudflareAccess: CloudflareAccessConfig? = testCloudflareAccess,
            block: suspend ApplicationTestBuilder.() -> Unit,
        ) = testApplication {
            application {
                configureTestApiRoutes(
                    ApiDependencies(
                        guestQueryService = GuestQueryService(repository),
                        guestAvatarService = GuestAvatarService(repository),
                        guestProfileService = GuestProfileService(repository),
                        guestAdminService = GuestAdminService(repository),
                        statsService = mockk<StatsService>(),
                        reservationService = mockk<ReservationService>(),
                        appUserService = AppUserService(GuestQueryService(repository)),
                        cloudflareAccess = cloudflareAccess,
                    ),
                )
            }
            block()
        }

        suspend fun ApplicationTestBuilder.upload(
            path: String,
            email: String?,
            body: ByteArray?,
            type: String? = jpegType.toString(),
        ): HttpResponse = client.put(path) {
            email?.let { header(ACCESS_JWT_HEADER, accessToken(it)) }
            type?.let { header(HttpHeaders.ContentType, it) }
            body?.let { setBody(it) }
        }

        suspend fun HttpResponse.errorCode(): String? =
            Json.parseToJsonElement(bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content

        "admin should upload the avatar of another guest, stripped, with the admin as creator" {
            givenUsers()

            withApp {
                val response = upload(
                    "/api/guests/visitor/avatar",
                    "admin@example.com",
                    JpegTestFiles.image(JpegTestFiles.exif()),
                )

                response.status shouldBe HttpStatusCode.NoContent
                repository.avatarById("visitor")!!.image.toList() shouldBe JpegTestFiles.bare().toList()
            }
        }

        "every role should upload its own avatar through /api/me/avatar" {
            givenUsers()

            withApp {
                listOf("admin", "family", "visitor").forEach { id ->
                    withClue(id) {
                        upload("/api/me/avatar", "$id@example.com", JpegTestFiles.bare()).status shouldBe
                            HttpStatusCode.NoContent
                        repository.avatarById(id)!!.image.toList() shouldBe JpegTestFiles.bare().toList()
                    }
                }
            }
        }

        "a replaced avatar should give a new avatar url and send nosniff on the avatar" {
            givenUsers()

            withApp {
                upload("/api/me/avatar", "visitor@example.com", JpegTestFiles.bare())
                val first = repository.allAvatarVersionTimes().getValue("visitor")
                withContext(Dispatchers.IO) {
                    Thread.sleep(5)
                }
                upload("/api/me/avatar", "visitor@example.com", JpegTestFiles.image(width = 9))

                (repository.allAvatarVersionTimes().getValue("visitor") > first) shouldBe true
                val avatar = client.get("/api/guests/visitor/avatar") {
                    header(ACCESS_JWT_HEADER, accessToken("visitor@example.com"))
                }
                avatar.status shouldBe HttpStatusCode.OK
                avatar.headers["X-Content-Type-Options"] shouldBe "nosniff"
            }
        }

        "only admin should upload the avatar of another guest" {
            givenUsers()

            withApp {
                listOf("family", "visitor").forEach { id ->
                    withClue(id) {
                        upload("/api/guests/admin/avatar", "$id@example.com", JpegTestFiles.bare()).status shouldBe
                            HttpStatusCode.Forbidden
                    }
                }
                repository.avatarById("admin") shouldBe null
            }
        }

        "upload should respond 401 without token" {
            givenUsers()

            withApp {
                upload("/api/guests/admin/avatar", null, JpegTestFiles.bare()).status shouldBe
                    HttpStatusCode.Unauthorized
                upload("/api/me/avatar", null, JpegTestFiles.bare()).status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "upload should respond 401 for an invalid token" {
            givenUsers()

            withApp {
                listOf("/api/me/avatar", "/api/guests/visitor/avatar").forEach { path ->
                    client.put(path) {
                        header(ACCESS_JWT_HEADER, "not-a-token")
                        header(HttpHeaders.ContentType, jpegType.toString())
                        setBody(JpegTestFiles.bare())
                    }.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }

        "upload should respond 403 for a guest without a role on both routes" {
            givenUsers()
            repository.addOrUpdate(testGuest("norole", role = null))

            withApp {
                upload("/api/me/avatar", "norole@example.com", JpegTestFiles.bare()).status shouldBe
                    HttpStatusCode.Forbidden
                upload("/api/guests/visitor/avatar", "norole@example.com", JpegTestFiles.bare()).status shouldBe
                    HttpStatusCode.Forbidden
                repository.avatarById("norole") shouldBe null
            }
        }

        "upload should respond 503 when Cloudflare Access is not configured" {
            givenUsers()

            withApp(cloudflareAccess = null) {
                listOf("/api/me/avatar", "/api/guests/visitor/avatar").forEach { path ->
                    upload(path, "admin@example.com", JpegTestFiles.bare()).status shouldBe
                        HttpStatusCode.ServiceUnavailable
                }
            }
        }

        "upload for an unknown guest should respond 404 with a code" {
            givenUsers()

            withApp {
                val response = upload("/api/guests/nobody/avatar", "admin@example.com", JpegTestFiles.bare())

                response.status shouldBe HttpStatusCode.NotFound
                response.errorCode() shouldBe "NOT_FOUND"
            }
        }

        "upload of bytes that are not a JPEG should respond 400 INVALID_IMAGE on both routes" {
            givenUsers()

            withApp {
                val body = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0)
                listOf("/api/me/avatar", "/api/guests/visitor/avatar").forEach { path ->
                    withClue(path) {
                        val response = upload(path, "admin@example.com", body)

                        response.status shouldBe HttpStatusCode.BadRequest
                        response.errorCode() shouldBe "INVALID_IMAGE"
                    }
                }
                repository.avatarById("visitor") shouldBe null
            }
        }

        "upload of an empty body should respond 400" {
            givenUsers()

            withApp {
                upload("/api/me/avatar", "visitor@example.com", null).status shouldBe HttpStatusCode.BadRequest
            }
        }

        "upload of a JPEG with parameters on the content type should be accepted" {
            givenUsers()

            withApp {
                upload("/api/me/avatar", "visitor@example.com", JpegTestFiles.bare(), "image/jpeg; charset=binary")
                    .status shouldBe HttpStatusCode.NoContent
            }
        }

        "upload with another content type should respond 415 on both routes" {
            givenUsers()

            withApp {
                listOf(
                    "image/png",
                    "application/json",
                    "text/plain",
                    "multipart/form-data; boundary=x",
                    "application/x-www-form-urlencoded",
                ).forEach { type ->
                    withClue(type) {
                        listOf("/api/me/avatar", "/api/guests/visitor/avatar").forEach { path ->
                            val response = upload(path, "admin@example.com", JpegTestFiles.bare(), type)

                            response.status shouldBe HttpStatusCode.UnsupportedMediaType
                            response.errorCode() shouldBe "UNSUPPORTED_MEDIA_TYPE"
                        }
                    }
                }
                upload("/api/me/avatar", "admin@example.com", JpegTestFiles.bare(), null).status shouldBe
                    HttpStatusCode.UnsupportedMediaType
                repository.avatarById("visitor") shouldBe null
            }
        }

        "upload larger than the limit should respond 413 on both routes, also when the length is not announced" {
            givenUsers()

            withApp {
                val tooLarge = JpegTestFiles.bare() + ByteArray(MAX_AVATAR_BYTES.toInt()) { 1 }

                listOf("/api/me/avatar", "/api/guests/visitor/avatar").forEach { path ->
                    withClue(path) {
                        val response = upload(path, "admin@example.com", tooLarge)

                        response.status shouldBe HttpStatusCode.PayloadTooLarge
                        response.errorCode() shouldBe "BODY_TOO_LARGE"
                    }
                }
                repository.avatarById("visitor") shouldBe null
            }
        }

        "upload of exactly the limit should not be rejected for its size" {
            givenUsers()

            withApp {
                val valid = JpegTestFiles.image(JpegTestFiles.comment())
                val padding = ByteArray(MAX_AVATAR_BYTES.toInt() - valid.size) { 1 }
                // A comment segment carries at most 65 533 bytes, so the padding goes after the end marker
                val atLimit = valid + padding

                upload("/api/me/avatar", "visitor@example.com", atLimit).status shouldBe HttpStatusCode.NoContent
                repository.avatarById("visitor")!!.image.size shouldBe JpegTestFiles.bare().size
            }
        }

        "a preflight to the avatar routes should never allow cross-origin access" {
            givenUsers()

            withApp {
                listOf("/api/me/avatar", "/api/guests/visitor/avatar").forEach { path ->
                    val response = client.options(path) {
                        header(HttpHeaders.Origin, "https://evil.example")
                        header(HttpHeaders.AccessControlRequestMethod, "PUT")
                    }

                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBe null
                    response.headers[HttpHeaders.AccessControlAllowMethods] shouldBe null
                }
            }
        }
    })
