package no.slomic.smarthytte.checkinouts

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.ReservationVehicleTripType
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensor
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorRepository
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import kotlin.time.Instant

class CheckInOutService(
    private val reservationRepository: ReservationRepository,
    private val checkInOutSensorRepository: CheckInOutSensorRepository,
    private val vehicleTripRepository: VehicleTripRepository,
) {
    private val logger: Logger = KtorSimpleLogger(CheckInOutService::class.java.name)

    /**
     * Updates the check-in and check-out status for all reservations.
     *
     * For each reservation in the system:
     * - If the reservation has started, attempts to create and persist a check-in entry.
     * - If the reservation has ended, attempts to create and persist a check-out entry.
     * For both actions, tracks and logs the results (updated or no action).
     *
     * The process uses data from the reservation repository, check-in/out sensors, and vehicle trips.
     *
     * This function is intended for system-wide status synchronization,
     * such as a scheduled job or maintenance action.
     *
     * @see ReservationRepository.allReservations
     * @see CheckInOutSensorRepository.allCheckInOuts
     * @see VehicleTripRepository.allVehicleTrips
     */
    suspend fun updateCheckInOutStatusForAllReservations() {
        logger.info("Updating check in/out status for all reservations...")

        val allReservations: List<Reservation> = reservationRepository.allReservations()
        val checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>> =
            checkInOutSensorRepository.allCheckInOuts().groupBy { it.date }
        val allVehicleTrips: List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
        val cabinVehicleTripList: List<CabinVehicleTrip> = CabinVehicleTripList(allVehicleTrips).cabinTrips

        val checkInPersistenceResults: MutableList<PersistenceResult> = mutableListOf()
        val checkOutPersistenceResults: MutableList<PersistenceResult> = mutableListOf()
        allReservations.forEach { reservation ->
            val cabinVehicleTrip: CabinVehicleTrip? = findCabinTrip(cabinVehicleTripList, reservation)

            // Link all trips associated with this cabin visit to the reservation
            cabinVehicleTrip?.let { cvt ->
                cvt.toCabinTrips.forEach {
                    reservationRepository.addVehicleTripLink(reservation.id, it.id, ReservationVehicleTripType.TO_CABIN)
                }
                cvt.atCabinTrips.forEach {
                    reservationRepository.addVehicleTripLink(reservation.id, it.id, ReservationVehicleTripType.AT_CABIN)
                }
                cvt.fromCabinTrips.forEach {
                    reservationRepository.addVehicleTripLink(
                        reservation.id,
                        it.id,
                        ReservationVehicleTripType.FROM_CABIN,
                    )
                }
            }

            val checkIn: CheckIn? = if (reservation.hasStarted) {
                createCheckIn(reservation, checkInOutSensorsByDate, cabinVehicleTripList)
            } else {
                null
            }

            val checkOut: CheckOut? = if (reservation.hasEnded) {
                createCheckOut(reservation, checkInOutSensorsByDate, cabinVehicleTripList)
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
                "check in updated: $checkInUpdatedCount, check in no actions: $checkInNoActionCount, " +
                "check out updated: $checkOutUpdatedCount, check out no actions: $checkOutNoActionCount",
        )
    }

    private fun createCheckIn(
        reservation: Reservation,
        checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>>,
        cabinVehicleTrips: List<CabinVehicleTrip>,
    ): CheckIn {
        val cabinVehicleTrip: CabinVehicleTrip? = findArrivalAtCabinTrip(cabinVehicleTrips, reservation)
        val checkInOutSensor: CheckInOutSensor? = findCheckInFromSensor(checkInOutSensorsByDate, reservation)
        val timeFromCheckInSensor: Instant? = checkInOutSensor?.time
        val timeFromVehicleTrip: Instant? = cabinVehicleTrip?.toCabinEndTimestamp
        val timeFromReservation: Instant = reservation.startTime

        return if (cabinVehicleTrip != null && timeFromVehicleTrip != null) {
            CheckIn(
                time = timeFromVehicleTrip,
                sourceName = CheckInOutSource.VEHICLE_TRIP,
                sourceId = cabinVehicleTrip.toCabinTripId!!,
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
        cabinVehicleTrips: List<CabinVehicleTrip>,
    ): CheckOut {
        val cabinVehicleTrip: CabinVehicleTrip? = findDepartureFromCabinTrip(cabinVehicleTrips, reservation)
        val checkInOutSensor: CheckInOutSensor? = findCheckOutFromSensor(checkInOutSensorsByDate, reservation)
        val timeFromCheckInSensor: Instant? = checkInOutSensor?.time
        val timeFromVehicleTrip: Instant? = cabinVehicleTrip?.fromCabinStartTimestamp
        val timeFromReservation: Instant = reservation.endTime

        return if (cabinVehicleTrip != null && timeFromVehicleTrip != null) {
            CheckOut(
                time = timeFromVehicleTrip,
                sourceName = CheckInOutSource.VEHICLE_TRIP,
                sourceId = cabinVehicleTrip.fromCabinTripId!!,
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

    private fun findArrivalAtCabinTrip(
        cabinVehicleTrips: List<CabinVehicleTrip>,
        reservation: Reservation,
    ): CabinVehicleTrip? {
        val reservationStartDate: LocalDate = reservation.startDate
        val checkInTrip: CabinVehicleTrip? =
            cabinVehicleTrips.firstOrNull { it.hasArrivedCabinAt(reservationStartDate) }

        return checkInTrip
    }

    private fun findDepartureFromCabinTrip(
        cabinVehicleTrips: List<CabinVehicleTrip>,
        reservation: Reservation,
    ): CabinVehicleTrip? {
        val reservationEndDate: LocalDate = reservation.endDate
        val checkOutTrip: CabinVehicleTrip? =
            cabinVehicleTrips.firstOrNull { it.hasDepartedCabinAt(reservationEndDate) }

        return checkOutTrip
    }

    private fun findCabinTrip(cabinVehicleTrips: List<CabinVehicleTrip>, reservation: Reservation): CabinVehicleTrip? {
        val cabinVehicleTrip: CabinVehicleTrip? = cabinVehicleTrips.firstOrNull {
            it.hasArrivedCabinAt(reservation.startDate) || it.hasDepartedCabinAt(reservation.endDate)
        }

        return cabinVehicleTrip
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
