package no.slomic.smarthytte.checkinouts

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.vehicletrips.createTrip

class CabinVehicleTripTest :
    ShouldSpec({
        val homeCity = "Oslo"
        val cabinCity = "Ullsåk"

        context("hasArrivedCabinAt") {
            should("return false when toCabinTrips is empty") {
                val cabinVehicleTrip = CabinVehicleTrip(
                    toCabinTrips = emptyList(),
                    atCabinTrips = emptyList(),
                    fromCabinTrips = emptyList(),
                )
                cabinVehicleTrip.hasArrivedCabinAt(LocalDate(2025, 1, 1)) shouldBe false
            }

            should("return true when arrival end date is same as provided date") {
                val arrivalDate = LocalDate(2025, 1, 1)
                val cabinVehicleTrip = CabinVehicleTrip(
                    toCabinTrips = listOf(
                        createTrip(
                            startCity = homeCity,
                            endCity = cabinCity,
                            startTime = "2025-01-01T10:00:00Z",
                            endTime = "2025-01-01T13:00:00Z",
                        ),
                    ),
                    atCabinTrips = emptyList(),
                    fromCabinTrips = emptyList(),
                )
                cabinVehicleTrip.hasArrivedCabinAt(arrivalDate) shouldBe true
            }

            should("return true when arrival end date is day after provided date") {
                val arrivalDate = LocalDate(2025, 1, 1)
                val cabinVehicleTrip = CabinVehicleTrip(
                    toCabinTrips = listOf(
                        createTrip(
                            startCity = homeCity,
                            endCity = cabinCity,
                            startTime = "2025-01-01T22:00:00Z",
                            endTime = "2025-01-02T01:00:00Z",
                        ),
                    ),
                    atCabinTrips = emptyList(),
                    fromCabinTrips = emptyList(),
                )
                cabinVehicleTrip.hasArrivedCabinAt(arrivalDate) shouldBe true
            }

            should("return false when arrival end date is day before provided date") {
                val arrivalDate = LocalDate(2025, 1, 2)
                val cabinVehicleTrip = CabinVehicleTrip(
                    toCabinTrips = listOf(
                        createTrip(
                            startCity = homeCity,
                            endCity = cabinCity,
                            startTime = "2025-01-01T10:00:00Z",
                            endTime = "2025-01-01T13:00:00Z",
                        ),
                    ),
                    atCabinTrips = emptyList(),
                    fromCabinTrips = emptyList(),
                )
                cabinVehicleTrip.hasArrivedCabinAt(arrivalDate) shouldBe false
            }

            should("return false when arrival end date is more than one day after provided date") {
                val arrivalDate = LocalDate(2025, 1, 1)
                val cabinVehicleTrip = CabinVehicleTrip(
                    toCabinTrips = listOf(
                        createTrip(
                            startCity = homeCity,
                            endCity = cabinCity,
                            startTime = "2025-01-03T10:00:00Z",
                            endTime = "2025-01-03T13:00:00Z",
                        ),
                    ),
                    atCabinTrips = emptyList(),
                    fromCabinTrips = emptyList(),
                )
                cabinVehicleTrip.hasArrivedCabinAt(arrivalDate) shouldBe false
            }
        }

        context("hasDepartedCabinAt") {
            should("return false when fromCabinTrips is empty") {
                val cabinVehicleTrip = CabinVehicleTrip(
                    toCabinTrips = emptyList(),
                    atCabinTrips = emptyList(),
                    fromCabinTrips = emptyList(),
                )
                cabinVehicleTrip.hasDepartedCabinAt(LocalDate(2025, 1, 1)) shouldBe false
            }

            should("return true when departure start date is same as provided date") {
                val departureDate = LocalDate(2025, 1, 5)
                val cabinVehicleTrip = CabinVehicleTrip(
                    toCabinTrips = emptyList(),
                    atCabinTrips = emptyList(),
                    fromCabinTrips = listOf(
                        createTrip(
                            startCity = cabinCity,
                            endCity = homeCity,
                            startTime = "2025-01-05T10:00:00Z",
                            endTime = "2025-01-05T13:00:00Z",
                        ),
                    ),
                )
                cabinVehicleTrip.hasDepartedCabinAt(departureDate) shouldBe true
            }

            should("return false when departure start date is different from provided date") {
                val departureDate = LocalDate(2025, 1, 5)
                val cabinVehicleTrip = CabinVehicleTrip(
                    toCabinTrips = emptyList(),
                    atCabinTrips = emptyList(),
                    fromCabinTrips = listOf(
                        createTrip(
                            startCity = cabinCity,
                            endCity = homeCity,
                            startTime = "2025-01-04T10:00:00Z",
                            endTime = "2025-01-04T13:00:00Z",
                        ),
                    ),
                )
                cabinVehicleTrip.hasDepartedCabinAt(departureDate) shouldBe false
            }
        }
    })
