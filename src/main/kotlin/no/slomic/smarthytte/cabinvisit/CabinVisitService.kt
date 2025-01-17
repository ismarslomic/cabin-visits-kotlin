package no.slomic.smarthytte.cabinvisit

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.calendar.CalendarEvent
import no.slomic.smarthytte.calendar.CalendarEventRepository
import no.slomic.smarthytte.checkin.CheckIn
import no.slomic.smarthytte.checkin.CheckInRepository
import no.slomic.smarthytte.vehicletrip.VehicleTrip
import no.slomic.smarthytte.vehicletrip.VehicleTripRepository
import no.slomic.smarthytte.vehicletrip.findCabinTripsWithExtraStops
import kotlin.time.Duration

class CabinVisitService(
    private val calendarRepository: CalendarEventRepository,
    private val checkInRepository: CheckInRepository,
    private val vehicleTripRepository: VehicleTripRepository,
) {
    private val logger: Logger = KtorSimpleLogger(CabinVisitService::class.java.name)

    fun createOrUpdateCabinVisits() {
        runBlocking {
            val allCalendarEvents: List<CalendarEvent> = calendarRepository.allEvents()
            val allCheckIns: Map<LocalDate, List<CheckIn>> =
                checkInRepository.allCheckIns().groupBy { it.date }
            val allVehicleTrips: List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
            val homeCabinTrips: List<VehicleTrip> = findCabinTripsWithExtraStops(allVehicleTrips)
            allCalendarEvents.forEach { event ->
                val cabinVisit = createCabinVisit(
                    reservation = event,
                    checkInsByDate = allCheckIns,
                    homeCabinTrips = homeCabinTrips,
                )

                logger.info("Cabin visit ${cabinVisit.reservation.start} - ${cabinVisit.reservation.end}")
            }
        }
    }

    private fun createCabinVisit(
        reservation: CalendarEvent,
        checkInsByDate: Map<LocalDate, List<CheckIn>>,
        homeCabinTrips: List<VehicleTrip>,
    ): CabinVisit {
        var checkInEvent: VisitEvent? = null
        var checkOutEvent: VisitEvent? = null
        var duration: Duration? = null

        if (reservation.hasStarted) {
            checkInEvent = createVisitEvent(
                reservation,
                checkInsByDate,
                homeCabinTrips,
                forCheckIn = true,
            )
        }

        if (reservation.hasEnded) {
            checkOutEvent = createVisitEvent(
                reservation,
                checkInsByDate,
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
        reservation: CalendarEvent,
        checkInsByDate: Map<LocalDate, List<CheckIn>>,
        homeCabinTrips: List<VehicleTrip>,
        forCheckIn: Boolean,
    ): VisitEvent {
        val checkInSensor: CheckIn?
        val tsFromCheckInSensor: Instant?
        val vehicleTrip: VehicleTrip?
        val tsFromVehicleTrip: Instant?
        val tsFromReservation: Instant

        if (forCheckIn) {
            checkInSensor = findCheckInFromSensor(checkInsByDate, reservation)
            tsFromCheckInSensor = checkInSensor?.timestamp
            vehicleTrip = findArrivalAtCabinTrip(homeCabinTrips, reservation)
            tsFromVehicleTrip = vehicleTrip?.endTimestamp
            tsFromReservation = reservation.start
        } else {
            checkInSensor = findCheckOutFromSensor(checkInsByDate, reservation)
            tsFromCheckInSensor = checkInSensor?.timestamp
            vehicleTrip = findDepartureFromCabinTrip(homeCabinTrips, reservation)
            tsFromVehicleTrip = vehicleTrip?.startTimestamp
            tsFromReservation = reservation.end
        }

        return if (vehicleTrip != null && tsFromVehicleTrip != null) {
            VisitEvent(
                timestamp = tsFromVehicleTrip,
                sourceName = EventSource.VEHICLE_TRIP,
                sourceId = vehicleTrip.id,
            )
        } else if (checkInSensor != null && tsFromCheckInSensor != null) {
            VisitEvent(
                timestamp = tsFromCheckInSensor,
                sourceName = EventSource.CHECK_IN_SENSOR,
                sourceId = checkInSensor.id,
            )
        } else {
            VisitEvent(
                timestamp = tsFromReservation,
                sourceName = EventSource.CALENDAR_EVENT,
                sourceId = reservation.id,
            )
        }
    }

    private fun findArrivalAtCabinTrip(homeCabinTrips: List<VehicleTrip>, event: CalendarEvent): VehicleTrip? {
        val eventStartDate: LocalDate = event.startDate
        val checkInTrip: VehicleTrip? = homeCabinTrips.firstOrNull { it.hasArrivedCabinAt(eventStartDate) }

        return checkInTrip
    }

    private fun findDepartureFromCabinTrip(homeCabinTrips: List<VehicleTrip>, event: CalendarEvent): VehicleTrip? {
        val eventEndDate: LocalDate = event.endDate
        val checkOutTrip: VehicleTrip? = homeCabinTrips.firstOrNull { it.hasDepartedCabinAt(eventEndDate) }

        return checkOutTrip
    }

    private fun findCheckInFromSensor(checkIns: Map<LocalDate, List<CheckIn>>, event: CalendarEvent): CheckIn? {
        val startDate: LocalDate = event.startDate
        val startCheckIns: List<CheckIn>? = checkIns[startDate]

        return startCheckIns?.firstOrNull { it.isCheckedIn }
    }

    private fun findCheckOutFromSensor(checkIns: Map<LocalDate, List<CheckIn>>, event: CalendarEvent): CheckIn? {
        val endDate: LocalDate = event.endDate
        val endCheckIns: List<CheckIn>? = checkIns[endDate]

        return endCheckIns?.firstOrNull { it.isCheckedOut }
    }
}
