package no.slomic.smarthytte.vehicletrips

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.Month

class CabinVehicleTripTest :
    ShouldSpec({
        val golCityName = "Gol"

        context("List<VehicleTrip>.totalDurationMinutes") {
            should("return null for empty list") {
                val trips = emptyList<VehicleTrip>()
                trips.totalDurationMinutes() shouldBe null
            }

            should("return total duration for single trip") {
                val trips = listOf(
                    createTrip(
                        HOME_CITY_NAME,
                        CABIN_CITY_NAME,
                        Instant.parse("2024-01-01T10:00:00Z"),
                        Instant.parse("2024-01-01T11:05:00Z"),
                    ),
                )
                trips.totalDurationMinutes() shouldBe 65
            }

            should("return sum of durations for multiple trips") {
                val trips = listOf(
                    createTrip(
                        HOME_CITY_NAME,
                        golCityName,
                        Instant.parse("2024-01-01T10:00:00Z"),
                        Instant.parse("2024-01-01T11:00:00Z"),
                    ),
                    createTrip(
                        golCityName,
                        CABIN_CITY_NAME,
                        Instant.parse("2024-01-01T11:15:00Z"),
                        Instant.parse("2024-01-01T11:45:00Z"),
                    ),
                )
                trips.totalDurationMinutes() shouldBe 90
            }
        }

        context("List<CabinVehicleTrip> extension functions") {
            val trip2024Jan = CabinVehicleTrip(
                toCabinTrips = listOf(
                    createTrip(
                        HOME_CITY_NAME,
                        CABIN_CITY_NAME,
                        Instant.parse("2024-01-01T10:00:00Z"),
                        Instant.parse("2024-01-01T12:00:00Z"),
                    ),
                ),
                fromCabinTrips = listOf(
                    createTrip(
                        CABIN_CITY_NAME,
                        HOME_CITY_NAME,
                        Instant.parse("2024-01-02T10:00:00Z"),
                        Instant.parse("2024-01-02T11:50:00Z"),
                    ),
                ),
                atCabinTrips = emptyList(),
            )
            val trip2024Feb = CabinVehicleTrip(
                toCabinTrips = listOf(
                    createTrip(
                        HOME_CITY_NAME,
                        CABIN_CITY_NAME,
                        Instant.parse("2024-02-01T10:00:00Z"),
                        Instant.parse("2024-02-01T12:10:00Z"),
                    ),
                ),
                fromCabinTrips = listOf(
                    createTrip(
                        CABIN_CITY_NAME,
                        HOME_CITY_NAME,
                        Instant.parse("2024-02-02T10:00:00Z"),
                        Instant.parse("2024-02-02T12:05:00Z"),
                    ),
                ),
                atCabinTrips = emptyList(),
            )
            val trip2023Dec = CabinVehicleTrip(
                toCabinTrips = listOf(
                    createTrip(
                        HOME_CITY_NAME,
                        CABIN_CITY_NAME,
                        Instant.parse("2023-12-31T10:00:00Z"),
                        Instant.parse("2023-12-31T12:20:00Z"),
                    ),
                ),
                fromCabinTrips = listOf(
                    createTrip(
                        CABIN_CITY_NAME,
                        HOME_CITY_NAME,
                        Instant.parse("2024-01-01T10:00:00Z"),
                        Instant.parse("2024-01-01T12:15:00Z"),
                    ),
                ),
                atCabinTrips = emptyList(),
            )

            val tripSpanningYears = CabinVehicleTrip(
                toCabinTrips = listOf(
                    createTrip(
                        HOME_CITY_NAME,
                        CABIN_CITY_NAME,
                        Instant.parse("2025-12-31T22:00:00Z"),
                        Instant.parse("2026-01-01T01:00:00Z"),
                    ),
                ),
                fromCabinTrips = emptyList(),
                atCabinTrips = emptyList(),
            )

            val fromCabinTripSpanningYears = CabinVehicleTrip(
                toCabinTrips = emptyList(),
                fromCabinTrips = listOf(
                    createTrip(
                        CABIN_CITY_NAME,
                        HOME_CITY_NAME,
                        Instant.parse("2025-12-31T23:00:00Z"),
                        Instant.parse("2026-01-01T02:00:00Z"),
                    ),
                ),
                atCabinTrips = emptyList(),
            )

            val allTrips = listOf(trip2024Jan, trip2024Feb, trip2023Dec, tripSpanningYears, fromCabinTripSpanningYears)

            should("handle empty list of cabin trips") {
                emptyList<CabinVehicleTrip>().toCabinDurations(2024) shouldBe emptyList()
                emptyList<CabinVehicleTrip>().fromCabinDurations(2024) shouldBe emptyList()
                emptyList<CabinVehicleTrip>().toCabinDurations(2024, Month.JANUARY) shouldBe emptyList()
                emptyList<CabinVehicleTrip>().fromCabinDurations(2024, Month.JANUARY) shouldBe emptyList()
            }

            should("toCabinDurations(year) filters correctly based on start date") {
                // trip2024Jan starts in 2024-01-01
                // trip2024Feb starts in 2024-02-01
                // trip2023Dec starts in 2023-12-31
                // tripSpanningYears starts in 2025-12-31
                allTrips.toCabinDurations(2024) shouldBe listOf(120, 130)
                allTrips.toCabinDurations(2023) shouldBe listOf(140)
                allTrips.toCabinDurations(2025) shouldBe listOf(180)
                allTrips.toCabinDurations(2026) shouldBe emptyList()
            }

            should("toCabinDurations(year, month) filters correctly based on start date and month") {
                allTrips.toCabinDurations(2024, Month.JANUARY) shouldBe listOf(120)
                allTrips.toCabinDurations(2024, Month.FEBRUARY) shouldBe listOf(130)
                allTrips.toCabinDurations(2023, Month.DECEMBER) shouldBe listOf(140)
                allTrips.toCabinDurations(2025, Month.DECEMBER) shouldBe listOf(180)
                allTrips.toCabinDurations(2026, Month.JANUARY) shouldBe emptyList()
            }

            should("fromCabinDurations(year) filters correctly based on start date") {
                // trip2024Jan starts in 2024-01-02
                // trip2024Feb starts in 2024-02-02
                // trip2023Dec starts in 2024-01-01
                // fromCabinTripSpanningYears starts in 2025-12-31
                allTrips.fromCabinDurations(2024) shouldBe listOf(110, 125, 135)
                allTrips.fromCabinDurations(2023) shouldBe emptyList()
                allTrips.fromCabinDurations(2025) shouldBe listOf(180)
                allTrips.fromCabinDurations(2026) shouldBe emptyList()
            }

            should("fromCabinDurations(year, month) filters correctly based on start date and month") {
                allTrips.fromCabinDurations(2024, Month.JANUARY) shouldBe listOf(110, 135)
                allTrips.fromCabinDurations(2024, Month.FEBRUARY) shouldBe listOf(125)
                allTrips.fromCabinDurations(2023, Month.DECEMBER) shouldBe emptyList()
                allTrips.fromCabinDurations(2025, Month.DECEMBER) shouldBe listOf(180)
                allTrips.fromCabinDurations(2026, Month.JANUARY) shouldBe emptyList()
            }

            context("moment-based extension functions") {
                should(
                    "avgDepartureHomeMinutes filters correctly and returns avg minutes since midnight in Oslo time",
                ) {
                    allTrips.avgDepartureHomeMinutes(2024) shouldBe 660
                    allTrips.avgDepartureHomeMinutes(2023) shouldBe 660
                    allTrips.avgDepartureHomeMinutes(2025) shouldBe 1380
                    allTrips.avgDepartureHomeMinutes(2026) shouldBe null
                }

                should(
                    "avgArrivalCabinMinutes filters correctly and returns avg minutes since midnight in Oslo time",
                ) {
                    allTrips.avgArrivalCabinMinutes(2024) shouldBe 785
                    allTrips.avgArrivalCabinMinutes(2023) shouldBe 800
                    allTrips.avgArrivalCabinMinutes(2026) shouldBe 120
                    allTrips.avgArrivalCabinMinutes(2025) shouldBe null
                }

                should(
                    "avgDepartureCabinMinutes filters correctly and returns avg minutes since midnight in Oslo time",
                ) {
                    allTrips.avgDepartureCabinMinutes(2024) shouldBe 660
                    allTrips.avgDepartureCabinMinutes(2026) shouldBe 0
                    allTrips.avgDepartureCabinMinutes(2025) shouldBe null
                    allTrips.avgDepartureCabinMinutes(2023) shouldBe null
                }

                should(
                    "avgArrivalHomeMinutes filters correctly and returns avg minutes since midnight in Oslo time",
                ) {
                    allTrips.avgArrivalHomeMinutes(2024) shouldBe 783
                    allTrips.avgArrivalHomeMinutes(2026) shouldBe 180
                    allTrips.avgArrivalHomeMinutes(2025) shouldBe null
                    allTrips.avgArrivalHomeMinutes(2023) shouldBe null
                }

                should("handle month filtering for moments correctly") {
                    allTrips.avgDepartureHomeMinutes(2024, Month.JANUARY) shouldBe 660
                    allTrips.avgDepartureHomeMinutes(2024, Month.FEBRUARY) shouldBe 660
                    allTrips.avgDepartureHomeMinutes(2023, Month.DECEMBER) shouldBe 660
                    allTrips.avgDepartureHomeMinutes(2025, Month.DECEMBER) shouldBe 1380

                    allTrips.avgArrivalCabinMinutes(2026, Month.JANUARY) shouldBe 120
                    allTrips.avgDepartureCabinMinutes(2026, Month.JANUARY) shouldBe 0
                    allTrips.avgArrivalHomeMinutes(2026, Month.JANUARY) shouldBe 180
                }

                should("handle empty list of cabin trips for moments") {
                    emptyList<CabinVehicleTrip>().avgDepartureHomeMinutes(2024) shouldBe null
                    emptyList<CabinVehicleTrip>().avgArrivalCabinMinutes(2024) shouldBe null
                    emptyList<CabinVehicleTrip>().avgDepartureCabinMinutes(2024) shouldBe null
                    emptyList<CabinVehicleTrip>().avgArrivalHomeMinutes(2024) shouldBe null
                }
            }
        }
    })
