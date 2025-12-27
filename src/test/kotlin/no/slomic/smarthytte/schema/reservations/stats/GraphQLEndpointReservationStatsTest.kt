package no.slomic.smarthytte.schema.reservations.stats

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.plugins.configureGraphQL
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.guestLena
import no.slomic.smarthytte.reservations.reservation
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.CABIN_CITY_NAME
import no.slomic.smarthytte.vehicletrips.HOME_CITY_NAME
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.createTrip
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class GraphQLEndpointReservationStatsTest :
    ShouldSpec({
        val testDb = TestDbSetup()
        val guestRepo = SqliteGuestRepository()
        val reservationRepo = SqliteReservationRepository()
        val vehicleTripRepo = SqliteVehicleTripRepository()

        beforeEach { testDb.setupDb() }
        afterEach { testDb.teardownDb() }

        should("serve reservationStats over HTTP GraphQL") {
            // Seed data
            guestRepo.addOrUpdate(guestLena)

            // 2024 reservations
            val r2024 = reservation.copy(
                id = "r2024",
                startTime = LocalDate(2024, 5, 10).atStartOfDayIn(TimeZone.Companion.UTC),
                endTime = LocalDate(2024, 5, 15).atStartOfDayIn(TimeZone.Companion.UTC),
                guestIds = listOf(guestLena.id),
            )
            // 2025 reservations
            val r2025 = reservation.copy(
                id = "r2025",
                startTime = LocalDate(2025, 1, 1).atStartOfDayIn(TimeZone.Companion.UTC),
                endTime = LocalDate(2025, 1, 5).atStartOfDayIn(TimeZone.Companion.UTC),
                guestIds = listOf(guestLena.id),
            )

            reservationRepo.addOrUpdate(r2024)
            reservationRepo.addOrUpdate(r2025)

            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }

                val query = """
                    query {
                      reservationStats {
                        year
                        totalVisits
                        months {
                          monthNumber
                          totalVisits
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

                val root = Json.Default.parseToJsonElement(body).jsonObject
                val data = root["data"]!!.jsonObject
                val stats = data["reservationStats"]!!.jsonArray

                stats.size shouldBe 2

                val stat2024 = stats.find { it.jsonObject["year"]!!.jsonPrimitive.content == "2024" }!!.jsonObject
                stat2024["totalVisits"]!!.jsonPrimitive.content.toInt() shouldBe 1

                val may2024 =
                    stat2024["months"]!!.jsonArray.find {
                        it.jsonObject["monthNumber"]!!.jsonPrimitive.content == "5"
                    }!!.jsonObject
                may2024["totalVisits"]!!.jsonPrimitive.content.toInt() shouldBe 1

                val stat2025 = stats.find { it.jsonObject["year"]!!.jsonPrimitive.content == "2025" }!!.jsonObject
                stat2025["totalVisits"]!!.jsonPrimitive.content.toInt() shouldBe 1

                val jan2025 =
                    stat2025["months"]!!.jsonArray.find {
                        it.jsonObject["monthNumber"]!!.jsonPrimitive.content == "1"
                    }!!.jsonObject
                jan2025["totalVisits"]!!.jsonPrimitive.content.toInt() shouldBe 1
            }
        }

        should("filter reservationStats by years") {
            // Seed data
            guestRepo.addOrUpdate(guestLena)

            val r2024 = reservation.copy(
                id = "r2024",
                startTime = LocalDate(2024, 5, 10).atStartOfDayIn(TimeZone.Companion.UTC),
                endTime = LocalDate(2024, 5, 15).atStartOfDayIn(TimeZone.Companion.UTC),
                guestIds = listOf(guestLena.id),
            )
            val r2025 = reservation.copy(
                id = "r2025",
                startTime = LocalDate(2025, 1, 1).atStartOfDayIn(TimeZone.Companion.UTC),
                endTime = LocalDate(2025, 1, 5).atStartOfDayIn(TimeZone.Companion.UTC),
                guestIds = listOf(guestLena.id),
            )

            reservationRepo.addOrUpdate(r2024)
            reservationRepo.addOrUpdate(r2025)

            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }

                val query = """
                    query {
                      reservationStats(years: [2025]) {
                        year
                        totalVisits
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

                val root = Json.Default.parseToJsonElement(body).jsonObject
                val data = root["data"]!!.jsonObject
                val stats = data["reservationStats"]!!.jsonArray

                stats.size shouldBe 1
                stats[0].jsonObject["year"]!!.jsonPrimitive.content shouldBe "2025"
            }
        }

        should("return empty reservationStats when no reservations exist") {
            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }

                val query = """
                    query {
                      reservationStats {
                        year
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

                val root = Json.Default.parseToJsonElement(body).jsonObject
                val data = root["data"]!!.jsonObject
                val stats = data["reservationStats"]!!.jsonArray

                stats.size shouldBe 0
            }
        }

        should("return all stats defined in YearReservationStats") {
            // Seed data
            guestRepo.addOrUpdate(guestLena)
            val r2025 = reservation.copy(
                id = "r2025",
                startTime = LocalDate(2025, 1, 1).atStartOfDayIn(TimeZone.Companion.UTC),
                endTime = LocalDate(2025, 1, 5).atStartOfDayIn(TimeZone.Companion.UTC),
                guestIds = listOf(guestLena.id),
            )
            reservationRepo.addOrUpdate(r2025)

            // Seed driving data
            val tripToCabin = createTrip(
                startCity = HOME_CITY_NAME,
                endCity = CABIN_CITY_NAME,
                startTime = "2025-01-01T08:00:00Z",
                endTime = "2025-01-01T11:00:00Z",
            ).copy(duration = 3.hours)
            val tripFromCabin = createTrip(
                startCity = CABIN_CITY_NAME,
                endCity = HOME_CITY_NAME,
                startTime = "2025-01-05T15:00:00Z",
                endTime = "2025-01-05T18:30:00Z",
            ).copy(duration = 210.minutes)
            vehicleTripRepo.addOrUpdate(tripToCabin)
            vehicleTripRepo.addOrUpdate(tripFromCabin)

            testApplication {
                application { configureGraphQL(guestRepo, reservationRepo, vehicleTripRepo) }

                val query = """
                    query {
                      reservationStats(years: [2025]) {
                        year
                        totalVisits
                        comparedToLast12Months
                        averageMonthlyVisits
                        totalStayDays
                        comparedStayDaysToLast12Months
                        averageMonthlyStayDays
                        percentDaysOccupied
                        percentWeeksOccupied
                        percentMonthsOccupied
                        monthMostVisits {
                          monthNumber
                          monthName
                          count
                        }
                        monthFewestVisits {
                          monthNumber
                          monthName
                          count
                        }
                        monthWithLongestStay {
                          monthNumber
                          monthName
                          days
                        }
                        topGuestByDays {
                          guestId
                          firstName
                          lastName
                          age
                          totalVisits
                          totalStayDays
                        }
                        newGuests {
                          guestId
                          firstName
                          lastName
                          age
                          totalVisits
                          totalStayDays
                        }
                        guests {
                          guestId
                          firstName
                          lastName
                          age
                          totalVisits
                          totalStayDays
                        }
                        drivingTime {
                          year
                          avgToCabinMinutes
                          avgToCabin
                          minToCabinMinutes
                          minToCabin
                          maxToCabinMinutes
                          maxToCabin
                          avgFromCabinMinutes
                          avgFromCabin
                          minFromCabinMinutes
                          minFromCabin
                          maxFromCabinMinutes
                          maxFromCabin
                        }
                        drivingMoments {
                          year
                          avgDepartureHomeMinutes
                          avgDepartureHome
                          avgArrivalCabinMinutes
                          avgArrivalCabin
                          avgDepartureCabinMinutes
                          avgDepartureCabin
                          avgArrivalHomeMinutes
                          avgArrivalHome
                        }
                        months {
                          monthNumber
                          monthName
                          totalVisits
                          comparedToLast30Days
                          comparedToSameMonthLastYear
                          comparedToYearToDateAverage
                          minStayDays
                          maxStayDays
                          avgStayDays
                          percentDaysOccupied
                          percentWeeksOccupied
                          guests {
                            guestId
                            firstName
                            lastName
                            age
                            totalVisits
                            totalStayDays
                          }
                          drivingTime {
                            monthNumber
                            monthName
                            year
                            avgToCabinMinutes
                            avgToCabin
                            minToCabinMinutes
                            minToCabin
                            maxToCabinMinutes
                            maxToCabin
                            avgFromCabinMinutes
                            avgFromCabin
                            minFromCabinMinutes
                            minFromCabin
                            maxFromCabinMinutes
                            maxFromCabin
                            diffAvgToCabinMinutesVsPrevMonth
                            diffAvgToCabinVsPrevMonth
                            diffAvgFromCabinMinutesVsPrevMonth
                            diffAvgFromCabinVsPrevMonth
                          }
                          drivingMoments {
                            monthNumber
                            monthName
                            year
                            avgDepartureHomeMinutes
                            avgDepartureHome
                            avgArrivalCabinMinutes
                            avgArrivalCabin
                            avgDepartureCabinMinutes
                            avgDepartureCabin
                            avgArrivalHomeMinutes
                            avgArrivalHome
                          }
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

                val root = Json.Default.parseToJsonElement(body).jsonObject
                val data = root["data"]!!.jsonObject
                val stats = data["reservationStats"]!!.jsonArray

                stats.size shouldBe 1
                val stat = stats[0].jsonObject
                stat["year"]!!.jsonPrimitive.content shouldBe "2025"
                stat["totalVisits"]!!.jsonPrimitive.content.toInt() shouldBe 1
                stat["totalStayDays"]!!.jsonPrimitive.content.toInt() shouldBe 4 // 1st to 5th is 4 nights

                // Driving Time Year assertions
                val dtYear = stat["drivingTime"]!!.jsonObject
                dtYear["avgToCabinMinutes"]!!.jsonPrimitive.content.toInt() shouldBe 180
                dtYear["avgFromCabinMinutes"]!!.jsonPrimitive.content.toInt() shouldBe 210

                // Driving Moments Year assertions
                val dmYear = stat["drivingMoments"]!!.jsonObject
                dmYear["avgDepartureHome"]!!.jsonPrimitive.content shouldBe "09:00" // 08:00 UTC is 09:00 Oslo in Winter
                dmYear["avgArrivalHome"]!!.jsonPrimitive.content shouldBe "19:30" // 18:30 UTC is 19:30 Oslo in Winter

                // Monthly stats assertions
                val months = stat["months"]!!.jsonArray
                months.size shouldBe 12
                val jan = months[0].jsonObject
                jan["monthNumber"]!!.jsonPrimitive.content.toInt() shouldBe 1
                jan["totalVisits"]!!.jsonPrimitive.content.toInt() shouldBe 1

                val dtJan = jan["drivingTime"]!!.jsonObject
                dtJan["avgToCabinMinutes"]!!.jsonPrimitive.content.toInt() shouldBe 180
                dtJan["avgFromCabinMinutes"]!!.jsonPrimitive.content.toInt() shouldBe 210

                val dmJan = jan["drivingMoments"]!!.jsonObject
                dmJan["avgDepartureHome"]!!.jsonPrimitive.content shouldBe "09:00"
                dmJan["avgArrivalHome"]!!.jsonPrimitive.content shouldBe "19:30"

                // Guest stats assertions
                val guests = jan["guests"]!!.jsonArray
                guests.size shouldBe 1
                guests[0].jsonObject["guestId"]!!.jsonPrimitive.content shouldBe guestLena.id
            }
        }
    })
