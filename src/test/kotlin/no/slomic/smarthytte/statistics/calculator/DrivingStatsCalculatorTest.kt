package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DrivingStatsCalculatorTest :
    ShouldSpec({

        fun createTrip(startTime: Instant, endTime: Instant): VehicleTrip = VehicleTrip(
            id = startTime.toString(),
            startTime = startTime,
            endTime = endTime,
            duration = endTime - startTime,
            durationUnit = "MINUTES",
            distance = 100.0,
            distanceUnit = "km",
            averageEnergyConsumption = 20.0,
            averageEnergyConsumptionUnit = "kWh/100km",
            averageSpeed = 80.0,
            speedUnit = "km/h",
            energyRegenerated = 5.0,
            energyRegeneratedUnit = "kWh",
            startAddress = "Home",
            startCity = "Oslo",
            endAddress = "Cabin",
            endCity = "Ullsåk",
            totalDistance = 100.0,
        )

        @Suppress("LongParameterList")
        fun createReservation(
            id: String,
            start: LocalDate,
            end: LocalDate,
            toCabinDurationMinutes: Int? = null,
            fromCabinDurationMinutes: Int? = null,
            toCabinStartTime: Instant? = null,
            fromCabinStartTime: Instant? = null,
        ): Reservation {
            val toTrips = if (toCabinDurationMinutes != null) {
                val st = toCabinStartTime ?: start.atTime(10, 0).toInstant(TimeZone.UTC)
                listOf(createTrip(st, st + toCabinDurationMinutes.minutes))
            } else {
                emptyList()
            }
            val fromTrips = if (fromCabinDurationMinutes != null) {
                val st = fromCabinStartTime ?: end.atTime(15, 0).toInstant(TimeZone.UTC)
                listOf(createTrip(st, st + fromCabinDurationMinutes.minutes))
            } else {
                emptyList()
            }
            return Reservation(
                id = id,
                startTime = start.atTime(0, 0).toInstant(TimeZone.UTC),
                endTime = end.atTime(0, 0).toInstant(TimeZone.UTC),
                guestIds = emptyList(),
                toCabinVehicleTrips = toTrips,
                fromCabinVehicleTrips = fromTrips,
            )
        }

        context("calculateYearDrivingTimeStats") {
            should("return null stats when no trips are provided") {
                val result = calculateYearDrivingTimeStats(2024, emptyList())

                result.year shouldBe 2024
                result.avgToCabinMinutes shouldBe null
                result.minToCabinMinutes shouldBe null
                result.maxToCabinMinutes shouldBe null
                result.avgFromCabinMinutes shouldBe null
            }

            should("filter out trips from other years") {
                val reservations = listOf(
                    createReservation("r1", LocalDate(2023, 6, 1), LocalDate(2023, 6, 5), toCabinDurationMinutes = 100),
                    createReservation("r2", LocalDate(2024, 6, 1), LocalDate(2024, 6, 5), toCabinDurationMinutes = 120),
                    createReservation("r3", LocalDate(2025, 6, 1), LocalDate(2025, 6, 5), toCabinDurationMinutes = 140),
                )

                val result = calculateYearDrivingTimeStats(2024, reservations)

                result.avgToCabinMinutes shouldBe 120
                result.minToCabinMinutes shouldBe 120
                result.maxToCabinMinutes shouldBe 120
            }

            should("calculate statistics correctly for multiple reservations") {
                val reservations = listOf(
                    createReservation("r1", LocalDate(2024, 1, 1), LocalDate(2024, 1, 5), 100, 90),
                    createReservation("r2", LocalDate(2024, 3, 1), LocalDate(2024, 3, 5), 120, 110),
                    createReservation("r3", LocalDate(2024, 6, 1), LocalDate(2024, 6, 5), 140, 130),
                )

                val result = calculateYearDrivingTimeStats(2024, reservations)

                result.avgToCabinMinutes shouldBe 120
                result.minToCabinMinutes shouldBe 100
                result.maxToCabinMinutes shouldBe 140
                result.avgToCabin shouldBe "02:00"
                result.minToCabin shouldBe "01:40"
                result.maxToCabin shouldBe "02:20"

                result.avgFromCabinMinutes shouldBe 110
                result.minFromCabinMinutes shouldBe 90
                result.maxFromCabinMinutes shouldBe 130
            }

            should("sum all legs per reservation when multiple trips exist") {
                val start = LocalDate(2024, 6, 1)
                val end = LocalDate(2024, 6, 5)
                val t0 = start.atTime(9, 0).toInstant(TimeZone.UTC)
                val reservation = Reservation(
                    id = "r1",
                    startTime = start.atTime(0, 0).toInstant(TimeZone.UTC),
                    endTime = end.atTime(0, 0).toInstant(TimeZone.UTC),
                    guestIds = emptyList(),
                    toCabinVehicleTrips = listOf(
                        createTrip(t0, t0 + 90.minutes), // leg 1: 90 min
                        createTrip(t0 + 100.minutes, t0 + 130.minutes), // leg 2: 30 min
                    ),
                    fromCabinVehicleTrips = listOf(
                        createTrip(t0, t0 + 60.minutes), // leg 1: 60 min
                        createTrip(t0 + 70.minutes, t0 + 100.minutes), // leg 2: 30 min
                    ),
                )

                val result = calculateYearDrivingTimeStats(2024, listOf(reservation))

                // Total toCabin = 90 + 30 = 120 min (one data point, so avg = min = max)
                result.avgToCabinMinutes shouldBe 120
                result.minToCabinMinutes shouldBe 120
                result.maxToCabinMinutes shouldBe 120
                // Total fromCabin = 60 + 30 = 90 min
                result.avgFromCabinMinutes shouldBe 90
                result.minFromCabinMinutes shouldBe 90
                result.maxFromCabinMinutes shouldBe 90
            }

            should("handle rounding in average calculation") {
                val reservations = listOf(
                    createReservation("r1", LocalDate(2024, 1, 1), LocalDate(2024, 1, 5), toCabinDurationMinutes = 100),
                    createReservation("r2", LocalDate(2024, 3, 1), LocalDate(2024, 3, 5), toCabinDurationMinutes = 101),
                )

                val result = calculateYearDrivingTimeStats(2024, reservations)

                // (100 + 101) / 2 = 100.5 -> toInt() truncates to 100
                result.avgToCabinMinutes shouldBe 100
            }
        }

        context("calculateYearDrivingMomentStats") {
            should("calculate average departure/arrival times in Oslo timezone") {
                // 08:00 UTC in January = 09:00 Oslo (UTC+1)
                val trip1Start = Instant.parse("2024-01-01T08:00:00Z")
                val trip1End = Instant.parse("2024-01-01T10:00:00Z")
                // 10:00 UTC in January = 11:00 Oslo
                val trip2Start = Instant.parse("2024-01-03T10:00:00Z")
                val trip2End = Instant.parse("2024-01-03T12:00:00Z")

                // 15:00 UTC = 16:00 Oslo
                val from1Start = Instant.parse("2024-01-02T15:00:00Z")
                val from1End = Instant.parse("2024-01-02T17:00:00Z")
                // 17:00 UTC = 18:00 Oslo
                val from2Start = Instant.parse("2024-01-04T17:00:00Z")
                val from2End = Instant.parse("2024-01-04T19:00:00Z")

                val reservations = listOf(
                    Reservation(
                        id = "r1",
                        startTime = Instant.parse("2024-01-01T00:00:00Z"),
                        endTime = Instant.parse("2024-01-03T00:00:00Z"),
                        guestIds = emptyList(),
                        toCabinVehicleTrips = listOf(createTrip(trip1Start, trip1End)),
                        fromCabinVehicleTrips = listOf(createTrip(from1Start, from1End)),
                    ),
                    Reservation(
                        id = "r2",
                        startTime = Instant.parse("2024-01-03T00:00:00Z"),
                        endTime = Instant.parse("2024-01-05T00:00:00Z"),
                        guestIds = emptyList(),
                        toCabinVehicleTrips = listOf(createTrip(trip2Start, trip2End)),
                        fromCabinVehicleTrips = listOf(createTrip(from2Start, from2End)),
                    ),
                )

                val result = calculateYearDrivingMomentStats(2024, reservations)

                result.year shouldBe 2024
                // Departure Home: 09:00 and 11:00 Oslo. Avg: 10:00 (600 min)
                result.avgDepartureHomeMinutes shouldBe 600
                result.avgDepartureHome shouldBe "10:00"
                // Arrival Cabin: 11:00 and 13:00 Oslo. Avg: 12:00 (720 min)
                result.avgArrivalCabinMinutes shouldBe 720
                result.avgArrivalCabin shouldBe "12:00"
                // Departure Cabin: 16:00 and 18:00 Oslo. Avg: 17:00 (1020 min)
                result.avgDepartureCabinMinutes shouldBe 1020
                result.avgDepartureCabin shouldBe "17:00"
                // Arrival Home: 18:00 and 20:00 Oslo. Avg: 19:00 (1140 min)
                result.avgArrivalHomeMinutes shouldBe 1140
                result.avgArrivalHome shouldBe "19:00"
            }

            should("use first leg departure and last leg arrival when multiple trips per reservation") {
                // toCabin: leg1 09:00–11:00 Oslo, leg2 (stop) 11:30–12:30 Oslo
                val toTrip1Start = Instant.parse("2024-06-01T07:00:00Z") // 09:00 Oslo (UTC+2)
                val toTrip1End = Instant.parse("2024-06-01T09:00:00Z") // 11:00 Oslo
                val toTrip2Start = Instant.parse("2024-06-01T09:30:00Z") // 11:30 Oslo
                val toTrip2End = Instant.parse("2024-06-01T10:30:00Z") // 12:30 Oslo

                // fromCabin: leg1 14:00–15:00 Oslo, leg2 (stop) 15:30–17:00 Oslo
                val fromTrip1Start = Instant.parse("2024-06-03T12:00:00Z") // 14:00 Oslo
                val fromTrip1End = Instant.parse("2024-06-03T13:00:00Z") // 15:00 Oslo
                val fromTrip2Start = Instant.parse("2024-06-03T13:30:00Z") // 15:30 Oslo
                val fromTrip2End = Instant.parse("2024-06-03T15:00:00Z") // 17:00 Oslo

                val reservations = listOf(
                    Reservation(
                        id = "r1",
                        startTime = Instant.parse("2024-06-01T00:00:00Z"),
                        endTime = Instant.parse("2024-06-03T00:00:00Z"),
                        guestIds = emptyList(),
                        toCabinVehicleTrips = listOf(
                            createTrip(toTrip1Start, toTrip1End),
                            createTrip(toTrip2Start, toTrip2End),
                        ),
                        fromCabinVehicleTrips = listOf(
                            createTrip(fromTrip1Start, fromTrip1End),
                            createTrip(fromTrip2Start, fromTrip2End),
                        ),
                    ),
                )

                val result = calculateYearDrivingMomentStats(2024, reservations)

                // Departure Home = startTime of first toCabin leg = 09:00 Oslo (540 min)
                result.avgDepartureHomeMinutes shouldBe 540
                result.avgDepartureHome shouldBe "09:00"
                // Arrival Cabin = endTime of last toCabin leg = 12:30 Oslo (750 min)
                result.avgArrivalCabinMinutes shouldBe 750
                result.avgArrivalCabin shouldBe "12:30"
                // Departure Cabin = startTime of first fromCabin leg = 14:00 Oslo (840 min)
                result.avgDepartureCabinMinutes shouldBe 840
                result.avgDepartureCabin shouldBe "14:00"
                // Arrival Home = endTime of last fromCabin leg = 17:00 Oslo (1020 min)
                result.avgArrivalHomeMinutes shouldBe 1020
                result.avgArrivalHome shouldBe "17:00"
            }
        }

        context("calculateMonthDrivingTimeStats") {
            should("calculate monthly stats and diff vs previous month") {
                val reservations = listOf(
                    createReservation("r1", LocalDate(2024, 3, 1), LocalDate(2024, 3, 5), 120, 110),
                    createReservation("r2", LocalDate(2024, 2, 1), LocalDate(2024, 2, 5), 100, 90),
                )

                val result = calculateMonthDrivingTimeStats(2024, Month.MARCH, reservations)

                result.monthNumber shouldBe 3
                result.year shouldBe 2024
                result.avgToCabinMinutes shouldBe 120
                result.avgFromCabinMinutes shouldBe 110
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe 20
                result.diffAvgToCabinVsPrevMonth shouldBe "+00:20"
                result.diffAvgFromCabinMinutesVsPrevMonth shouldBe 20
                result.diffAvgFromCabinVsPrevMonth shouldBe "+00:20"
            }

            should("handle January correctly (previous month is December of prior year)") {
                val reservations = listOf(
                    createReservation("r1", LocalDate(2024, 1, 1), LocalDate(2024, 1, 5), toCabinDurationMinutes = 120),
                    createReservation(
                        "r2",
                        LocalDate(2023, 12, 1),
                        LocalDate(2023, 12, 5),
                        toCabinDurationMinutes = 100,
                    ),
                )

                val result = calculateMonthDrivingTimeStats(2024, Month.JANUARY, reservations)

                result.monthNumber shouldBe 1
                result.avgToCabinMinutes shouldBe 120
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe 20
            }

            should("return null diff when current month has no trips") {
                val reservations = listOf(
                    createReservation("r1", LocalDate(2024, 2, 1), LocalDate(2024, 2, 5), toCabinDurationMinutes = 100),
                )

                val result = calculateMonthDrivingTimeStats(2024, Month.MARCH, reservations)

                result.avgToCabinMinutes shouldBe null
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe null
            }

            should("return null diff when previous month has no trips") {
                val reservations = listOf(
                    createReservation("r1", LocalDate(2024, 3, 1), LocalDate(2024, 3, 5), toCabinDurationMinutes = 120),
                )

                val result = calculateMonthDrivingTimeStats(2024, Month.MARCH, reservations)

                result.avgToCabinMinutes shouldBe 120
                result.diffAvgToCabinMinutesVsPrevMonth shouldBe null
            }

            should("handle negative differences") {
                val reservations = listOf(
                    createReservation("r1", LocalDate(2024, 3, 1), LocalDate(2024, 3, 5), toCabinDurationMinutes = 100),
                    createReservation("r2", LocalDate(2024, 2, 1), LocalDate(2024, 2, 5), toCabinDurationMinutes = 120),
                )

                val result = calculateMonthDrivingTimeStats(2024, Month.MARCH, reservations)

                result.diffAvgToCabinMinutesVsPrevMonth shouldBe -20
                result.diffAvgToCabinVsPrevMonth shouldBe "-00:20"
            }
        }
    })
