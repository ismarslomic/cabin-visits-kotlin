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
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import no.slomic.smarthytte.common.toIsoUtcString
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.plugins.configureGraphQL
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.guestAmira
import no.slomic.smarthytte.reservations.guestCarlos
import no.slomic.smarthytte.reservations.guestLena
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import no.slomic.smarthytte.reservations.checkIn as domainCheckIn
import no.slomic.smarthytte.reservations.checkOut as domainCheckOut
import no.slomic.smarthytte.reservations.reservation as domainReservation

class GraphQLEndpointReservationsTest :
    ShouldSpec({
        val testDb = TestDbSetup()
        val guestRepo = SqliteGuestRepository()
        val reservationRepo = SqliteReservationRepository()
        val vehicleTripRepo = SqliteVehicleTripRepository()

        beforeEach { testDb.setupDb() }
        afterEach { testDb.teardownDb() }

        should("serve allReservations over HTTP GraphQL with correct mapping and order") {
            // seed guests
            guestRepo.addOrUpdate(guestLena)
            guestRepo.addOrUpdate(guestCarlos)
            guestRepo.addOrUpdate(guestAmira)

            val now = Clock.System.now()
            val r1 = domainReservation.copy(
                id = "r1",
                startTime = now.minus(10.days).truncatedToMillis(),
                endTime = now.minus(7.days).truncatedToMillis(),
                guestIds = listOf(guestLena.id),
                sourceCreatedTime = now.minus(11.days).truncatedToMillis(),
                sourceUpdatedTime = now.minus(10.days).truncatedToMillis(),
            )
            val r2 = domainReservation.copy(
                id = "r2",
                startTime = now.minus(2.days).truncatedToMillis(),
                endTime = now.plus(1.days).truncatedToMillis(),
                guestIds = listOf(guestAmira.id, guestCarlos.id),
                summary = "Trip with Carlos and Amira",
                description = "Some description",
                sourceCreatedTime = now.minus(3.days).truncatedToMillis(),
                sourceUpdatedTime = now.minus(6.hours).truncatedToMillis(),
            )

            reservationRepo.addOrUpdate(r1)
            reservationRepo.addOrUpdate(r2)

            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }

                val query = """
                    query {
                      allReservations {
                        id
                        startTime
                        endTime
                        guestIds
                        summary
                        description
                        sourceCreatedTime
                        sourceUpdatedTime
                        checkIn {
                          time
                          sourceName
                          sourceId
                        }
                        checkOut {
                          time
                          sourceName
                          sourceId
                        }
                      }
                    }
                """.trimIndent()

                val requestJsonBody = buildJsonObject { put("query", query) }
                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(requestJsonBody.toString())
                }

                response.status.value shouldBe 200
                val body = response.bodyAsText()
                body.shouldContain("\"data\"")
                body.shouldContain("allReservations")

                val data = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject
                val reservations = data["allReservations"]!!.jsonArray

                // sorted by latest startTime: r2, r1
                reservations.map { it.jsonObject["id"]!!.jsonPrimitive.content } shouldContainInOrder listOf("r2", "r1")

                val first = reservations.first().jsonObject
                first["id"]!!.jsonPrimitive.content shouldBe "r2"
                first["startTime"]!!.jsonPrimitive.content shouldBe r2.startTime.toIsoUtcString()
                first["endTime"]!!.jsonPrimitive.content shouldBe r2.endTime.toIsoUtcString()
                first["guestIds"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainInOrder r2.guestIds
                first["summary"]!!.jsonPrimitive.content shouldBe (r2.summary!!)
                first["description"]!!.jsonPrimitive.content shouldBe (r2.description!!)
                first["sourceCreatedTime"]!!.jsonPrimitive.content shouldBe r2.sourceCreatedTime!!.toIsoUtcString()
                first["sourceUpdatedTime"]!!.jsonPrimitive.content shouldBe r2.sourceUpdatedTime!!.toIsoUtcString()
                first["checkIn"] shouldBe JsonNull
                first["checkOut"] shouldBe JsonNull

                val second = reservations[1].jsonObject
                second["guestIds"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainInOrder
                    listOf(guestLena.id)
            }
        }

        should("serve reservationById over HTTP GraphQL including nested checkIn/checkOut when present") {
            // seed guest and reservation
            guestRepo.addOrUpdate(guestLena)
            val resId = "res-with-checks"
            val res = domainReservation.copy(id = resId, guestIds = listOf(guestLena.id))
            reservationRepo.addOrUpdate(res)
            reservationRepo.setCheckIn(domainCheckIn, resId)
            reservationRepo.setCheckOut(domainCheckOut, resId)

            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }

                val query = """
                    query {
                      reservationById(id: "$resId") {
                        id
                        guestIds
                        checkIn {
                          time
                          sourceName
                          sourceId
                        }
                        checkOut {
                          time
                          sourceName
                          sourceId
                        }
                      }
                    }
                """.trimIndent()
                val requestJsonBody = buildJsonObject { put("query", query) }
                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(requestJsonBody.toString())
                }

                response.status.value shouldBe 200
                val body = response.bodyAsText()
                val data = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject
                val reservation = data["reservationById"]!!.jsonObject

                reservation["id"]!!.jsonPrimitive.content shouldBe resId
                reservation["guestIds"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainInOrder
                    listOf(guestLena.id)

                val checkIn = reservation["checkIn"]!!.jsonObject
                checkIn["time"]!!.jsonPrimitive.content shouldBe domainCheckIn.time.toIsoUtcString()
                checkIn["sourceId"]!!.jsonPrimitive.content shouldBe domainCheckIn.sourceId
                checkIn["sourceName"]!!.jsonPrimitive.content shouldBe "CHECK_IN_SENSOR"

                val checkOut = reservation["checkOut"]!!.jsonObject
                checkOut["time"]!!.jsonPrimitive.content shouldBe domainCheckOut.time.toIsoUtcString()
                checkOut["sourceId"]!!.jsonPrimitive.content shouldBe domainCheckOut.sourceId
                checkOut["sourceName"]!!.jsonPrimitive.content shouldBe "CHECK_IN_SENSOR"
            }
        }

        should("return null for missing reservationById over HTTP GraphQL") {
            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }
                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"query":"{ reservationById(id: \"nope\") { id } }"}""")
                }
                response.status.value shouldBe 200
                val body = response.bodyAsText()
                val data = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject
                data["reservationById"] shouldBe JsonNull
            }
        }
    })
