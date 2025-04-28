package no.slomic.smarthytte.checkinouts

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.PersistenceResult
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
    private val logger: Logger = KtorSimpleLogger(CheckInOutService::class.java.name)

    suspend fun updateCheckInOutStatusForAllReservations() {
        logger.info("Updating check in/out status for all reservations...")

        val allReservations: List<Reservation> = reservationRepository.allReservations()
        val checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>> =
            checkInOutSensorRepository.allCheckInOuts().groupBy { it.date }
        val allVehicleTrips: List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
        val homeCabinTrips: List<VehicleTrip> = findCabinTripsWithExtraStops(allVehicleTrips)

        val checkInPersistenceResults: MutableList<PersistenceResult> = mutableListOf()
        val checkOutPersistenceResults: MutableList<PersistenceResult> = mutableListOf()
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
                checkInPersistenceResults.add(reservationRepository.setCheckIn(checkIn, reservation.id))
            } else {
                checkInPersistenceResults.add(PersistenceResult.NO_ACTION)
            }

            if (checkOut != null) {
                checkOutPersistenceResults.add(reservationRepository.setCheckOut(checkOut, reservation.id))
            } else {
                checkOutPersistenceResults.add(PersistenceResult.NO_ACTION)
            }
        }

        val checkInUpdatedCount = checkInPersistenceResults.count { it == PersistenceResult.UPDATED }
        val checkInNoActionCount = checkInPersistenceResults.count { it == PersistenceResult.NO_ACTION }
        val checkOutUpdatedCount = checkOutPersistenceResults.count { it == PersistenceResult.UPDATED }
        val checkOutNoActionCount = checkOutPersistenceResults.count { it == PersistenceResult.NO_ACTION }
        logger.info(
            "Updating check in/out status for all reservations complete. " +
                "Total reservations in db: ${allReservations.size}, " +
                "check in updated: $checkInUpdatedCount, check in no actions: $checkInNoActionCount ," +
                "check out updated: $checkOutUpdatedCount, check out no actions: $checkOutNoActionCount",
        )
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

    private fun findArrivalAtCabinTrip(homeCabinTrips: List<VehicleTrip>, reservation: Reservation): VehicleTrip? {
        val reservationStartDate: LocalDate = reservation.startDate
        val checkInTrip: VehicleTrip? = homeCabinTrips.firstOrNull { it.hasArrivedCabinAt(reservationStartDate) }

        return checkInTrip
    }

    private fun findDepartureFromCabinTrip(homeCabinTrips: List<VehicleTrip>, reservation: Reservation): VehicleTrip? {
        val reservationEndDate: LocalDate = reservation.endDate
        val checkOutTrip: VehicleTrip? = homeCabinTrips.firstOrNull { it.hasDepartedCabinAt(reservationEndDate) }

        return checkOutTrip
    }

    private fun findCheckInFromSensor(
        checkInOutSensors: Map<LocalDate, List<CheckInOutSensor>>,
        reservation: Reservation,
    ): CheckInOutSensor? {
        val startDate: LocalDate = reservation.startDate
        val startCheckInOutSensors: List<CheckInOutSensor>? = checkInOutSensors[startDate]

        return startCheckInOutSensors?.firstOrNull { it.isCheckedIn }
    }

    private fun findCheckOutFromSensor(
        checkInOutSensors: Map<LocalDate, List<CheckInOutSensor>>,
        reservation: Reservation,
    ): CheckInOutSensor? {
        val endDate: LocalDate = reservation.endDate
        val endCheckInOutSensors: List<CheckInOutSensor>? = checkInOutSensors[endDate]

        return endCheckInOutSensors?.firstOrNull { it.isCheckedOut }
    }
}
