package no.slomic.smarthytte.checkinouts

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

class CheckInOutService(
    private val reservationRepository: ReservationRepository,
    private val checkInOutSensorRepository: CheckInOutSensorRepository,
    private val vehicleTripRepository: VehicleTripRepository,
) {
    fun updateCheckInOut() {
        runBlocking {
            val allReservations: List<Reservation> = reservationRepository.allReservations()
            val checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>> =
                checkInOutSensorRepository.allCheckInOuts().groupBy { it.date }
            val allVehicleTrips: List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
            val homeCabinTrips: List<VehicleTrip> = findCabinTripsWithExtraStops(allVehicleTrips)
            allReservations.forEach { reservation ->
                val checkIn: CheckIn? = if (reservation.hasStarted) {
                    createCheckIn(reservation, checkInOutSensorsByDate, homeCabinTrips)
                } else {
                    null
                }

                val checkOut: CheckOut? = if (reservation.hasEnded) {
                    createCheckOut(
                        reservation,
                        checkInOutSensorsByDate,
                        homeCabinTrips,
                    )
                } else {
                    null
                }

                if (checkIn != null) {
                    reservationRepository.setCheckIn(checkIn, reservation.id)
                }

                if (checkOut != null) {
                    reservationRepository.setCheckOut(checkOut, reservation.id)
                }
            }
        }
    }

    private fun createCheckIn(
        reservation: Reservation,
        checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>>,
        homeCabinTrips: List<VehicleTrip>,
    ): CheckIn {
        val vehicleTrip: VehicleTrip? = findArrivalAtCabinTrip(homeCabinTrips, reservation)
        val checkInOutSensor: CheckInOutSensor? = findCheckInFromSensor(checkInOutSensorsByDate, reservation)
        val timeFromCheckInSensor: Instant? = checkInOutSensor?.time
        val timeFromVehicleTrip: Instant? = vehicleTrip?.endTime
        val timeFromReservation: Instant = reservation.startTime

        return if (vehicleTrip != null && timeFromVehicleTrip != null) {
            CheckIn(
                time = timeFromVehicleTrip,
                sourceName = CheckInOutSource.VEHICLE_TRIP,
                sourceId = vehicleTrip.id,
            )
        } else if (checkInOutSensor != null && timeFromCheckInSensor != null) {
            CheckIn(
                time = timeFromCheckInSensor,
                sourceName = CheckInOutSource.CHECK_IN_SENSOR,
                sourceId = checkInOutSensor.id,
            )
        } else {
            CheckIn(
                time = timeFromReservation,
                sourceName = CheckInOutSource.CALENDAR_EVENT,
                sourceId = reservation.id,
            )
        }
    }

    private fun createCheckOut(
        reservation: Reservation,
        checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>>,
        homeCabinTrips: List<VehicleTrip>,
    ): CheckOut {
        val vehicleTrip: VehicleTrip? = findDepartureFromCabinTrip(homeCabinTrips, reservation)
        val checkInOutSensor: CheckInOutSensor? = findCheckOutFromSensor(checkInOutSensorsByDate, reservation)
        val timeFromCheckInSensor: Instant? = checkInOutSensor?.time
        val timeFromVehicleTrip: Instant? = vehicleTrip?.startTime
        val timeFromReservation: Instant = reservation.endTime

        return if (vehicleTrip != null && timeFromVehicleTrip != null) {
            CheckOut(
                time = timeFromVehicleTrip,
                sourceName = CheckInOutSource.VEHICLE_TRIP,
                sourceId = vehicleTrip.id,
            )
        } else if (checkInOutSensor != null && timeFromCheckInSensor != null) {
            CheckOut(
                time = timeFromCheckInSensor,
                sourceName = CheckInOutSource.CHECK_IN_SENSOR,
                sourceId = checkInOutSensor.id,
            )
        } else {
            CheckOut(
                time = timeFromReservation,
                sourceName = CheckInOutSource.CALENDAR_EVENT,
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
