package no.slomic.smarthytte.guests

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.slomic.smarthytte.utils.TestDbSetup

class GuestRoutesTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val repository: GuestRepository = SqliteGuestRepository()
        val image = byteArrayOf(1, 2, 3)

        "get avatar should respond with the jpeg image, etag and cache control" {
            repository.addOrUpdate(guest, image)
            val updatedTime = repository.avatarById(guest.id)!!.updatedTime

            testApplication {
                application { configureGuestRoutes(repository) }

                val response = client.get("/api/guests/${guest.id}/avatar")

                response.status shouldBe HttpStatusCode.OK
                response.headers[HttpHeaders.ContentType] shouldBe ContentType.Image.JPEG.toString()
                response.headers[HttpHeaders.ETag] shouldBe "\"${updatedTime.toEpochMilliseconds()}\""
                response.headers[HttpHeaders.CacheControl] shouldBe "max-age=86400"
                response.bodyAsBytes() shouldBe image
            }
        }

        "get avatar with matching if-none-match should respond not modified without body" {
            repository.addOrUpdate(guest, image)
            val updatedTime = repository.avatarById(guest.id)!!.updatedTime

            testApplication {
                application { configureGuestRoutes(repository) }

                val response = client.get("/api/guests/${guest.id}/avatar") {
                    header(HttpHeaders.IfNoneMatch, "\"${updatedTime.toEpochMilliseconds()}\"")
                }

                response.status shouldBe HttpStatusCode.NotModified
                response.bodyAsBytes().size shouldBe 0
            }
        }

        "get avatar with outdated if-none-match should respond with the image" {
            repository.addOrUpdate(guest, image)

            testApplication {
                application { configureGuestRoutes(repository) }

                val response = client.get("/api/guests/${guest.id}/avatar") {
                    header(HttpHeaders.IfNoneMatch, "\"1\"")
                }

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsBytes() shouldBe image
            }
        }

        "get avatar for guest without avatar should respond not found" {
            repository.addOrUpdate(guest)

            testApplication {
                application { configureGuestRoutes(repository) }

                client.get("/api/guests/${guest.id}/avatar").status shouldBe HttpStatusCode.NotFound
            }
        }

        "get avatar for unknown guest should respond not found" {
            testApplication {
                application { configureGuestRoutes(repository) }

                client.get("/api/guests/unknown/avatar").status shouldBe HttpStatusCode.NotFound
            }
        }
    })
