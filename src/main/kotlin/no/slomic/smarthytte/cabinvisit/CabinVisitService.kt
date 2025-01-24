package no.slomic.smarthytte.cabinvisit

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensor
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorRepository
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import no.slomic.smarthytte.vehicletrips.findCabinTripsWithExtraStops
import kotlin.time.Duration

class CabinVisitService(
    private val calendarRepository: ReservationRepository,
    private val checkInOutSensorRepository: CheckInOutSensorRepository,
    private val vehicleTripRepository: VehicleTripRepository,
) {
    private val logger: Logger = KtorSimpleLogger(CabinVisitService::class.java.name)

    fun createOrUpdateCabinVisits() {
        runBlocking {
            val allReservations: List<Reservation> = calendarRepository.allReservations()
            val allCheckInOutSensors: Map<LocalDate, List<CheckInOutSensor>> =
                checkInOutSensorRepository.allCheckInOuts().groupBy { it.date }
            val allVehicleTrips: List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
            val homeCabinTrips: List<VehicleTrip> = findCabinTripsWithExtraStops(allVehicleTrips)
            allReservations.forEach { event ->
                val cabinVisit = createCabinVisit(
                    reservation = event,
                    checkInOutSensorsByDate = allCheckInOutSensors,
                    homeCabinTrips = homeCabinTrips,
                )

                logger.info("Cabin visit ${cabinVisit.reservation.startTime} - ${cabinVisit.reservation.endTime}")
            }
        }
    }

    private fun createCabinVisit(
        reservation: Reservation,
        checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>>,
        homeCabinTrips: List<VehicleTrip>,
    ): CabinVisit {
        var checkInEvent: VisitEvent? = null
        var checkOutEvent: VisitEvent? = null
        var duration: Duration? = null

        if (reservation.hasStarted) {
            checkInEvent = createVisitEvent(
                reservation,
                checkInOutSensorsByDate,
                homeCabinTrips,
                forCheckIn = true,
            )
        }

        if (reservation.hasEnded) {
            checkOutEvent = createVisitEvent(
                reservation,
                checkInOutSensorsByDate,
                homeCabinTrips,
                forCheckIn = false,
            )
        }

        if (checkInEvent != null && checkOutEvent != null) {
            duration = checkOutEvent.timestamp - checkInEvent.timestamp
        }

        return CabinVisit(
            reservation = reservation,
            checkIn = checkInEvent,
            checkOut = checkOutEvent,
            duration = duration,
        )
    }

    private fun createVisitEvent(
        reservation: Reservation,
        checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>>,
        homeCabinTrips: List<VehicleTrip>,
        forCheckIn: Boolean,
    ): VisitEvent {
        val checkInOutSensor: CheckInOutSensor?
        val timeFromCheckInSensor: Instant?
        val vehicleTrip: VehicleTrip?
        val timeFromVehicleTrip: Instant?
        val timeFromReservation: Instant

        if (forCheckIn) {
            checkInOutSensor = findCheckInFromSensor(checkInOutSensorsByDate, reservation)
            timeFromCheckInSensor = checkInOutSensor?.time
            vehicleTrip = findArrivalAtCabinTrip(homeCabinTrips, reservation)
            timeFromVehicleTrip = vehicleTrip?.endTime
            timeFromReservation = reservation.startTime
        } else {
            checkInOutSensor = findCheckOutFromSensor(checkInOutSensorsByDate, reservation)
            timeFromCheckInSensor = checkInOutSensor?.time
            vehicleTrip = findDepartureFromCabinTrip(homeCabinTrips, reservation)
            timeFromVehicleTrip = vehicleTrip?.startTime
            timeFromReservation = reservation.endTime
        }

        return if (vehicleTrip != null && timeFromVehicleTrip != null) {
            VisitEvent(
                timestamp = timeFromVehicleTrip,
                sourceName = EventSource.VEHICLE_TRIP,
                sourceId = vehicleTrip.id,
            )
        } else if (checkInOutSensor != null && timeFromCheckInSensor != null) {
            VisitEvent(
                timestamp = timeFromCheckInSensor,
                sourceName = EventSource.CHECK_IN_SENSOR,
                sourceId = checkInOutSensor.id,
            )
        } else {
            VisitEvent(
                timestamp = timeFromReservation,
                sourceName = EventSource.CALENDAR_EVENT,
                sourceId = reservation.id,
            )
        }
    }

    private fun findArrivalAtCabinTrip(homeCabinTrips: List<VehicleTrip>, event: Reservation): VehicleTrip? {
        val eventStartDate: LocalDate = event.startDate
        val checkInTrip: VehicleTrip? = homeCabinTrips.firstOrNull { it.hasArrivedCabinAt(eventStartDate) }

        return checkInTrip
    }

    private fun findDepartureFromCabinTrip(homeCabinTrips: List<VehicleTrip>, event: Reservation): VehicleTrip? {
        val eventEndDate: LocalDate = event.endDate
        val checkOutTrip: VehicleTrip? = homeCabinTrips.firstOrNull { it.hasDepartedCabinAt(eventEndDate) }

        return checkOutTrip
    }

    private fun findCheckInFromSensor(
        checkInOutSensors: Map<LocalDate, List<CheckInOutSensor>>,
        event: Reservation,
    ): CheckInOutSensor? {
        val startDate: LocalDate = event.startDate
        val startCheckInOutSensors: List<CheckInOutSensor>? = checkInOutSensors[startDate]

        return startCheckInOutSensors?.firstOrNull { it.isCheckedIn }
    }

    private fun findCheckOutFromSensor(
        checkInOutSensors: Map<LocalDate, List<CheckInOutSensor>>,
        event: Reservation,
    ): CheckInOutSensor? {
        val endDate: LocalDate = event.endDate
        val endCheckInOutSensors: List<CheckInOutSensor>? = checkInOutSensors[endDate]

        return endCheckInOutSensors?.firstOrNull { it.isCheckedOut }
    }
}
