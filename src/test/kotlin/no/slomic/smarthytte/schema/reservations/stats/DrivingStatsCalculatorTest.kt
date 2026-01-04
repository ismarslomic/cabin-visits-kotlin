package no.slomic.smarthytte.schema.reservations.stats

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.vehicletrips.CABIN_CITY_NAME
import no.slomic.smarthytte.vehicletrips.CabinVehicleTrip
import no.slomic.smarthytte.vehicletrips.HOME_CITY_NAME
import no.slomic.smarthytte.vehicletrips.createTrip
import kotlin.time.Duration.Companion.minutes

class DrivingStatsCalculatorTest :
    ShouldSpec({
        fun createCabinVehicleTrip(
            toCabinDurations: List<Int> = emptyList(),
            fromCabinDurations: List<Int> = emptyList(),
            year: Int = 2024,
            month: Int = 1,
        ): CabinVehicleTrip {
            val monthStr = month.toString().padStart(2, '0')
            val toTrips = toCabinDurations.map {
                createTrip(
                    HOME_CITY_NAME,
                    CABIN_CITY_NAME,
                    "$year-$monthStr-01T10:00:00Z",
                    "$year-$monthStr-01T11:00:00Z",
                    it.minutes,
                )
            }
            val fromTrips = fromCabinDurations.map {
                createTrip(
                    CABIN_CITY_NAME,
                    HOME_CITY_NAME,
                    "$year-$monthStr-01T15:00:00Z",
                    "$year-$monthStr-01T16:00:00Z",
                    it.minutes,
                )
            }
            return CabinVehicleTrip(
                toCabinTrips = toTrips,
                atCabinTrips = emptyList(),
                fromCabinTrips = fromTrips,
            )
        }

        context("alculateYearDrivingTimeStats") {
            should("return empty stats when no trips are provided") {
                val year = 2024
                val result = calculateYearDrivingTimeStats(year, emptyList())

                result.year shouldBe year
                result.avgToCabinMinutes shouldBe null
                result.avgToCabin shouldBe null
                result.minToCabinMinutes shouldBe null
                result.minToCabin shouldBe null
                result.maxToCabinMinutes shouldBe null
                result.maxToCabin shouldBe null
                result.avgFromCabinMinutes shouldBe null
                result.avgFromCabin shouldBe null
            }

            should("filter out trips from other years") {
                val year = 2024
                val trips = listOf(
                    createCabinVehicleTrip(toCabinDurations = listOf(100), year = 2023),
                    createCabinVehicleTrip(toCabinDurations = listOf(120), year = 2024),
                    createCabinVehicleTrip(toCabinDurations = listOf(140), year = 2025),
                )

                val result = calculateYearDrivingTimeStats(year, trips)

                result.year shouldBe year
                result.avgToCabinMinutes shouldBe 120
                result.minToCabinMinutes shouldBe 120
                result.maxToCabinMinutes shouldBe 120
            }

            should("calculate statistics correctly for multiple trips") {
                val year = 2024
                val trips = listOf(
                    createCabinVehicleTrip(
                        toCabinDurations = listOf(100),
                        fromCabinDurations = listOf(90),
                        year = year,
                    ),
                    createCabinVehicleTrip(
                        toCabinDurations = listOf(120),
                        fromCabinDurations = listOf(110),
                        year = year,
                    ),
                    createCabinVehicleTrip(
                        toCabinDurations = listOf(140),
                        fromCabinDurations = listOf(130),
                        year = year,
                    ),
                )

                val result = calculateYearDrivingTimeStats(year, trips)

                result.year shouldBe year
                // To Cabin: 100, 120, 140. Avg: 120, Min: 100, Max: 140
                result.avgToCabinMinutes shouldBe 120
                result.minToCabinMinutes shouldBe 100
                result.maxToCabinMinutes shouldBe 140
                result.avgToCabin shouldBe "02:00"
                result.minToCabin shouldBe "01:40"
                result.maxToCabin shouldBe "02:20"

                // From Cabin: 90, 110, 130. Avg: 110, Min: 90, Max: 130
                result.avgFromCabinMinutes shouldBe 110
                result.minFromCabinMinutes shouldBe 90
                result.maxFromCabinMinutes shouldBe 130
                result.avgFromCabin shouldBe "01:50"
                result.minFromCabin shouldBe "01:30"
                result.maxFromCabin shouldBe "02:10"
            }

            should("handle rounding in average calculation") {
                val year = 2024
                val trips = listOf(
                    createCabinVehicleTrip(toCabinDurations = listOf(100), year = year),
                    createCabinVehicleTrip(toCabinDurations = listOf(101), year = year),
                )

                val result = calculateYearDrivingTimeStats(year, trips)

                // (100 + 101) / 2 = 100.5 -> should be 100 (toInt() truncates)
                result.avgToCabinMinutes shouldBe 100
            }

            should("use toCabinStartDate for filtering toCabin and fromCabinStartDate for filtering fromCabin") {
                val year = 2024
                val trips = listOf(
                    // toCabin ends in 2024, but starts in 2023 (exclude from 2024)
                    // fromCabin starts in 2024 (include in 2024)
                    CabinVehicleTrip(
                        toCabinTrips = listOf(
                            createTrip(
                                HOME_CITY_NAME,
                                CABIN_CITY_NAME,
                                "2023-12-31T23:00:00Z",
                                "2024-01-01T01:00:00Z",
                                100.minutes,
                            ),
                        ),
                        atCabinTrips = emptyList(),
                        fromCabinTrips = listOf(
                            createTrip(
                                CABIN_CITY_NAME,
                                HOME_CITY_NAME,
                                "2024-01-01T10:00:00Z",
                                "2024-01-01T12:00:00Z",
                                120.minutes,
                            ),
                        ),
                    ),
                    // toCabin ends in 2025, but starts in 2024 (include in 2024)
                    // fromCabin ends in 2025, but starts in 2024 (include in 2024)
                    CabinVehicleTrip(
                        toCabinTrips = listOf(
                            createTrip(
                                HOME_CITY_NAME,
                                CABIN_CITY_NAME,
                                "2024-12-31T23:00:00Z",
                                "2025-01-01T01:00:00Z",
                                200.minutes,
                            ),
                        ),
                        atCabinTrips = emptyList(),
                        fromCabinTrips = listOf(
                            createTrip(
                                CABIN_CITY_NAME,
                                HOME_CITY_NAME,
                                "2024-12-31T23:00:00Z",
                                "2025-01-01T02:00:00Z",
                                140.minutes,
                            ),
                        ),
                    ),
                )

                val result = calculateYearDrivingTimeStats(year, trips)

                // toCabin: only the second one starts in 2024
                result.avgToCabinMinutes shouldBe 200
                result.minToCabinMinutes shouldBe 200
                result.maxToCabinMinutes shouldBe 200

                // fromCabin: both start in 2024. Avg: (120 + 140) / 2 = 130
                result.avgFromCabinMinutes shouldBe 130
                result.minFromCabinMinutes shouldBe 120
                result.maxFromCabinMinutes shouldBe 140
            }
        }

        context("computeYearDrivingMomentStats") {
            should("calculate average moments correctly") {
                val year = 2024
                val trips = listOf(
                    CabinVehicleTrip(
                        toCabinTrips = listOf(
                            createTrip(
                                HOME_CITY_NAME,
                                CABIN_CITY_NAME,
                                "2024-01-01T08:00:00Z", // 09:00 Oslo (Winter)
                                "2024-01-01T10:00:00Z", // 11:00 Oslo
                                120.minutes,
                            ),
                        ),
                        atCabinTrips = emptyList(),
                        fromCabinTrips = listOf(
                            createTrip(
                                CABIN_CITY_NAME,
                                HOME_CITY_NAME,
                                "2024-01-02T15:00:00Z", // 16:00 Oslo
                                "2024-01-02T17:00:00Z", // 18:00 Oslo
                                120.minutes,
                            ),
                        ),
                    ),
                    CabinVehicleTrip(
                        toCabinTrips = listOf(
                            createTrip(
                                HOME_CITY_NAME,
                                CABIN_CITY_NAME,
                                "2024-01-03T10:00:00Z", // 11:00 Oslo
                                "2024-01-03T12:00:00Z", // 13:00 Oslo
                                120.minutes,
                            ),
                        ),
                        atCabinTrips = emptyList(),
                        fromCabinTrips = listOf(
                            createTrip(
                                CABIN_CITY_NAME,
                                HOME_CITY_NAME,
                                "2024-01-04T17:00:00Z", // 18:00 Oslo
                                "2024-01-04T19:00:00Z", // 20:00 Oslo
                                120.minutes,
                            ),
                        ),
                    ),
                )

                val result = computeYearDrivingMomentStats(year, trips)

                result.year shouldBe year
                // Departure Home: 09:00 and 11:00. Avg: 10:00 (600 minutes)
                result.avgDepartureHomeMinutes shouldBe 600
                result.avgDepartureHome shouldBe "10:00"

                // Arrival Cabin: 11:00 and 13:00. Avg: 12:00 (720 minutes)
                result.avgArrivalCabinMinutes shouldBe 720
                result.avgArrivalCabin shouldBe "12:00"

                // Departure Cabin: 16:00 and 18:00. Avg: 17:00 (1020 minutes)
                result.avgDepartureCabinMinutes shouldBe 1020
                result.avgDepartureCabin shouldBe "17:00"

                // Arrival Home: 18:00 and 20:00. Avg: 19:00 (1140 minutes)
                result.avgArrivalHomeMinutes shouldBe 1140
                result.avgArrivalHome shouldBe "19:00"
            }
        }

        context("computeMonthDrivingMomentStats") {
            should("calculate average moments for the specific month correctly") {
                val year = 2024
                val month = kotlinx.datetime.Month.MARCH
                val trips = listOf(
                    // Trip in March: Departure Home 11:00 Oslo (10:00 UTC)
                    createCabinVehicleTrip(toCabinDurations = listOf(60), year = 2024, month = 3),
                    // Trip in February (should be ignored)
                    createCabinVehicleTrip(toCabinDurations = listOf(60), year = 2024, month = 2),
                )

                val result = computeMonthDrivingMomentStats(year, month, trips)

                result.monthNumber shouldBe 3
                result.year shouldBe 2024
                // 10:00 UTC on 2024-03-01 is 11:00 Oslo. 11*60 = 660 minutes.
                result.avgDepartureHomeMinutes shouldBe 660
                result.avgDepartureHome shouldBe "11:00"
            }

            should("return empty stats when no trips in month") {
                val year = 2024
                val month = kotlinx.datetime.Month.MARCH
                val result = computeMonthDrivingMomentStats(year, month, emptyList())

                result.avgDepartureHomeMinutes shouldBe null
                result.avgDepartureHome shouldBe null
            }
        }

        context("calculateMonthDrivingTimeStats") {
            should("calculate monthly stats and differences correctly") {
                val year = 2024
                val month = kotlinx.datetime.Month.MARCH
                val trips = listOf(
                    createCabinVehicleTrip(
                        toCabinDurations = listOf(120),
                        fromCabinDurations = listOf(110),
                        year = year,
                        month = 3,
                    ),
                    createCabinVehicleTrip(
                        toCabinDurations = listOf(100),
                        fromCabinDurations = listOf(90),
                        year = year,
                        month = 2,
                    ),
                )

                val result = calculateMonthDrivingTimeStats(year, month, trips)

                result.monthNumber shouldBe 3
                result.year shouldBe 2024
                result.avgToCabinMinutes shouldBe 120
                result.avgFromCabinMinutes shouldBe 110

                // Diff vs prev month: 120 - 100 = 20
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe 20
                result.diffAvgToCabinVsPrevMonth shouldBe "+00:20"

                // Diff vs prev month: 110 - 90 = 20
                result.diffAvgFromCabinMinutesVsPrevMonth shouldBe 20
                result.diffAvgFromCabinVsPrevMonth shouldBe "+00:20"
            }

            should("handle January correctly (previous month in previous year)") {
                val year = 2024
                val month = kotlinx.datetime.Month.JANUARY
                val trips = listOf(
                    createCabinVehicleTrip(toCabinDurations = listOf(120), year = 2024, month = 1),
                    createCabinVehicleTrip(toCabinDurations = listOf(100), year = 2023, month = 12),
                )

                val result = calculateMonthDrivingTimeStats(year, month, trips)

                result.monthNumber shouldBe 1
                result.year shouldBe 2024
                result.avgToCabinMinutes shouldBe 120
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe 20
                result.diffAvgToCabinVsPrevMonth shouldBe "+00:20"
            }

            should("handle empty current month") {
                val year = 2024
                val month = kotlinx.datetime.Month.MARCH
                val trips = listOf(
                    createCabinVehicleTrip(toCabinDurations = listOf(100), year = 2024, month = 2),
                )

                val result = calculateMonthDrivingTimeStats(year, month, trips)

                result.avgToCabinMinutes shouldBe null
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe null
                result.diffAvgToCabinVsPrevMonth shouldBe null
            }

            should("handle empty previous month") {
                val year = 2024
                val month = kotlinx.datetime.Month.MARCH
                val trips = listOf(
                    createCabinVehicleTrip(toCabinDurations = listOf(120), year = 2024, month = 3),
                )

                val result = calculateMonthDrivingTimeStats(year, month, trips)

                result.avgToCabinMinutes shouldBe 120
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe null
                result.diffAvgToCabinVsPrevMonth shouldBe null
            }

            should("handle negative differences") {
                val year = 2024
                val month = kotlinx.datetime.Month.MARCH
                val trips = listOf(
                    createCabinVehicleTrip(toCabinDurations = listOf(100), year = 2024, month = 3),
                    createCabinVehicleTrip(toCabinDurations = listOf(120), year = 2024, month = 2),
                )

                val result = calculateMonthDrivingTimeStats(year, month, trips)

                result.diffAvgToCabinMinutesVsPrevMonth shouldBe -20
                result.diffAvgToCabinVsPrevMonth shouldBe "-00:20"
            }

            should("handle multiple trips in both months") {
                val year = 2024
                val month = kotlinx.datetime.Month.MARCH
                val trips = listOf(
                    // March: 100, 120. Avg: 110
                    createCabinVehicleTrip(toCabinDurations = listOf(100), year = 2024, month = 3),
                    createCabinVehicleTrip(toCabinDurations = listOf(120), year = 2024, month = 3),
                    // Feb: 80, 100. Avg: 90
                    createCabinVehicleTrip(toCabinDurations = listOf(80), year = 2024, month = 2),
                    createCabinVehicleTrip(toCabinDurations = listOf(100), year = 2024, month = 2),
                )

                val result = calculateMonthDrivingTimeStats(year, month, trips)

                result.avgToCabinMinutes shouldBe 110
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe 20 // 110 - 90
            }

            should("use start dates for monthly filtering of both toCabin and fromCabin") {
                val year = 2024
                val month = kotlinx.datetime.Month.MARCH
                val trips = listOf(
                    // fromCabin starts in March, ends in April -> include in March
                    CabinVehicleTrip(
                        toCabinTrips = emptyList(),
                        atCabinTrips = emptyList(),
                        fromCabinTrips = listOf(
                            createTrip(
                                CABIN_CITY_NAME,
                                HOME_CITY_NAME,
                                "2024-03-31T23:00:00Z",
                                "2024-04-01T02:00:00Z",
                                150.minutes,
                            ),
                        ),
                    ),
                    // fromCabin starts in February, ends in March -> exclude from March
                    CabinVehicleTrip(
                        toCabinTrips = emptyList(),
                        atCabinTrips = emptyList(),
                        fromCabinTrips = listOf(
                            createTrip(
                                CABIN_CITY_NAME,
                                HOME_CITY_NAME,
                                "2024-02-29T23:00:00Z",
                                "2024-03-01T02:00:00Z",
                                100.minutes,
                            ),
                        ),
                    ),
                )

                val result = calculateMonthDrivingTimeStats(year, month, trips)

                result.avgFromCabinMinutes shouldBe 150
            }
        }
    })
