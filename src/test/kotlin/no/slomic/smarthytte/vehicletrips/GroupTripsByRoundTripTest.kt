package no.slomic.smarthytte.vehicletrips

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.datetime.Instant

class GroupTripsByRoundTripTest :
    ShouldSpec({
        val homeCity = "Oslo"
        val cabinCity = "Ullsåk"
        val golCity = "Gol"
        val hemsedalCity = "Hemsedal"
        val nesoddtangen = "Nesoddtangen"
        val lillehammerCity = "Lillehammer"
        val sjusjoenCity = "Sjusjoen"

        context("Home to Cabin trip is a trip where startCity = homeCity and endCity = cabinCity") {
            should("should find Home to Cabin trip when correct startCity and endCity") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 1
            }

            should("should return empty list when no Home to Cabin trip is found") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        nesoddtangen,
                        Instant.parse("2025-01-01T10:00:00+01:00"),
                        Instant.parse("2025-01-01T11:00:00+01:00"),
                    ),
                    createTrip(
                        nesoddtangen,
                        homeCity,
                        Instant.parse("2025-01-01T11:00:00+01:00"),
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        hemsedalCity,
                        Instant.parse("2025-01-02T11:00:00+01:00"),
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        cabinCity,
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                        Instant.parse("2025-01-02T13:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 2
                groupedTrips[0] shouldHaveSize 2
                groupedTrips[1] shouldHaveSize 2
            }
        }

        context(
            "Home to Cabin trips can contain extra stops",
        ) {
            should("should find Home to Cabin trip when extra stops after departure and before arrival to Cabin") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        sjusjoenCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T14:00:00+01:00"),
                    ),
                    createTrip(
                        sjusjoenCity,
                        golCity,
                        Instant.parse("2025-01-01T14:00:00+01:00"),
                        Instant.parse("2025-01-01T16:00:00+01:00"),
                    ),
                    createTrip(
                        golCity,
                        cabinCity,
                        Instant.parse("2025-01-01T16:00:00+01:00"),
                        Instant.parse("2025-01-01T17:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 3
            }
        }

        context("Home to Cabin trip can exist without Cabin to Home trip") {
            should("should find Home to Cabin trip without Cabin to Home trip") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 1
            }

            should("should find Home to Cabin trip when Cabin to Home trip after it") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        homeCity,
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                        Instant.parse("2025-01-02T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 2
            }

            should("should only find Home to Cabin trip when a Cabin to Home trip before it") {
                val trips = listOf(
                    createTrip(
                        cabinCity,
                        homeCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                        Instant.parse("2025-01-02T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 2
            }
        }

        context(
            "Trips at Cabin occurred after a Home to Cabin trip should be found and counted as at the Cabin",
        ) {
            should(
                "should find Home to Cabin trip and the trips at the Cabin even when missing the Cabin to home trip",
            ) {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        golCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                    createTrip(
                        golCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        hemsedalCity,
                        Instant.parse("2025-01-01T12:05:00+01:00"),
                        Instant.parse("2025-01-01T15:10:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:15:00+01:00"),
                        Instant.parse("2025-01-01T15:20:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        hemsedalCity,
                        Instant.parse("2025-01-02T10:34:00+01:00"),
                        Instant.parse("2025-01-02T10:40:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        cabinCity,
                        Instant.parse("2025-01-02T12:47:00+01:00"),
                        Instant.parse("2025-01-02T12:52:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        hemsedalCity,
                        Instant.parse("2025-01-02T13:17:00+01:00"),
                        Instant.parse("2025-01-02T13:25:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        cabinCity,
                        Instant.parse("2025-01-02T14:26:00+01:00"),
                        Instant.parse("2025-01-02T14:34:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        hemsedalCity,
                        Instant.parse("2025-01-03T09:53:00+01:00"),
                        Instant.parse("2025-01-03T10:02:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        hemsedalCity,
                        Instant.parse("2025-01-03T10:15:00+01:00"),
                        Instant.parse("2025-01-03T10:18:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        hemsedalCity,
                        Instant.parse("2025-01-03T10:27:00+01:00"),
                        Instant.parse("2025-01-03T10:40:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        hemsedalCity,
                        Instant.parse("2025-01-03T13:36:00+01:00"),
                        Instant.parse("2025-01-03T13:53:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        cabinCity,
                        Instant.parse("2025-01-03T14:01:00+01:00"),
                        Instant.parse("2025-01-03T14:10:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 13
            }
        }

        context(
            "Trips at Home occurred before the Home to Cabin trip should be ignored",
        ) {
            should("should find Home to Cabin trip and ignore the trips at home before the departure to Cabin") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        nesoddtangen,
                        Instant.parse("2025-01-01T10:00:00+01:00"),
                        Instant.parse("2025-01-01T11:00:00+01:00"),
                    ),
                    createTrip(
                        nesoddtangen,
                        homeCity,
                        Instant.parse("2025-01-01T11:00:00+01:00"),
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                    ),
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 2
                groupedTrips[0] shouldHaveSize 2
                groupedTrips[1] shouldHaveSize 1
            }
        }

        context("Cabin to Home trip is a trip where startCity = cabinCity and endCity = homeCity") {
            should("should find Cabin to Home trip given correct start and end city and preceding Home to Cabin trip") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        homeCity,
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                        Instant.parse("2025-01-02T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 2
            }

            should("should find Home to Cabin even if Cabin to Home trip is missing") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 1
            }
        }

        context(
            "Cabin to Home trips can contain extra stops (after leaving the Cabin and before arriving at Home)",
        ) {
            should("should find Cabin to Home trip with intermediate stops") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        lillehammerCity,
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                        Instant.parse("2025-01-02T14:00:00+01:00"),
                    ),
                    createTrip(
                        lillehammerCity,
                        sjusjoenCity,
                        Instant.parse("2025-01-02T14:00:00+01:00"),
                        Instant.parse("2025-01-02T15:00:00+01:00"),
                    ),
                    createTrip(
                        sjusjoenCity,
                        homeCity,
                        Instant.parse("2025-01-02T15:00:00+01:00"),
                        Instant.parse("2025-01-02T17:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 4
            }
        }

        context(
            "Cabin to Home trip can only exist with a corresponding Home to Cabin trip (before the Home to Cabin trip)",
        ) {
            should(
                "should find Cabin to Home trip if preceded by Home to Cabin trip",
            ) {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        homeCity,
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                        Instant.parse("2025-01-02T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 2
            }

            should(
                "should find Cabin to Home trip after Home to Cabin trip, even with unsorted trips",
            ) {
                val trips = listOf(
                    createTrip(
                        cabinCity,
                        homeCity,
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                        Instant.parse("2025-01-02T15:00:00+01:00"),
                    ),
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 2
            }

            should(
                "should find Home to Cabin trip only if earlier Cabin to Home trip exists",
            ) {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-02T12:00:00+01:00"),
                        Instant.parse("2025-01-02T15:00:00+01:00"),
                    ),
                    createTrip(
                        cabinCity,
                        homeCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 2
            }

            should("should find Home to Cabin trip only if Cabin to Home is absent") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 1
                groupedTrips[0] shouldHaveSize 1
            }

            context(
                "Trips at Home occurred after a Cabin to Home trip should be ignored",
            ) {
                should("should find Cabin to Home trip and ignore the trips at home after the arrival to Home") {
                    val trips = listOf(
                        createTrip(
                            homeCity,
                            cabinCity,
                            Instant.parse("2025-01-01T12:00:00+01:00"),
                            Instant.parse("2025-01-01T15:00:00+01:00"),
                        ),
                        createTrip(
                            cabinCity,
                            homeCity,
                            Instant.parse("2025-01-02T12:00:00+01:00"),
                            Instant.parse("2025-01-02T15:00:00+01:00"),
                        ),
                        createTrip(
                            homeCity,
                            nesoddtangen,
                            Instant.parse("2025-01-02T17:00:00+01:00"),
                            Instant.parse("2025-01-02T18:00:00+01:00"),
                        ),
                        createTrip(
                            nesoddtangen,
                            homeCity,
                            Instant.parse("2025-01-02T19:00:00+01:00"),
                            Instant.parse("2025-01-02T20:00:00+01:00"),
                        ),
                        createTrip(
                            homeCity,
                            nesoddtangen,
                            Instant.parse("2025-01-03T11:00:00+01:00"),
                            Instant.parse("2025-01-03T12:00:00+01:00"),
                        ),
                        createTrip(
                            nesoddtangen,
                            homeCity,
                            Instant.parse("2025-01-03T12:00:00+01:00"),
                            Instant.parse("2025-01-03T15:00:00+01:00"),
                        ),
                    )

                    val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                    groupedTrips shouldHaveSize 3
                    groupedTrips[0] shouldHaveSize 2
                    groupedTrips[1] shouldHaveSize 2
                    groupedTrips[2] shouldHaveSize 2
                }
            }

            context(
                "Trips at Home occurring while still at Cabin and without returning trip should be ignored",
            ) {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        cabinCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T15:00:00+01:00"),
                    ),
                    createTrip(
                        homeCity,
                        nesoddtangen,
                        Instant.parse("2025-01-02T17:00:00+01:00"),
                        Instant.parse("2025-01-02T18:00:00+01:00"),
                    ),
                    createTrip(
                        nesoddtangen,
                        homeCity,
                        Instant.parse("2025-01-02T19:00:00+01:00"),
                        Instant.parse("2025-01-02T20:00:00+01:00"),
                    ),
                    createTrip(
                        homeCity,
                        nesoddtangen,
                        Instant.parse("2025-01-03T11:00:00+01:00"),
                        Instant.parse("2025-01-03T12:00:00+01:00"),
                    ),
                    createTrip(
                        nesoddtangen,
                        homeCity,
                        Instant.parse("2025-01-03T12:00:00+01:00"),
                        Instant.parse("2025-01-03T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 3
                groupedTrips[0] shouldHaveSize 1
                groupedTrips[1] shouldHaveSize 2
                groupedTrips[2] shouldHaveSize 2
            }

            context("Cabin trips missing Cabin to Home trip should not stop processing new Cabin trips") {
                val trips = listOf(
                    createTrip(
                        homeCity,
                        golCity,
                        Instant.parse("2025-01-01T12:00:00+01:00"),
                        Instant.parse("2025-01-01T14:00:00+01:00"),
                    ),
                    createTrip(
                        golCity,
                        cabinCity,
                        Instant.parse("2025-01-01T14:15:00+01:00"),
                        Instant.parse("2025-01-01T15:15:00+01:00"),
                    ),

                    createTrip(
                        homeCity,
                        golCity,
                        Instant.parse("2025-01-05T12:00:00+01:00"),
                        Instant.parse("2025-01-05T14:00:00+01:00"),
                    ),
                    createTrip(
                        golCity,
                        cabinCity,
                        Instant.parse("2025-01-05T14:30:00+01:00"),
                        Instant.parse("2025-01-05T15:45:00+01:00"),
                    ),

                    createTrip(
                        cabinCity,
                        hemsedalCity,
                        Instant.parse("2025-01-06T11:00:00+01:00"),
                        Instant.parse("2025-01-06T12:00:00+01:00"),
                    ),
                    createTrip(
                        hemsedalCity,
                        cabinCity,
                        Instant.parse("2025-01-06T11:00:00+01:00"),
                        Instant.parse("2025-01-06T12:00:00+01:00"),
                    ),

                    createTrip(
                        cabinCity,
                        homeCity,
                        Instant.parse("2025-01-07T12:00:00+01:00"),
                        Instant.parse("2025-01-07T15:00:00+01:00"),
                    ),
                )

                val groupedTrips: List<List<VehicleTrip>> = CabinVehicleTripList(trips).groupTripsByRoundTrip(trips)

                groupedTrips shouldHaveSize 2
                groupedTrips[0] shouldHaveSize 2
                groupedTrips[1] shouldHaveSize 5
            }
        }
    })
