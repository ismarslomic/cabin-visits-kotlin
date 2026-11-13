package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
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
            guests = emptyList(),
            toCabinVehicleTrips = toCabinTrips,
            fromCabinVehicleTrips = fromCabinTrips,
        )

        context("stays") {
            // baseReservation: 2024-06-01 to 2024-06-05 (UTC dates), 4 nights, 5 days
            fun stayOf(guest: ReservationGuest) = baseReservation().copy(guests = listOf(guest)).stays.single()

            should("follow the reservation for a guest without own dates") {
                val stay = stayOf(ReservationGuest("g1"))

                stay shouldBe GuestStay("g1", "r1", LocalDate(2024, 6, 1), LocalDate(2024, 6, 5))
                stay.nights shouldBe baseReservation().durationNights
                stay.days shouldBe baseReservation().durationDays
            }

            should("use the own dates, each end independently") {
                val late = stayOf(ReservationGuest("g1", fromDate = LocalDate(2024, 6, 3)))
                val early = stayOf(ReservationGuest("g2", toDate = LocalDate(2024, 6, 2)))
                val middle = stayOf(ReservationGuest("g3", LocalDate(2024, 6, 2), LocalDate(2024, 6, 4)))

                late.startDate shouldBe LocalDate(2024, 6, 3)
                late.endDate shouldBe LocalDate(2024, 6, 5)
                late.nights shouldBe 2
                late.days shouldBe 3
                early.startDate shouldBe LocalDate(2024, 6, 1)
                early.endDate shouldBe LocalDate(2024, 6, 2)
                early.nights shouldBe 1
                early.days shouldBe 2
                middle.nights shouldBe 2
                middle.days shouldBe 3
            }

            should("count a single-day stay as 0 nights and 1 day") {
                val day = stayOf(ReservationGuest("g1", LocalDate(2024, 6, 3), LocalDate(2024, 6, 3)))

                day.nights shouldBe 0
                day.days shouldBe 1
            }

            should("have one stay per guest, in the order of the guests, and list the guest ids") {
                val withStays = baseReservation().copy(
                    guests = listOf(ReservationGuest("g1"), ReservationGuest("g2", fromDate = LocalDate(2024, 6, 3))),
                )

                withStays.guestIds shouldBe listOf("g1", "g2")
                withStays.stays.map { it.guestId to it.startDate } shouldBe
                    listOf("g1" to LocalDate(2024, 6, 1), "g2" to LocalDate(2024, 6, 3))
            }

            should("count the days of a stay inside a period, and its status on a day") {
                val stay = stayOf(ReservationGuest("g1", LocalDate(2024, 6, 3), LocalDate(2024, 6, 4)))

                stay.daysInPeriod(LocalDate(2024, 6, 1), LocalDate(2024, 6, 4)) shouldBe 1
                stay.daysInPeriod(LocalDate(2024, 6, 4), LocalDate(2024, 7, 1)) shouldBe 1
                stay.daysInPeriod(LocalDate(2024, 6, 5), LocalDate(2024, 7, 1)) shouldBe 0
                stay.visitStatus(LocalDate(2024, 6, 2)) shouldBe VisitStatus.UPCOMING
                stay.visitStatus(LocalDate(2024, 6, 3)) shouldBe VisitStatus.ONGOING
                stay.visitStatus(LocalDate(2024, 6, 4)) shouldBe VisitStatus.COMPLETED
            }

            should("sum visits, nights and days per guest over the stays") {
                val stays = listOf(
                    GuestStay("g1", "r1", LocalDate(2024, 6, 1), LocalDate(2024, 6, 3)),
                    GuestStay("g1", "r2", LocalDate(2024, 6, 10), LocalDate(2024, 6, 10)),
                    GuestStay("g2", "r1", LocalDate(2024, 6, 2), LocalDate(2024, 6, 3)),
                )

                stays.visitsByGuest() shouldBe mapOf("g1" to 2, "g2" to 1)
                stays.nightsByGuest() shouldBe mapOf("g1" to 2, "g2" to 1)
                stays.daysByGuest(LocalDate(2024, 6, 2), LocalDate(2024, 6, 11)) shouldBe mapOf("g1" to 3, "g2" to 2)
            }
        }

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
