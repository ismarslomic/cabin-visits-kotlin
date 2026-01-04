package no.slomic.smarthytte.vehicletrips

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Month
import kotlin.time.Duration.Companion.minutes

class CabinVehicleTripTest :
    ShouldSpec({
        context("List<VehicleTrip>.totalDurationMinutes") {
            should("return null for empty list") {
                val trips = emptyList<VehicleTrip>()
                trips.totalDurationMinutes() shouldBe null
            }

            should("return total duration for single trip") {
                val trips = listOf(
                    createTrip(
                        "Oslo",
                        "Ullsåk",
                        "2024-01-01T10:00:00Z",
                        "2024-01-01T11:00:00Z",
                    ).copy(duration = 65.minutes),
                )
                trips.totalDurationMinutes() shouldBe 65
            }

            should("return sum of durations for multiple trips") {
                val trips = listOf(
                    createTrip(
                        "Oslo",
                        "Gol",
                        "2024-01-01T10:00:00Z",
                        "2024-01-01T11:00:00Z",
                    ).copy(duration = 60.minutes),
                    createTrip(
                        "Gol",
                        "Ullsåk",
                        "2024-01-01T11:15:00Z",
                        "2024-01-01T11:45:00Z",
                    ).copy(duration = 30.minutes),
                )
                trips.totalDurationMinutes() shouldBe 90
            }
        }

        context("List<CabinVehicleTrip> extension functions") {
            val trip2024Jan = CabinVehicleTrip(
                toCabinTrips = listOf(
                    createTrip(
                        "Oslo",
                        "Ullsåk",
                        "2024-01-01T10:00:00Z",
                        "2024-01-01T12:00:00Z",
                    ).copy(duration = 120.minutes),
                ),
                fromCabinTrips = listOf(
                    createTrip(
                        "Ullsåk",
                        "Oslo",
                        "2024-01-02T10:00:00Z",
                        "2024-01-02T12:00:00Z",
                    ).copy(duration = 110.minutes),
                ),
                atCabinTrips = emptyList(),
            )
            val trip2024Feb = CabinVehicleTrip(
                toCabinTrips = listOf(
                    createTrip(
                        "Oslo",
                        "Ullsåk",
                        "2024-02-01T10:00:00Z",
                        "2024-02-01T12:00:00Z",
                    ).copy(duration = 130.minutes),
                ),
                fromCabinTrips = listOf(
                    createTrip(
                        "Ullsåk",
                        "Oslo",
                        "2024-02-02T10:00:00Z",
                        "2024-02-02T12:00:00Z",
                    ).copy(duration = 125.minutes),
                ),
                atCabinTrips = emptyList(),
            )
            val trip2023Dec = CabinVehicleTrip(
                toCabinTrips = listOf(
                    createTrip(
                        "Oslo",
                        "Ullsåk",
                        "2023-12-31T10:00:00Z",
                        "2023-12-31T12:00:00Z",
                    ).copy(duration = 140.minutes),
                ),
                fromCabinTrips = listOf(
                    createTrip(
                        "Ullsåk",
                        "Oslo",
                        "2024-01-01T10:00:00Z",
                        "2024-01-01T12:00:00Z",
                    ).copy(duration = 135.minutes),
                ),
                atCabinTrips = emptyList(),
            )

            val tripSpanningYears = CabinVehicleTrip(
                toCabinTrips = listOf(
                    createTrip(
                        "Oslo",
                        "Ullsåk",
                        "2025-12-31T22:00:00Z",
                        "2026-01-01T01:00:00Z",
                    ).copy(duration = 180.minutes),
                ),
                fromCabinTrips = emptyList(),
                atCabinTrips = emptyList(),
            )

            val fromCabinTripSpanningYears = CabinVehicleTrip(
                toCabinTrips = emptyList(),
                fromCabinTrips = listOf(
                    createTrip(
                        "Ullsåk",
                        "Oslo",
                        "2025-12-31T23:00:00Z",
                        "2026-01-01T02:00:00Z",
                    ).copy(duration = 180.minutes),
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
        }
    })
