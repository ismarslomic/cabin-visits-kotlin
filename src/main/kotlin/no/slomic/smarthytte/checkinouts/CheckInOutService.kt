package no.slomic.smarthytte.checkinouts

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.runCatchingLogged
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationSystemService
import no.slomic.smarthytte.reservations.ReservationVehicleTripType
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensor
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorQueryService
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import no.slomic.smarthytte.vehicletrips.VehicleTripQueryService
import kotlin.time.Instant

class CheckInOutService(
    private val reservationService: ReservationSystemService,
    private val checkInOutSensorQueryService: CheckInOutSensorQueryService,
    private val vehicleTripQueryService: VehicleTripQueryService,
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
     * The process uses data from the reservations, check-in/out sensors, and vehicle trips.
     *
     * This function is intended for system-wide status synchronization,
     * such as a scheduled job or maintenance action.
     *
     * @see ReservationSystemService.all
     * @see CheckInOutSensorQueryService.allCheckInOuts
     * @see VehicleTripQueryService.allVehicleTrips
     */
    suspend fun updateCheckInOutStatusForAllReservations() {
        logger.info("Updating check in/out status for all reservations...")

        val allReservations: List<Reservation> = reservationService.all()
        val checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>> =
            checkInOutSensorQueryService.allCheckInOuts().groupBy { it.date }
        val allVehicleTrips: List<VehicleTrip> = vehicleTripQueryService.allVehicleTrips()
        val cabinVehicleTripList: List<CabinVehicleTrip> = CabinVehicleTripList(allVehicleTrips).cabinTrips

        val checkInPersistenceResults: MutableList<PersistenceResult> = mutableListOf()
        val checkOutPersistenceResults: MutableList<PersistenceResult> = mutableListOf()
        allReservations.forEach { reservation ->
            // One failing reservation must not abort the rest of the run
            runCatchingLogged("Updating check in/out status of reservation ${reservation.id}", logger) {
                val (checkInResult, checkOutResult) =
                    updateReservation(reservation, checkInOutSensorsByDate, cabinVehicleTripList)
                checkInPersistenceResults.add(checkInResult)
                checkOutPersistenceResults.add(checkOutResult)
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

    /**
     * Derives the trip links, check-in and check-out of one reservation, for example after the API created it or
     * moved its times (the all-reservations job only runs inside the reservation window). Does nothing if the
     * reservation does not exist.
     */
    suspend fun updateCheckInOutStatusForReservation(reservationId: String) {
        val reservation: Reservation = reservationService.byId(reservationId) ?: return
        val checkInOutSensorsByDate = checkInOutSensorQueryService.allCheckInOuts().groupBy { it.date }
        val cabinVehicleTripList = CabinVehicleTripList(vehicleTripQueryService.allVehicleTrips()).cabinTrips

        updateReservation(reservation, checkInOutSensorsByDate, cabinVehicleTripList)
    }

    /** Links the trips and sets check-in and check-out of one reservation; returns the two persistence results. */
    private suspend fun updateReservation(
        reservation: Reservation,
        checkInOutSensorsByDate: Map<LocalDate, List<CheckInOutSensor>>,
        cabinVehicleTripList: List<CabinVehicleTrip>,
    ): Pair<PersistenceResult, PersistenceResult> {
        // Link all trips associated with this cabin visit to the reservation
        findCabinTrip(cabinVehicleTripList, reservation)?.let { linkTrips(reservation, it) }

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

        val checkInResult = checkIn?.let {
            reservationService.setCheckIn(it, reservation.id, reservation.startTime, reservation.endTime)
        } ?: PersistenceResult.NO_ACTION
        val checkOutResult = checkOut?.let {
            reservationService.setCheckOut(it, reservation.id, reservation.startTime, reservation.endTime)
        } ?: PersistenceResult.NO_ACTION

        return checkInResult to checkOutResult
    }

    /** The snapshot times make a concurrent time change or delete of the reservation skip the link. */
    private suspend fun linkTrips(reservation: Reservation, cabinVehicleTrip: CabinVehicleTrip) {
        val tripsByType: Map<ReservationVehicleTripType, List<VehicleTrip>> = mapOf(
            ReservationVehicleTripType.TO_CABIN to cabinVehicleTrip.toCabinTrips,
            ReservationVehicleTripType.AT_CABIN to cabinVehicleTrip.atCabinTrips,
            ReservationVehicleTripType.FROM_CABIN to cabinVehicleTrip.fromCabinTrips,
        )

        tripsByType.forEach { (type, trips) ->
            trips.forEach {
                reservationService.addVehicleTripLink(
                    reservation.id,
                    it.id,
                    type,
                    reservation.startTime,
                    reservation.endTime,
                )
            }
        }
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
