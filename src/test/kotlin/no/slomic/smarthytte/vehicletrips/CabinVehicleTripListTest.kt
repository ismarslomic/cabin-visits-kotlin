@file:Suppress("ktlint:standard:max-line-length")

package no.slomic.smarthytte.vehicletrips

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CabinVehicleTripListTest :
    ShouldSpec({
        val homeCity = "Oslo"
        val cabinCity = "Ullsåk"
        val golCity = "Gol"
        val hemsedalCity = "Hemsedal"
        val nesoddtangen = "Nesoddtangen"
        val lillehammerCity = "Lillehammer"
        val sjusjoenCity = "Sjusjoen"

        /**
         * Rules:
         * 1. Home to Cabin trip is a trip where startCity = homeCity and endCity = cabinCity
         * 2. Home to Cabin trips can contain extra stops (occurred after leaving Home and before arriving at the Cabin)
         * 3. Home to Cabin trip can exist without Cabin to Home trip
         * 4. Cabin to Home trip is a trip where startCity = cabinCity and endCity = homeCity
         * 5. Cabin to Home trips can contain extra stops (occurred after leaving the Cabin and before arriving at Home)
         * 6. Cabin to Home trip can only exist with a corresponding Home to Cabin trip (before the Home to Cabin trip,
         * in the past)
         * 7. Trips at Cabin (for instance, Cabin to Hemsedal and Hemsedal to Cabin) occurred after a Home to Cabin
         * trip should be ignored
         * 8. Trips at Home (for instance, Home to Nesoddtangen and Nesoddtangen to Home), occurred after the
         * Cabin to Home trip should be ignored
         * 8. Trips at Home (for instance, Home to Nesoddtangen and Nesoddtangen to Home), occurred before the
         * Home to Cabin trip should be ignored
         * 9. Trips at Home occurred after arriving at the Cabin without a corresponding Cabin to Home trip should be
         * ignored (this is considered an exception and randomly happens).
         */

        context("Home to Cabin trip is a trip where startCity = homeCity and endCity = cabinCity") {
            should("should find Home to Cabin trip given startCity = homeCity and endCity = cabinCity") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-01T12:00:00+01:00",
                    endTimestamp = "2025-01-01T15:00:00+01:00",
                )
            }
            should("should return empty list when no Home to Cabin trip is found") {
                val trips = listOf(
                    createTrip(homeCity, nesoddtangen, "2025-01-01T10:00:00+01:00", "2025-01-01T11:00:00+01:00"),
                    createTrip(nesoddtangen, homeCity, "2025-01-01T11:00:00+01:00", "2025-01-01T12:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 0
            }
        }

        context(
            "Home to Cabin trips can contain extra stops",
        ) {
            should("should find Home to Cabin trip given extra stops after departure and before arrival") {
                val trips = listOf(
                    createTrip(homeCity, sjusjoenCity, "2025-01-01T12:00:00+01:00", "2025-01-01T14:00:00+01:00"),
                    createTrip(sjusjoenCity, golCity, "2025-01-01T14:00:00+01:00", "2025-01-01T16:00:00+01:00"),
                    createTrip(golCity, cabinCity, "2025-01-01T16:00:00+01:00", "2025-01-01T17:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(sjusjoenCity, golCity),
                    startTimestamp = "2025-01-01T12:00:00+01:00",
                    endTimestamp = "2025-01-01T17:00:00+01:00",
                )
            }
        }

        context("Home to Cabin trip can exist without Cabin to Home trip") {
            should("should find Home to Cabin trip without Cabin to Home trip") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-01T12:00:00+01:00",
                    endTimestamp = "2025-01-01T15:00:00+01:00",
                )
            }

            should("should find Home to Cabin trip with Cabin to Home trip after it") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 2

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-01T12:00:00+01:00",
                    endTimestamp = "2025-01-01T15:00:00+01:00",
                )
            }

            should("should find Home to Cabin trip with Cabin to Home trip before it") {
                val trips = listOf(
                    createTrip(cabinCity, homeCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(homeCity, cabinCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-02T12:00:00+01:00",
                    endTimestamp = "2025-01-02T15:00:00+01:00",
                )
            }
        }

        context(
            "Trips at Cabin occurred after a Home to Cabin trip should be ignored",
        ) {
            should("should find Home to Cabin trip and ignore the trips at cabin after arrival") {
                val trips = listOf(
                    createTrip(homeCity, golCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(golCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, hemsedalCity, "2025-01-01T12:05:00+01:00", "2025-01-01T15:10:00+01:00"),
                    createTrip(hemsedalCity, cabinCity, "2025-01-01T12:15:00+01:00", "2025-01-01T15:20:00+01:00"),
                    createTrip(cabinCity, hemsedalCity, "2025-01-02T10:34:00+01:00", "2025-01-02T10:40:00+01:00"),
                    createTrip(hemsedalCity, cabinCity, "2025-01-02T12:47:00+01:00", "2025-01-02T12:52:00+01:00"),
                    createTrip(cabinCity, hemsedalCity, "2025-01-02T13:17:00+01:00", "2025-01-02T13:25:00+01:00"),
                    createTrip(hemsedalCity, cabinCity, "2025-01-02T14:26:00+01:00", "2025-01-02T14:34:00+01:00"),
                    createTrip(cabinCity, hemsedalCity, "2025-01-03T09:53:00+01:00", "2025-01-03T10:02:00+01:00"),
                    createTrip(hemsedalCity, hemsedalCity, "2025-01-03T10:15:00+01:00", "2025-01-03T10:18:00+01:00"),
                    createTrip(hemsedalCity, hemsedalCity, "2025-01-03T10:27:00+01:00", "2025-01-03T10:40:00+01:00"),
                    createTrip(hemsedalCity, hemsedalCity, "2025-01-03T13:36:00+01:00", "2025-01-03T13:53:00+01:00"),
                    createTrip(hemsedalCity, cabinCity, "2025-01-03T14:01:00+01:00", "2025-01-03T14:10:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(golCity),
                    startTimestamp = "2025-01-01T12:00:00+01:00",
                    endTimestamp = "2025-01-01T15:00:00+01:00",
                )
            }
        }

        context(
            "Trips at Home occurred before the Home to Cabin trip should be ignored",
        ) {
            should("should find Home to Cabin trip and ignore the trips at home before the departure from Home") {
                val trips = listOf(
                    createTrip(homeCity, nesoddtangen, "2025-01-01T10:00:00+01:00", "2025-01-01T11:00:00+01:00"),
                    createTrip(nesoddtangen, homeCity, "2025-01-01T11:00:00+01:00", "2025-01-01T12:00:00+01:00"),
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-01T12:00:00+01:00",
                    endTimestamp = "2025-01-01T15:00:00+01:00",
                )
            }
        }

        context("Cabin to Home trip is a trip where startCity = cabinCity and endCity = homeCity") {
            should("should find Cabin to Home trip given correct startCity, endCity and Home to Cabin trip before it") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 2

                assertTrip(
                    vehicleTrip = homeCabinTrips[1],
                    startCity = cabinCity,
                    endCity = homeCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-02T12:00:00+01:00",
                    endTimestamp = "2025-01-02T15:00:00+01:00",
                )
            }

            should("should return Home to Cabin trip only when no Cabin to Home trip exists") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1
            }
        }

        context(
            "Cabin to Home trips can contain extra stops (after leaving the Cabin and before arriving at Home)",
        ) {
            should("should find Cabin to Home trip given extra stops after departure and before arrival") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, lillehammerCity, "2025-01-02T12:00:00+01:00", "2025-01-02T14:00:00+01:00"),
                    createTrip(lillehammerCity, sjusjoenCity, "2025-01-02T14:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                    createTrip(sjusjoenCity, homeCity, "2025-01-02T15:00:00+01:00", "2025-01-02T17:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 2

                assertTrip(
                    vehicleTrip = homeCabinTrips[1],
                    startCity = cabinCity,
                    endCity = homeCity,
                    extraStops = listOf(lillehammerCity, sjusjoenCity),
                    startTimestamp = "2025-01-02T12:00:00+01:00",
                    endTimestamp = "2025-01-02T17:00:00+01:00",
                )
            }
        }

        context(
            "Cabin to Home trip can only exist with a corresponding Home to Cabin trip (before the Home to Cabin trip)",
        ) {
            should(
                "should find Cabin to Home trip given Home to Cabin trip occurred before it",
            ) {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 2

                assertTrip(
                    vehicleTrip = homeCabinTrips[1],
                    startCity = cabinCity,
                    endCity = homeCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-02T12:00:00+01:00",
                    endTimestamp = "2025-01-02T15:00:00+01:00",
                )
            }

            should(
                "should find Cabin to Home trip given Home to Cabin trip occurred before it even unsorted list",
            ) {
                val trips = listOf(
                    createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 2

                assertTrip(
                    vehicleTrip = homeCabinTrips[1],
                    startCity = cabinCity,
                    endCity = homeCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-02T12:00:00+01:00",
                    endTimestamp = "2025-01-02T15:00:00+01:00",
                )
            }

            should(
                "should return Home to Cabin trip only given no Cabin to Home trip before it",
            ) {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                    createTrip(cabinCity, homeCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-02T12:00:00+01:00",
                    endTimestamp = "2025-01-02T15:00:00+01:00",
                )
            }

            should("should return Home to Cabin trip only when no Cabin to Home trip exists") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1
            }

            context(
                "Trips at Home occurred after a Cabin to Home trip should be ignored",
            ) {
                should("should find Cabin to Home trip and ignore the trips at home after the arrival to Home") {
                    val trips = listOf(
                        createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                        createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                        createTrip(homeCity, nesoddtangen, "2025-01-02T17:00:00+01:00", "2025-01-02T18:00:00+01:00"),
                        createTrip(nesoddtangen, homeCity, "2025-01-02T19:00:00+01:00", "2025-01-02T20:00:00+01:00"),
                        createTrip(homeCity, nesoddtangen, "2025-01-03T11:00:00+01:00", "2025-01-03T12:00:00+01:00"),
                        createTrip(nesoddtangen, homeCity, "2025-01-03T12:00:00+01:00", "2025-01-03T15:00:00+01:00"),
                    )

                    val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                    homeCabinTrips shouldHaveSize 2

                    assertTrip(
                        vehicleTrip = homeCabinTrips[1],
                        startCity = cabinCity,
                        endCity = homeCity,
                        extraStops = listOf(),
                        startTimestamp = "2025-01-02T12:00:00+01:00",
                        endTimestamp = "2025-01-02T15:00:00+01:00",
                    )
                }
            }

            context(
                "Trips at Home occurring while still at Cabin and without returning trip should be ignored",
            ) {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(homeCity, nesoddtangen, "2025-01-02T17:00:00+01:00", "2025-01-02T18:00:00+01:00"),
                    createTrip(nesoddtangen, homeCity, "2025-01-02T19:00:00+01:00", "2025-01-02T20:00:00+01:00"),
                    createTrip(homeCity, nesoddtangen, "2025-01-03T11:00:00+01:00", "2025-01-03T12:00:00+01:00"),
                    createTrip(nesoddtangen, homeCity, "2025-01-03T12:00:00+01:00", "2025-01-03T15:00:00+01:00"),
                )

                val homeCabinTrips: List<VehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertTrip(
                    vehicleTrip = homeCabinTrips[0],
                    startCity = homeCity,
                    endCity = cabinCity,
                    extraStops = listOf(),
                    startTimestamp = "2025-01-01T12:00:00+01:00",
                    endTimestamp = "2025-01-01T15:00:00+01:00",
                )
            }
        }
    })

@Suppress("LongParameterList")
fun assertTrip(
    vehicleTrip: VehicleTrip,
    startCity: String,
    endCity: String,
    extraStops: List<String>,
    startTimestamp: String,
    endTimestamp: String,
) {
    vehicleTrip.startCity shouldBe startCity
    vehicleTrip.endCity shouldBe endCity
    vehicleTrip.extraStops shouldBe extraStops
    vehicleTrip.startTime shouldBe Instant.parse(startTimestamp)
    vehicleTrip.endTime shouldBe Instant.parse(endTimestamp)
}
