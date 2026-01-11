package no.slomic.smarthytte.checkinouts

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.vehicletrips.createTrip

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
class CabinVehicleTripListTest :
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
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }

            should("should return empty list when no Home to Cabin trip is found") {
                val trips = listOf(
                    createTrip(homeCity, nesoddtangen, "2025-01-01T10:00:00+01:00", "2025-01-01T11:00:00+01:00"),
                    createTrip(nesoddtangen, homeCity, "2025-01-01T11:00:00+01:00", "2025-01-01T12:00:00+01:00"),
                    createTrip(cabinCity, hemsedalCity, "2025-01-02T11:00:00+01:00", "2025-01-02T12:00:00+01:00"),
                    createTrip(hemsedalCity, cabinCity, "2025-01-02T12:00:00+01:00", "2025-01-02T13:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 0
            }
        }

        context("Home to Cabin trips can contain extra stops") {
            should("should find Home to Cabin trip when extra stops after departure and before arrival to Cabin") {
                val trips = listOf(
                    createTrip(homeCity, sjusjoenCity, "2025-01-01T12:00:00+01:00", "2025-01-01T14:00:00+01:00"),
                    createTrip(sjusjoenCity, golCity, "2025-01-01T14:00:00+01:00", "2025-01-01T16:00:00+01:00"),
                    createTrip(golCity, cabinCity, "2025-01-01T16:00:00+01:00", "2025-01-01T17:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(sjusjoenCity, golCity, cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T17:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }
        }

        context("Home to Cabin trip can exist without Cabin to Home trip") {
            should("should find Home to Cabin trip without Cabin to Home trip") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }

            should("should find Home to Cabin trip when Cabin to Home trip after it") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 1
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = Instant.parse("2025-01-02T12:00:00+01:00")
                    fromCabinEnd = Instant.parse("2025-01-02T15:00:00+01:00")
                }
            }

            should("should only find Home to Cabin trip when a Cabin to Home trip before it") {
                val trips = listOf(
                    createTrip(cabinCity, homeCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(homeCity, cabinCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-02T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-02T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }
        }

        context("Trips at Cabin occurred after a Home to Cabin trip should be found and counted as at the Cabin") {
            should(
                "should find Home to Cabin trip and the trips at the Cabin even when missing the Cabin to home trip",
            ) {
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

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(golCity, cabinCity)
                    atCabinTripsSize = 11
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }
        }

        context("Trips at Home occurred before the Home to Cabin trip should be ignored") {
            should("should find Home to Cabin trip and ignore the trips at home before the departure to Cabin") {
                val trips = listOf(
                    createTrip(homeCity, nesoddtangen, "2025-01-01T10:00:00+01:00", "2025-01-01T11:00:00+01:00"),
                    createTrip(nesoddtangen, homeCity, "2025-01-01T11:00:00+01:00", "2025-01-01T12:00:00+01:00"),
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }
        }

        context("Cabin to Home trip is a trip where startCity = cabinCity and endCity = homeCity") {
            should("should find Cabin to Home trip given correct start and end city and preceding Home to Cabin trip") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 1
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = Instant.parse("2025-01-02T12:00:00+01:00")
                    fromCabinEnd = Instant.parse("2025-01-02T15:00:00+01:00")
                    fromCabinTripsEndCities = listOf(homeCity)
                }
            }

            should("should find Home to Cabin even if Cabin to Home trip is missing") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }
        }

        context("Cabin to Home trips can contain extra stops (after leaving the Cabin and before arriving at Home)") {
            should("should find Cabin to Home trip with intermediate stops") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, lillehammerCity, "2025-01-02T12:00:00+01:00", "2025-01-02T14:00:00+01:00"),
                    createTrip(lillehammerCity, sjusjoenCity, "2025-01-02T14:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                    createTrip(sjusjoenCity, homeCity, "2025-01-02T15:00:00+01:00", "2025-01-02T17:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 3
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = Instant.parse("2025-01-02T12:00:00+01:00")
                    fromCabinEnd = Instant.parse("2025-01-02T17:00:00+01:00")
                    fromCabinTripsEndCities = listOf(lillehammerCity, sjusjoenCity, homeCity)
                }
            }
        }

        context(
            "Cabin to Home trip can only exist with a corresponding Home to Cabin trip (before the Home to Cabin trip)",
        ) {
            should(
                "should find Cabin to Home trip if preceded by Home to Cabin trip",
            ) {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                    createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 1
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = Instant.parse("2025-01-02T12:00:00+01:00")
                    fromCabinEnd = Instant.parse("2025-01-02T15:00:00+01:00")
                }
            }

            should(
                "should find Cabin to Home trip after Home to Cabin trip, even with unsorted trips",
            ) {
                val trips = listOf(
                    createTrip(cabinCity, homeCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 1
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = Instant.parse("2025-01-02T12:00:00+01:00")
                    fromCabinEnd = Instant.parse("2025-01-02T15:00:00+01:00")
                }
            }

            should(
                "should find Home to Cabin trip only if earlier Cabin to Home trip exists",
            ) {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-02T12:00:00+01:00", "2025-01-02T15:00:00+01:00"),
                    createTrip(cabinCity, homeCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-02T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-02T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }

            should("should find Home to Cabin trip only if Cabin to Home is absent") {
                val trips = listOf(
                    createTrip(homeCity, cabinCity, "2025-01-01T12:00:00+01:00", "2025-01-01T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
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

                    val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                    homeCabinTrips shouldHaveSize 1

                    assertCabinTrip(homeCabinTrips[0]) {
                        toCabinTripsEndCities = listOf(cabinCity)
                        atCabinTripsSize = 0
                        fromCabinTripsSize = 1
                        toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                        toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                        fromCabinStart = Instant.parse("2025-01-02T12:00:00+01:00")
                        fromCabinEnd = Instant.parse("2025-01-02T15:00:00+01:00")
                    }
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

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 1

                assertCabinTrip(actual = homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:00:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }
            }

            context("Cabin trips missing Cabin to Home trip should not stop processing new Cabin trips") {
                val trips = listOf(
                    createTrip(homeCity, golCity, "2025-01-01T12:00:00+01:00", "2025-01-01T14:00:00+01:00"),
                    createTrip(golCity, cabinCity, "2025-01-01T14:15:00+01:00", "2025-01-01T15:15:00+01:00"),

                    createTrip(homeCity, golCity, "2025-01-05T12:00:00+01:00", "2025-01-05T14:00:00+01:00"),
                    createTrip(golCity, cabinCity, "2025-01-05T14:30:00+01:00", "2025-01-05T15:45:00+01:00"),

                    createTrip(cabinCity, hemsedalCity, "2025-01-06T11:00:00+01:00", "2025-01-06T12:00:00+01:00"),
                    createTrip(hemsedalCity, cabinCity, "2025-01-06T11:00:00+01:00", "2025-01-06T12:00:00+01:00"),

                    createTrip(cabinCity, homeCity, "2025-01-07T12:00:00+01:00", "2025-01-07T15:00:00+01:00"),
                )

                val homeCabinTrips: List<CabinVehicleTrip> = CabinVehicleTripList(trips).cabinTrips

                homeCabinTrips shouldHaveSize 2

                assertCabinTrip(actual = homeCabinTrips[0]) {
                    toCabinTripsEndCities = listOf(golCity, cabinCity)
                    atCabinTripsSize = 0
                    fromCabinTripsSize = 0
                    toCabinStart = Instant.parse("2025-01-01T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-01T15:15:00+01:00")
                    fromCabinStart = null
                    fromCabinEnd = null
                }

                assertCabinTrip(homeCabinTrips[1]) {
                    toCabinTripsEndCities = listOf(golCity, cabinCity)
                    atCabinTripsSize = 2
                    fromCabinTripsSize = 1
                    toCabinStart = Instant.parse("2025-01-05T12:00:00+01:00")
                    toCabinEnd = Instant.parse("2025-01-05T15:45:00+01:00")
                    fromCabinStart = Instant.parse("2025-01-07T12:00:00+01:00")
                    fromCabinEnd = Instant.parse("2025-01-07T15:00:00+01:00")
                }
            }
        }

        context("CabinVehicleTrip logic") {
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
        }
    })

class CabinTripExpectation {
    var toCabinTripsEndCities: List<String> = emptyList()
    var atCabinTripsSize: Int = 0
    var fromCabinTripsSize: Int = 0
    var toCabinStart: Instant? = null
    var toCabinEnd: Instant? = null
    var fromCabinStart: Instant? = null
    var fromCabinEnd: Instant? = null
    var fromCabinTripsEndCities: List<String>? = null
}

internal fun assertCabinTrip(actual: CabinVehicleTrip, block: CabinTripExpectation.() -> Unit) {
    val expected = CabinTripExpectation().apply(block)

    actual.toCabinTrips.map { it.endCity } shouldBe expected.toCabinTripsEndCities
    actual.atCabinTrips shouldHaveSize expected.atCabinTripsSize
    actual.fromCabinTrips shouldHaveSize expected.fromCabinTripsSize
    actual.toCabinStartTimestamp shouldBe expected.toCabinStart
    actual.toCabinEndTimestamp shouldBe expected.toCabinEnd
    actual.fromCabinStartTimestamp shouldBe expected.fromCabinStart
    actual.fromCabinEndTimestamp shouldBe expected.fromCabinEnd

    expected.fromCabinTripsEndCities?.let { expectedEndCityName ->
        actual.fromCabinTrips.map { it.endCity } shouldBe expectedEndCityName
    }
}
