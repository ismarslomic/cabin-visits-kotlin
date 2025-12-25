package no.slomic.smarthytte.schema

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.plugins.configureGraphQL
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.guestAmira
import no.slomic.smarthytte.reservations.guestCarlos
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository

class GraphQLEndpointGuestsTest :
    ShouldSpec({
        val testDb = TestDbSetup()
        val guestRepo = SqliteGuestRepository()
        val reservationRepo = SqliteReservationRepository()
        val vehicleTripRepo = SqliteVehicleTripRepository()

        beforeEach { testDb.setupDb() }
        afterEach { testDb.teardownDb() }

        should("serve allGuests over HTTP GraphQL with correct mapping and order") {
            // Seed data via repository
            guestRepo.addOrUpdate(guestCarlos)
            guestRepo.addOrUpdate(guestAmira)

            testApplication {
                application {
                    // mount only GraphQL with in-memory DB-backed repos
                    configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo)
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"query":"{ allGuests { id firstName lastName birthYear gender email } }"}""")
                }

                response.status.value shouldBe 200
                val body = response.bodyAsText()

                // quick smoke checks
                body.shouldContain("\"data\"")
                body.shouldContain("allGuests")

                val root = Json.parseToJsonElement(body).jsonObject
                val data = root["data"]!!.jsonObject
                val guests = data["allGuests"]!!.jsonArray

                // order should be by firstName: Amira, Carlos
                guests.map { it.jsonObject["firstName"]!!.jsonPrimitive.content } shouldContainInOrder listOf(
                    guestAmira.firstName,
                    guestCarlos.firstName,
                )

                val amira = guests.first().jsonObject
                amira["id"]!!.jsonPrimitive.content shouldBe guestAmira.id
                amira["birthYear"]!!.jsonPrimitive.content.toInt() shouldBe guestAmira.birthYear
                amira["gender"]!!.jsonPrimitive.content shouldBe guestAmira.gender.name
                amira["email"]!!.jsonPrimitive.content shouldBe guestAmira.email

                val carlos = guests[1].jsonObject
                carlos["id"]!!.jsonPrimitive.content shouldBe guestCarlos.id
                carlos["birthYear"]!!.jsonPrimitive.content.toInt() shouldBe guestCarlos.birthYear
                carlos["gender"]!!.jsonPrimitive.content shouldBe guestCarlos.gender.name
                carlos["email"]!!.jsonPrimitive.content shouldBe guestCarlos.email
            }
        }

        should("serve guestById over HTTP GraphQL and return null for missing and object for existing") {
            // missing first
            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }
                val respMissing = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"query":"{ guestById(id: \"missing\") { id } }"}""")
                }
                respMissing.status.value shouldBe 200
                val bodyMissing = respMissing.bodyAsText()
                val rootMissing = Json.parseToJsonElement(bodyMissing).jsonObject
                val dataMissing = rootMissing["data"]!!.jsonObject
                dataMissing["guestById"]!!.jsonPrimitive.isString shouldBe false // null
            }

            // existing
            guestRepo.addOrUpdate(guestAmira)

            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }
                val resp = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"query":"{ guestById(id: \"amira\") { id firstName lastName birthYear gender email } }"}""",
                    )
                }
                resp.status.value shouldBe 200
                val body = resp.bodyAsText()
                val data = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject

                val guest = data["guestById"]!!.jsonObject
                guest["id"]!!.jsonPrimitive.content shouldBe guestAmira.id
                guest["firstName"]!!.jsonPrimitive.content shouldBe guestAmira.firstName
                guest["lastName"]!!.jsonPrimitive.content shouldBe guestAmira.lastName
                guest["birthYear"]!!.jsonPrimitive.content.toInt() shouldBe guestAmira.birthYear
                guest["gender"]!!.jsonPrimitive.content shouldBe guestAmira.gender.name
                guest["email"]!!.jsonPrimitive.content shouldBe guestAmira.email
            }
        }
    })
