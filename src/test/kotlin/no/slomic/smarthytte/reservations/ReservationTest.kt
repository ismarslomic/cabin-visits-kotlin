package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ReservationTest :
    ShouldSpec({

        fun createTrip(startTime: Instant, durationMinutes: Int): VehicleTrip {
            val endTime = startTime + durationMinutes.minutes
            return VehicleTrip(
                id = startTime.toString(),
                startTime = startTime,
                endTime = endTime,
                duration = durationMinutes.minutes,
                durationUnit = "MINUTES",
                distance = 0.0,
                distanceUnit = "km",
                averageEnergyConsumption = 0.0,
                averageEnergyConsumptionUnit = "kWh/100km",
                averageSpeed = 0.0,
                speedUnit = "km/h",
                energyRegenerated = 0.0,
                energyRegeneratedUnit = "kWh",
                startAddress = "",
                startCity = "Oslo",
                endAddress = "",
                endCity = "Ullsåk",
                totalDistance = 0.0,
            )
        }

        fun baseReservation(
            toCabinTrips: List<VehicleTrip> = emptyList(),
            fromCabinTrips: List<VehicleTrip> = emptyList(),
        ) = Reservation(
            id = "r1",
            startTime = Instant.parse("2024-06-01T00:00:00Z"),
            endTime = Instant.parse("2024-06-05T00:00:00Z"),
            guestIds = emptyList(),
            toCabinVehicleTrips = toCabinTrips,
            fromCabinVehicleTrips = fromCabinTrips,
        )

        context("toCabinDrivingDuration") {
            should("return null when no toCabin trips") {
                baseReservation().toCabinDrivingDuration shouldBe null
            }

            should("return duration of single trip") {
                val t0 = Instant.parse("2024-06-01T08:00:00Z")
                val reservation = baseReservation(toCabinTrips = listOf(createTrip(t0, 90)))
                reservation.toCabinDrivingDuration shouldBe 90.minutes
            }

            should("return sum of all legs when multiple trips") {
                val t0 = Instant.parse("2024-06-01T08:00:00Z")
                val reservation = baseReservation(
                    toCabinTrips = listOf(
                        createTrip(t0, 90),
                        createTrip(t0 + 100.minutes, 30),
                    ),
                )
                reservation.toCabinDrivingDuration shouldBe 120.minutes
            }
        }

        context("fromCabinDrivingDuration") {
            should("return null when no fromCabin trips") {
                baseReservation().fromCabinDrivingDuration shouldBe null
            }

            should("return duration of single trip") {
                val t0 = Instant.parse("2024-06-05T14:00:00Z")
                val reservation = baseReservation(fromCabinTrips = listOf(createTrip(t0, 80)))
                reservation.fromCabinDrivingDuration shouldBe 80.minutes
            }

            should("return sum of all legs when multiple trips") {
                val t0 = Instant.parse("2024-06-05T14:00:00Z")
                val reservation = baseReservation(
                    fromCabinTrips = listOf(
                        createTrip(t0, 60),
                        createTrip(t0 + 70.minutes, 30),
                    ),
                )
                reservation.fromCabinDrivingDuration shouldBe 90.minutes
            }
        }

        context("toCabinDrivingDepartureTime") {
            should("return null when no toCabin trips") {
                baseReservation().toCabinDrivingDepartureTime shouldBe null
            }

            should("return startTime of single trip") {
                val t0 = Instant.parse("2024-06-01T08:00:00Z")
                val reservation = baseReservation(toCabinTrips = listOf(createTrip(t0, 90)))
                reservation.toCabinDrivingDepartureTime shouldBe t0
            }

            should("return startTime of first leg when multiple trips") {
                val t0 = Instant.parse("2024-06-01T08:00:00Z")
                val reservation = baseReservation(
                    toCabinTrips = listOf(
                        createTrip(t0, 90),
                        createTrip(t0 + 100.minutes, 30),
                    ),
                )
                reservation.toCabinDrivingDepartureTime shouldBe t0
            }
        }

        context("toCabinDrivingArrivalTime") {
            should("return null when no toCabin trips") {
                baseReservation().toCabinDrivingArrivalTime shouldBe null
            }

            should("return endTime of single trip") {
                val t0 = Instant.parse("2024-06-01T08:00:00Z")
                val reservation = baseReservation(toCabinTrips = listOf(createTrip(t0, 90)))
                reservation.toCabinDrivingArrivalTime shouldBe t0 + 90.minutes
            }

            should("return endTime of last leg when multiple trips") {
                val t0 = Instant.parse("2024-06-01T08:00:00Z")
                val leg2Start = t0 + 100.minutes
                val reservation = baseReservation(
                    toCabinTrips = listOf(
                        createTrip(t0, 90),
                        createTrip(leg2Start, 30),
                    ),
                )
                reservation.toCabinDrivingArrivalTime shouldBe leg2Start + 30.minutes
            }
        }

        context("fromCabinDrivingDepartureTime") {
            should("return null when no fromCabin trips") {
                baseReservation().fromCabinDrivingDepartureTime shouldBe null
            }

            should("return startTime of single trip") {
                val t0 = Instant.parse("2024-06-05T14:00:00Z")
                val reservation = baseReservation(fromCabinTrips = listOf(createTrip(t0, 80)))
                reservation.fromCabinDrivingDepartureTime shouldBe t0
            }

            should("return startTime of first leg when multiple trips") {
                val t0 = Instant.parse("2024-06-05T14:00:00Z")
                val reservation = baseReservation(
                    fromCabinTrips = listOf(
                        createTrip(t0, 60),
                        createTrip(t0 + 70.minutes, 30),
                    ),
                )
                reservation.fromCabinDrivingDepartureTime shouldBe t0
            }
        }

        context("fromCabinDrivingArrivalTime") {
            should("return null when no fromCabin trips") {
                baseReservation().fromCabinDrivingArrivalTime shouldBe null
            }

            should("return endTime of single trip") {
                val t0 = Instant.parse("2024-06-05T14:00:00Z")
                val reservation = baseReservation(fromCabinTrips = listOf(createTrip(t0, 80)))
                reservation.fromCabinDrivingArrivalTime shouldBe t0 + 80.minutes
            }

            should("return endTime of last leg when multiple trips") {
                val t0 = Instant.parse("2024-06-05T14:00:00Z")
                val leg2Start = t0 + 70.minutes
                val reservation = baseReservation(
                    fromCabinTrips = listOf(
                        createTrip(t0, 60),
                        createTrip(leg2Start, 30),
                    ),
                )
                reservation.fromCabinDrivingArrivalTime shouldBe leg2Start + 30.minutes
            }
        }
    })
