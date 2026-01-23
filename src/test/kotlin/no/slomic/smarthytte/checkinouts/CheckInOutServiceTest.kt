package no.slomic.smarthytte.checkinouts

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.slomic.smarthytte.common.toIsoUtcString
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensor
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInStatus
import no.slomic.smarthytte.sensors.checkinouts.SqliteCheckInOutSensorRepository
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import no.slomic.smarthytte.vehicletrips.createTrip
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CheckInOutServiceTest :
    ShouldSpec({
        val testDbSetup = TestDbSetup()
        lateinit var reservationRepository: ReservationRepository
        lateinit var checkInOutSensorRepository: CheckInOutSensorRepository
        lateinit var vehicleTripRepository: VehicleTripRepository
        lateinit var service: CheckInOutService

        beforeEach {
            testDbSetup.setupDb()

            reservationRepository = SqliteReservationRepository()
            checkInOutSensorRepository = SqliteCheckInOutSensorRepository()
            vehicleTripRepository = SqliteVehicleTripRepository()
            service = CheckInOutService(reservationRepository, checkInOutSensorRepository, vehicleTripRepository)
        }

        afterEach {
            testDbSetup.teardownDb()
        }

        context("Check in/out should not be set when reservation has not started yet") {
            val scenarios = listOf(
                CheckInOutScenario(
                    reservationState = ReservationTestState.NOT_STARTED,
                    hasCheckInSensor = false,
                    hasCheckOutSensor = false,
                    hasCheckInTrip = false,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = false,
                    expectedCheckOutSet = false,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.NOT_STARTED,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = false,
                    hasCheckInTrip = false,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = false,
                    expectedCheckOutSet = false,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.NOT_STARTED,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = true,
                    hasCheckInTrip = false,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = false,
                    expectedCheckOutSet = false,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.NOT_STARTED,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = true,
                    hasCheckInTrip = true,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = false,
                    expectedCheckOutSet = false,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.NOT_STARTED,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = true,
                    hasCheckInTrip = true,
                    hasCheckOutTrip = true,
                    expectedCheckInSet = false,
                    expectedCheckOutSet = false,
                ),
            )

            suspend fun executeScenarios(scenarios: List<CheckInOutScenario>) {
                scenarios.forEach { scenario ->
                    should(scenario.describe()) {
                        // Setup based on scenario flags
                        val reservation = scenario.reservation
                        reservationRepository.addOrUpdate(reservation)

                        if (scenario.hasCheckInSensor) {
                            checkInOutSensorRepository.addOrUpdate(scenario.checkInSensor!!)
                        }

                        if (scenario.hasCheckOutSensor) {
                            checkInOutSensorRepository.addOrUpdate(scenario.checkOutSensor!!)
                        }

                        if (scenario.hasCheckInTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.checkInTrip!!)
                        }

                        if (scenario.hasCheckOutTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.checkOutTrip!!)
                        }

                        if (scenario.hasAtCabinTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.atCabinTrip!!)
                        }

                        // Execute logic
                        service.updateCheckInOutStatusForAllReservations()

                        // Assert actual with expected
                        val actualReservation: Reservation = reservationRepository.reservationById(reservation.id)!!

                        if (scenario.expectedCheckInSet) {
                            actualReservation.checkIn shouldNotBe null
                        } else {
                            actualReservation.checkIn shouldBe null
                        }

                        if (scenario.expectedCheckOutSet) {
                            actualReservation.checkOut shouldNotBe null
                        } else {
                            actualReservation.checkOut shouldBe null
                        }

                        if (scenario.hasCheckInTrip || scenario.hasCheckOutTrip) {
                            if (scenario.hasCheckInTrip) {
                                actualReservation.toCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                            if (scenario.hasCheckOutTrip) {
                                actualReservation.fromCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                            if (scenario.hasAtCabinTrip) {
                                actualReservation.atCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                        }
                    }
                }
            }
            executeScenarios(scenarios)
        }

        context("Only check in should be set when reservation has started but not yet ended") {
            val scenarios = listOf(
                CheckInOutScenario(
                    reservationState = ReservationTestState.ACTIVE,
                    hasCheckInSensor = false,
                    hasCheckOutSensor = false,
                    hasCheckInTrip = false,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = true,
                    expectedCheckInSource = CheckInOutSource.CALENDAR_EVENT,
                    expectedCheckOutSet = false,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.ACTIVE,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = false,
                    hasCheckInTrip = false,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = true,
                    expectedCheckInSource = CheckInOutSource.CHECK_IN_SENSOR,
                    expectedCheckOutSet = false,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.ACTIVE,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = false,
                    hasCheckInTrip = true,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = true,
                    expectedCheckInSource = CheckInOutSource.VEHICLE_TRIP,
                    expectedCheckOutSet = false,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.ACTIVE,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = true,
                    hasCheckInTrip = true,
                    hasCheckOutTrip = true,
                    expectedCheckInSet = true,
                    expectedCheckInSource = CheckInOutSource.VEHICLE_TRIP,
                    expectedCheckOutSet = false,
                ),
            )

            suspend fun executeScenarios(scenarios: List<CheckInOutScenario>) {
                scenarios.forEach { scenario ->
                    should(scenario.describe()) {
                        // Setup based on scenario flags
                        val reservation = scenario.reservation
                        reservationRepository.addOrUpdate(reservation)

                        if (scenario.hasCheckInSensor) {
                            checkInOutSensorRepository.addOrUpdate(scenario.checkInSensor!!)
                        }

                        if (scenario.hasCheckOutSensor) {
                            checkInOutSensorRepository.addOrUpdate(scenario.checkOutSensor!!)
                        }

                        if (scenario.hasCheckInTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.checkInTrip!!)
                        }

                        if (scenario.hasCheckOutTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.checkOutTrip!!)
                        }

                        if (scenario.hasAtCabinTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.atCabinTrip!!)
                        }

                        // Execute logic
                        service.updateCheckInOutStatusForAllReservations()

                        // Assert actual with expected
                        val actualReservation: Reservation = reservationRepository.reservationById(reservation.id)!!

                        if (scenario.expectedCheckInSet) {
                            actualReservation.checkIn shouldNotBe null
                            actualReservation.checkIn!!.sourceName shouldBe scenario.expectedCheckInSource
                        } else {
                            actualReservation.checkIn shouldBe null
                        }

                        if (scenario.expectedCheckOutSet) {
                            actualReservation.checkOut shouldNotBe null
                            actualReservation.checkOut!!.sourceName shouldBe scenario.expectedCheckOutSource
                        } else {
                            actualReservation.checkOut shouldBe null
                        }

                        if (scenario.hasCheckInTrip || scenario.hasCheckOutTrip) {
                            if (scenario.hasCheckInTrip) {
                                actualReservation.toCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                            if (scenario.hasCheckOutTrip) {
                                actualReservation.fromCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                            if (scenario.hasAtCabinTrip) {
                                actualReservation.atCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                        }
                    }
                }
            }
            executeScenarios(scenarios)
        }

        context("Check in/out should be set when reservation has ended") {
            val scenarios = listOf(
                CheckInOutScenario(
                    reservationState = ReservationTestState.ENDED,
                    hasCheckInSensor = false,
                    hasCheckOutSensor = false,
                    hasCheckInTrip = false,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = true,
                    expectedCheckInSource = CheckInOutSource.CALENDAR_EVENT,
                    expectedCheckOutSet = true,
                    expectedCheckOutSource = CheckInOutSource.CALENDAR_EVENT,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.ENDED,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = true,
                    hasCheckInTrip = false,
                    hasCheckOutTrip = false,
                    expectedCheckInSet = true,
                    expectedCheckInSource = CheckInOutSource.CHECK_IN_SENSOR,
                    expectedCheckOutSet = true,
                    expectedCheckOutSource = CheckInOutSource.CHECK_IN_SENSOR,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.ENDED,
                    hasCheckInSensor = true,
                    hasCheckOutSensor = true,
                    hasCheckInTrip = true,
                    hasCheckOutTrip = true,
                    expectedCheckInSet = true,
                    expectedCheckInSource = CheckInOutSource.VEHICLE_TRIP,
                    expectedCheckOutSet = true,
                    expectedCheckOutSource = CheckInOutSource.VEHICLE_TRIP,
                ),
                CheckInOutScenario(
                    reservationState = ReservationTestState.ENDED,
                    hasCheckInSensor = false,
                    hasCheckOutSensor = false,
                    hasCheckInTrip = true,
                    hasCheckOutTrip = true,
                    expectedCheckInSet = true,
                    expectedCheckInSource = CheckInOutSource.VEHICLE_TRIP,
                    expectedCheckOutSet = true,
                    expectedCheckOutSource = CheckInOutSource.VEHICLE_TRIP,
                ),
            )

            suspend fun executeScenarios(scenarios: List<CheckInOutScenario>) {
                scenarios.forEach { scenario ->
                    should(scenario.describe()) {
                        // Setup based on scenario flags
                        val reservation = scenario.reservation
                        reservationRepository.addOrUpdate(reservation)

                        if (scenario.hasCheckInSensor) {
                            checkInOutSensorRepository.addOrUpdate(scenario.checkInSensor!!)
                        }

                        if (scenario.hasCheckOutSensor) {
                            checkInOutSensorRepository.addOrUpdate(scenario.checkOutSensor!!)
                        }

                        if (scenario.hasCheckInTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.checkInTrip!!)
                        }

                        if (scenario.hasCheckOutTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.checkOutTrip!!)
                        }

                        if (scenario.hasAtCabinTrip) {
                            vehicleTripRepository.addOrUpdate(scenario.atCabinTrip!!)
                        }

                        // Execute logic
                        service.updateCheckInOutStatusForAllReservations()

                        // Assert actual with expected
                        val actualReservation: Reservation = reservationRepository.reservationById(reservation.id)!!

                        if (scenario.expectedCheckInSet) {
                            actualReservation.checkIn shouldNotBe null
                            actualReservation.checkIn!!.sourceName shouldBe scenario.expectedCheckInSource
                        } else {
                            actualReservation.checkIn shouldBe null
                        }

                        if (scenario.expectedCheckOutSet) {
                            actualReservation.checkOut shouldNotBe null
                            actualReservation.checkOut!!.sourceName shouldBe scenario.expectedCheckOutSource
                        } else {
                            actualReservation.checkOut shouldBe null
                        }

                        if (scenario.hasCheckInTrip || scenario.hasCheckOutTrip) {
                            if (scenario.hasCheckInTrip) {
                                actualReservation.toCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                            if (scenario.hasCheckOutTrip) {
                                actualReservation.fromCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                            if (scenario.hasAtCabinTrip) {
                                actualReservation.atCabinVehicleTrips shouldNotBe emptyList<VehicleTrip>()
                            }
                        }
                    }
                }
            }
            executeScenarios(scenarios)
        }
    })

enum class ReservationTestState {
    NOT_STARTED,
    ACTIVE,
    ENDED,
}

data class CheckInOutScenario(
    val reservationState: ReservationTestState,
    val hasCheckInSensor: Boolean,
    val hasCheckOutSensor: Boolean,
    val hasCheckInTrip: Boolean,
    val hasCheckOutTrip: Boolean,
    val expectedCheckInSet: Boolean,
    val expectedCheckOutSet: Boolean,
    val hasAtCabinTrip: Boolean = false,
    val expectedCheckInSource: CheckInOutSource? = null,
    val expectedCheckOutSource: CheckInOutSource? = null,
) {
    val reservation: Reservation = createReservation()
    val checkInSensor: CheckInOutSensor? = createCheckInSensor()
    val checkOutSensor: CheckInOutSensor? = createCheckOutSensor()
    val checkInTrip: VehicleTrip? = createCheckInTrip()
    val checkOutTrip: VehicleTrip? = createCheckOutTrip()
    val atCabinTrip: VehicleTrip? = createAtCabinTrip()

    fun describe(): String = "${reservationState.name}, " +
        "sensor: [in:$hasCheckInSensor, out:$hasCheckOutSensor], " +
        "trip: [in:$hasCheckInTrip, out:$hasCheckOutTrip], " +
        "expected: [checkIn:$expectedCheckInSet, checkOut:$expectedCheckOutSet]"

    private fun createReservation(now: Instant = Clock.System.now()): Reservation {
        val guest = listOf("guest-1")
        return when (reservationState) {
            ReservationTestState.NOT_STARTED -> Reservation(
                id = UUID.randomUUID().toString(),
                startTime = now.plus(7.days),
                endTime = now.plus(14.days),
                guestIds = guest,
            )

            ReservationTestState.ACTIVE -> Reservation(
                id = UUID.randomUUID().toString(),
                startTime = now.minus(1.days),
                endTime = now.plus(6.days),
                guestIds = guest,
            )

            ReservationTestState.ENDED -> Reservation(
                id = UUID.randomUUID().toString(),
                startTime = now.minus(7.days),
                endTime = now.minus(1.days),
                guestIds = guest,
            )
        }
    }

    private fun createCheckInSensor(): CheckInOutSensor? = if (hasCheckInSensor) {
        CheckInOutSensor(
            id = UUID.randomUUID().toString(),
            time = reservation.startTime.plus(10.minutes),
            status = CheckInStatus.CHECKED_IN,
        )
    } else {
        null
    }

    private fun createCheckOutSensor(): CheckInOutSensor? = if (hasCheckOutSensor) {
        CheckInOutSensor(
            id = UUID.randomUUID().toString(),
            time = reservation.endTime.plus(5.minutes),
            status = CheckInStatus.CHECKED_OUT,
        )
    } else {
        null
    }

    private fun createCheckInTrip(): VehicleTrip? = if (hasCheckInTrip) {
        createTrip(
            startCity = HOME_CITY_NAME,
            endCity = CABIN_CITY_NAME,
            startTime = reservation.startTime.minus(3.hours).toIsoUtcString(),
            endTime = reservation.startTime.plus(3.hours).toIsoUtcString(),
            id = UUID.randomUUID().toString(),
        )
    } else {
        null
    }

    private fun createAtCabinTrip(): VehicleTrip? = if (hasAtCabinTrip) {
        createTrip(
            startCity = CABIN_CITY_NAME,
            endCity = "Hemsedal",
            startTime = reservation.startTime.minus(4.hours).toIsoUtcString(),
            endTime = reservation.startTime.plus(5.minutes).toIsoUtcString(),
            id = UUID.randomUUID().toString(),
        )
    } else {
        null
    }

    private fun createCheckOutTrip(): VehicleTrip? = if (hasCheckOutTrip) {
        createTrip(
            startCity = CABIN_CITY_NAME,
            endCity = HOME_CITY_NAME,
            startTime = reservation.endTime.plus(5.minutes).toIsoUtcString(),
            endTime = reservation.endTime.plus(3.hours).toIsoUtcString(),
            id = UUID.randomUUID().toString(),
        )
    } else {
        null
    }
}
