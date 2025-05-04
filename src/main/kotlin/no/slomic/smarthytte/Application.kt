package no.slomic.smarthytte

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import no.slomic.smarthytte.calendarevents.GoogleCalendarService
import no.slomic.smarthytte.checkinouts.CheckInOutService
import no.slomic.smarthytte.guests.GuestService
import no.slomic.smarthytte.guests.SqliteGuestRepository
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
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTripService
import java.util.*

fun main() {
    // Set UTC as the default timezone for the entire app to avoid inconsistency between the local dev environment
    // and the Docker runtime.
    // Exposed v0.59 introduced some bugs with handling timezone for timestamp types,
    // see https://youtrack.jetbrains.com/issue/EXPOSED-731/Timestamp-support-for-SQLite-is-broken
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

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
    // Initialize database
    configureDatabases()

    // Initialize monitoring
    configureMonitoring()

    // Initialize repositories
    val syncCheckpointRepository: SyncCheckpointRepository = SqliteSyncCheckpointRepository()
    val reservationRepository: ReservationRepository = SqliteReservationRepository()
    val vehicleTripRepository: VehicleTripRepository = SqliteVehicleTripRepository()
    val checkInOutSensorRepository: CheckInOutSensorRepository = SqliteCheckInOutSensorRepository()

    // Initialize services
    val syncCheckpointService = SyncCheckpointService(syncCheckpointRepository)
    val vehicleTripService = VehicleTripService(vehicleTripRepository)
    val googleCalendarService = GoogleCalendarService(reservationRepository, syncCheckpointService)
    val checkInOutSensorService = CheckInOutSensorService(checkInOutSensorRepository, syncCheckpointService)
    val checkInOutService = CheckInOutService(
        reservationRepository = reservationRepository,
        checkInOutSensorRepository = checkInOutSensorRepository,
        vehicleTripRepository = vehicleTripRepository,
    )

    // Run initial load BEFORE starting to handle requests and running synchronization processes in the background
    runBlocking {
        initialLoad(
            vehicleTripService,
            checkInOutSensorService,
            googleCalendarService,
            checkInOutService,
        )
    }

    // Fetch reservations, check in/out sensor data and update check in/out status continuously in the background
    // launchSyncGoogleCalendarTask(googleCalendarService)
    // NOTE!: the method below reads all reservations and updates their check in/out status.
    // checkInOutService.updateCheckInOutStatusForAllReservations()

    // Configure Ktor routing (after the initial load is completed)
    configureRouting()
}

/***
 * Initial load of historical data from a file or external storage.
 *
 * Loads historical data for
 * * guests from a file
 * * vehicle trips from a file
 * * check in/out sensor data from InfluxDb
 * * reservations from Google Calendar and join these with loaded guests
 * * updates the check in/out status of reservations by using check in/out sensor data and vehicle trips
 *
 */
suspend fun Application.initialLoad(
    vehicleTripService: VehicleTripService,
    checkInOutSensorService: CheckInOutSensorService,
    googleCalendarService: GoogleCalendarService,
    checkInOutService: CheckInOutService,
) {
    log.info("Starting initial load...")

    val guestService = GuestService(guestRepository = SqliteGuestRepository())

    // The following data are decoupled from each other and could potentially be parallelized
    guestService.insertGuestsFromFile()
    vehicleTripService.insertVehicleTripsFromFile()
    checkInOutSensorService.fetchCheckInOut()

    // The following data must be loaded after previous steps since they depend on them.
    // These steps must also be executed in sequence
    googleCalendarService.fetchGoogleCalendarEvents()
    checkInOutService.updateCheckInOutStatusForAllReservations()

    log.info("Initial load complete!")
}
