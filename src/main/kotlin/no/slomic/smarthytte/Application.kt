package no.slomic.smarthytte

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import no.slomic.smarthytte.cabinvisit.CabinVisitService
import no.slomic.smarthytte.calendarevents.GoogleCalendarRepository
import no.slomic.smarthytte.calendarevents.GoogleCalendarService
import no.slomic.smarthytte.calendarevents.SqliteGoogleCalendarRepository
import no.slomic.smarthytte.calendarevents.createGoogleCalendarService
import no.slomic.smarthytte.calendarevents.launchSyncGoogleCalendarTask
import no.slomic.smarthytte.guest.GuestRepository
import no.slomic.smarthytte.guest.SqliteGuestRepository
import no.slomic.smarthytte.guest.insertGuestsFromFile
import no.slomic.smarthytte.plugins.configureDatabases
import no.slomic.smarthytte.plugins.configureMonitoring
import no.slomic.smarthytte.plugins.configureRouting
import no.slomic.smarthytte.properties.KtorPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorService
import no.slomic.smarthytte.sensors.checkinouts.SqliteCheckInOutSensorRepository
import no.slomic.smarthytte.sensors.checkinouts.createCheckInOutSensorService
import no.slomic.smarthytte.sensors.checkinouts.launchSyncCheckInOutSensorTask
import no.slomic.smarthytte.vehicletrip.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrip.VehicleTripRepository
import no.slomic.smarthytte.vehicletrip.insertVehicleTripsFromFile

fun main() {
    val ktorProperties = loadProperties<KtorPropertiesHolder>().ktor

    with(ktorProperties.deployment) {
        embeddedServer(
            CIO,
            port = port,
            host = host,
            module = Application::module,
        ).start(wait = true)
    }
}

fun Application.module() {
    val reservationRepository: ReservationRepository = SqliteReservationRepository()
    val googleCalendarRepository: GoogleCalendarRepository = SqliteGoogleCalendarRepository()
    val guestRepository: GuestRepository = SqliteGuestRepository()
    val googleCalendarService: GoogleCalendarService =
        createGoogleCalendarService(
            reservationRepository = reservationRepository,
            googleCalendarRepository = googleCalendarRepository,
        )
    val vehicleTripRepository: VehicleTripRepository = SqliteVehicleTripRepository()
    val checkInOutSensorRepository: CheckInOutSensorRepository = SqliteCheckInOutSensorRepository()
    val checkInOutSensorService: CheckInOutSensorService = createCheckInOutSensorService(checkInOutSensorRepository)
    val cabinVisitService = CabinVisitService(
        calendarRepository = reservationRepository,
        checkInOutSensorRepository = checkInOutSensorRepository,
        vehicleTripRepository = vehicleTripRepository,
    )

    configureMonitoring()
    configureRouting()
    configureDatabases()
    insertGuestsFromFile(guestRepository)
    insertVehicleTripsFromFile(vehicleTripRepository)
    launchSyncGoogleCalendarTask(googleCalendarService)
    launchSyncCheckInOutSensorTask(checkInOutSensorService)
    cabinVisitService.createOrUpdateCabinVisits()
}
