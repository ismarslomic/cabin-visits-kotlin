package no.slomic.smarthytte

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.calendarevents.GoogleCalendarService
import no.slomic.smarthytte.checkinouts.CheckInOutService
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.guests.GuestService
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.plugins.HttpClientProvider
import no.slomic.smarthytte.plugins.configureDatabases
import no.slomic.smarthytte.plugins.configureGraphQL
import no.slomic.smarthytte.plugins.configureMonitoring
import no.slomic.smarthytte.plugins.configureRouting
import no.slomic.smarthytte.properties.GoogleCalendarProperties
import no.slomic.smarthytte.properties.GoogleCalendarPropertiesHolder
import no.slomic.smarthytte.properties.KtorPropertiesHolder
import no.slomic.smarthytte.properties.VehicleTripProperties
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

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

    // Create the Http client
    val httpClient = HttpClientProvider.client
    monitor.subscribe(ApplicationStopped) {
        log.info("Closing the HttpClient.")
        httpClient.close()
    }

    // Initialize repositories
    val syncCheckpointRepository: SyncCheckpointRepository = SqliteSyncCheckpointRepository()
    val reservationRepository: ReservationRepository = SqliteReservationRepository()
    val vehicleTripRepository: VehicleTripRepository = SqliteVehicleTripRepository()
    val checkInOutSensorRepository: CheckInOutSensorRepository = SqliteCheckInOutSensorRepository()
    val guestRepository: GuestRepository = SqliteGuestRepository()

    // Initialize services
    val syncCheckpointService = SyncCheckpointService(syncCheckpointRepository)
    val vehicleTripService = VehicleTripService(vehicleTripRepository, syncCheckpointService, httpClient)
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
            guestRepository,
        )
    }

    // 🔁 Start background sync after the initial load
    startBackgroundSync(
        googleCalendarService,
        reservationRepository,
        vehicleTripService,
        checkInOutSensorService,
        checkInOutService,
    )

    // Fetch reservations, check in/out sensor data and update check in/out status continuously in the background
    // launchSyncGoogleCalendarTask(googleCalendarService)
    // NOTE!: the method below reads all reservations and updates their check in/out status.
    // checkInOutService.updateCheckInOutStatusForAllReservations()

    // Configure GraphQL
    configureGraphQL(guestRepository, reservationRepository)

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
    guestRepository: GuestRepository,
) {
    log.info("Starting initial load...")

    val guestService = GuestService(guestRepository = guestRepository)

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

fun Application.startBackgroundSync(
    googleCalendarService: GoogleCalendarService,
    reservationRepository: ReservationRepository,
    vehicleTripService: VehicleTripService,
    checkInOutSensorService: CheckInOutSensorService,
    checkInOutService: CheckInOutService,
) {
    val googleProperties: GoogleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar
    val reservationSyncFrequency: Duration = googleProperties.syncFrequencyMinutes.minutes

    val vehicleTripProperties: VehicleTripProperties = loadProperties<VehicleTripPropertiesHolder>().vehicleTrip
    val vehicleTripSyncFrequency: Duration = vehicleTripProperties.syncFrequencyMinutes.minutes

    val schedulerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    log.info("Launching background sync tasks.")

    monitor.subscribe(ApplicationStopped) {
        log.info("Stopping background sync tasks.")
        schedulerScope.cancel()
    }

    // Start the scheduler to periodically fetch reservations from the Google Calendar during daytime (sync task)
    schedulerScope.launch {
        while (isActive) {
            if (isDaytime()) {
                googleCalendarService.fetchGoogleCalendarEvents()
            }
            delay(duration = reservationSyncFrequency)
        }
    }

    // After fetching data from Google Calendar, fetch vehicle trips and check-in/out sensor data and update the
    // check-in / out status for all reservations (sync task).
    // This task is scheduled only within the daytime and during the reservation window (between from and end time),
    // to reduce unnecessary data fetching from the external sources outside the reservation window.
    schedulerScope.launch {
        while (isActive) {
            if (isDaytime() && isWithinReservationWindow(reservationRepository)) {
                vehicleTripService.fetchVehicleTrips()
                checkInOutSensorService.fetchCheckInOut()
                checkInOutService.updateCheckInOutStatusForAllReservations()
            }
            delay(duration = vehicleTripSyncFrequency)
        }
    }
}

suspend fun isWithinReservationWindow(reservationRepository: ReservationRepository): Boolean {
    val now: Instant = Clock.System.now()
    val reservations = reservationRepository.allReservations()

    return reservations.any { reservation ->
        val windowStart = reservation.startTime.minus(1.days)
        val windowEnd = reservation.endTime.plus(1.days)

        now >= windowStart && now <= windowEnd
    }
}

fun isDaytime(): Boolean {
    val zone = kotlinx.datetime.TimeZone.of(System.getenv("DAYTIME_TIMEZONE") ?: "Europe/Oslo")
    val now = Clock.System.now().toLocalDateTime(zone).time

    val startTime = LocalTime.parse(System.getenv("DAYTIME_START") ?: "08:00")
    val endTime = LocalTime.parse(System.getenv("DAYTIME_END") ?: "23:00")

    return now in startTime..endTime
}
