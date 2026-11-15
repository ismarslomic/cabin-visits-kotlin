package no.slomic.smarthytte.vehicletrips.jlr

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.vehicletrips.DataQuality
import no.slomic.smarthytte.vehicletrips.GeoPoint
import no.slomic.smarthytte.vehicletrips.TripPlaces
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val json = Json { ignoreUnknownKeys = true }

private val journeyJson = """
    {
      "items": [{
        "journeyId": "8764829",
        "startTime": "2026-11-01T10:00:00Z",
        "endTime": "2026-11-01T12:30:00Z",
        "duration": 9000,
        "distance": {"value": 200.3, "unit": "km"},
        "averageSpeed": {"value": 80.1, "unit": "km/h"},
        "startLocation": {"latitude": 59.9, "longitude": 10.7},
        "endLocation": {"latitude": 60.8, "longitude": 9.1},
        "somethingNew": true
      }],
      "page": {"totalElements": 1, "totalPages": 1, "currentPage": 0, "pageSize": 100}
    }
""".trimIndent()

private val oslo = TripPlaces(startCity = "Oslo", startAddress = "Gate 1", endCity = "Ullsåk", endAddress = "Vei 2")

class JlrJourneyMappingTest :
    ShouldSpec({
        val response = json.decodeFromString<JlrJourneyListResponse>(journeyJson)
        val journey = response.items.single()

        context("parsing") {
            should("read the journeys and the page, and ignore unknown fields") {
                response.page shouldBe
                    JlrPageResponse(totalElements = 1, totalPages = 1, currentPage = 0, pageSize = 100)
                journey.journeyId shouldBe "8764829"
                journey.startTime shouldBe Instant.parse("2026-11-01T10:00:00Z")
                journey.duration shouldBe 9000.0
                journey.distance shouldBe JlrValueUnitResponse(200.3, "km")
                journey.endLocation shouldBe JlrLocationResponse(60.8, 9.1)
            }

            should("read a duration with decimals") {
                val decoded = json.decodeFromString<JlrJourneyListResponse>(journeyJson.replace("9000", "9000.5"))

                decoded.items.single().duration shouldBe 9000.5
            }
        }

        context("mapping to a vehicle trip") {
            should("use the end time in epoch milliseconds as id, and keep the journey id and the coordinates") {
                val trip = journey.toVehicleTrip(oslo)

                trip.id shouldBe Instant.parse("2026-11-01T12:30:00Z").toEpochMilliseconds().toString()
                trip.jlrJourneyId shouldBe "8764829"
                trip.startPoint shouldBe GeoPoint(59.9, 10.7)
                trip.endPoint shouldBe GeoPoint(60.8, 9.1)
                trip.startTime shouldBe Instant.parse("2026-11-01T10:00:00Z")
                trip.endTime shouldBe Instant.parse("2026-11-01T12:30:00Z")
            }

            should(
                "take distance and speed with their units as received, and total distance equal to distance",
            ) {
                val trip = journey.toVehicleTrip(oslo)

                trip.distance shouldBe 200.3
                trip.totalDistance shouldBe 200.3
                trip.distanceUnit shouldBe "km"
                trip.averageSpeed shouldBe 80.1
                trip.speedUnit shouldBe "km/h"
            }

            should("read the duration as seconds and label it sec") {
                val trip = journey.toVehicleTrip(oslo)

                trip.duration shouldBe 150.minutes
                trip.durationUnit shouldBe "sec"
            }

            should("have no energy values") {
                val trip = journey.toVehicleTrip(oslo)

                trip.averageEnergyConsumption shouldBe null
                trip.averageEnergyConsumptionUnit shouldBe null
                trip.energyRegenerated shouldBe null
                trip.energyRegeneratedUnit shouldBe null
            }

            should("be complete with the given places") {
                val trip = journey.toVehicleTrip(oslo)

                trip.dataQuality shouldBe DataQuality.COMPLETE
                trip.startCity shouldBe "Oslo"
                trip.startAddress shouldBe "Gate 1"
                trip.endCity shouldBe "Ullsåk"
                trip.endAddress shouldBe "Vei 2"
            }

            should("be complete without addresses, as an address is for display only") {
                val trip = journey.toVehicleTrip(oslo.copy(startAddress = null, endAddress = null))

                trip.dataQuality shouldBe DataQuality.COMPLETE
            }

            should("miss the place when the lookup failed") {
                val trip = journey.toVehicleTrip(places = null)

                trip.dataQuality shouldBe DataQuality.PLACE_MISSING
                trip.startCity shouldBe null
                trip.endCity shouldBe null
                trip.startAddress shouldBe null
                trip.endAddress shouldBe null
            }

            should("miss the place when only one of the cities is known") {
                journey.toVehicleTrip(oslo.copy(endCity = null)).dataQuality shouldBe DataQuality.PLACE_MISSING
                journey.toVehicleTrip(oslo.copy(startCity = null)).dataQuality shouldBe DataQuality.PLACE_MISSING
            }
        }
    })
