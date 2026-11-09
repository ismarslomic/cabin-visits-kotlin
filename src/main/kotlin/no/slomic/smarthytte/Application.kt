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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.configureAuthRoutes
import no.slomic.smarthytte.auth.configureAuthentication
import no.slomic.smarthytte.auth.toConfig
import no.slomic.smarthytte.checkinouts.CheckInOutService
import no.slomic.smarthytte.common.runCatchingLogged
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.guests.configureGuestRoutes
import no.slomic.smarthytte.plugins.HttpClientProvider
import no.slomic.smarthytte.plugins.configureDatabases
import no.slomic.smarthytte.plugins.configureMonitoring
import no.slomic.smarthytte.plugins.configureRouting
import no.slomic.smarthytte.properties.ApiKeyPropertiesHolder
import no.slomic.smarthytte.properties.CheckInProperties
import no.slomic.smarthytte.properties.CloudflareAccessPropertiesHolder
import no.slomic.smarthytte.properties.InfluxDbProperties
import no.slomic.smarthytte.properties.InfluxDbPropertiesHolder
import no.slomic.smarthytte.properties.KtorPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorService
import no.slomic.smarthytte.sensors.checkinouts.SqliteCheckInOutSensorRepository
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.statistics.configureOpenApi
import no.slomic.smarthytte.statistics.configureStatsRoutes
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTripService
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
    val checkInOutSensorService = CheckInOutSensorService(checkInOutSensorRepository, syncCheckpointService)
    val checkInOutService = CheckInOutService(
        reservationRepository = reservationRepository,
        checkInOutSensorRepository = checkInOutSensorRepository,
        vehicleTripRepository = vehicleTripRepository,
    )
    val statsService = StatsService(reservationRepository, guestRepository)
    val appUserService = AppUserService(guestRepository)
    val guestProfileService = GuestProfileService(guestRepository)
    val guestAdminService = GuestAdminService(guestRepository)

    // Run initial load BEFORE starting to handle requests and running synchronization processes in the background
    runBlocking {
        initialLoad(
            vehicleTripService,
            checkInOutSensorService,
            checkInOutService,
        )
    }

    // 🔁 Start background sync after the initial load
    startBackgroundSync(
        reservationRepository,
        vehicleTripService,
        checkInOutSensorService,
        checkInOutService,
    )

    // Configure Ktor routing (after the initial load is completed)
    val cloudflareAccess = loadProperties<CloudflareAccessPropertiesHolder>().cloudflareAccess.toConfig()
    val apiKeys = loadProperties<ApiKeyPropertiesHolder>().apiKey.toConfig()
    configureApiRoutes(
        ApiDependencies(
            guestRepository = guestRepository,
            guestProfileService = guestProfileService,
            guestAdminService = guestAdminService,
            statsService = statsService,
            appUserService = appUserService,
            cloudflareAccess = cloudflareAccess,
            apiKeys = apiKeys,
        ),
    )
}

/**
 * Installs every HTTP route group served by the application, except monitoring ([configureMonitoring]).
 *
 * Shared with `OpenApiSpecTest`, so the committed OpenAPI spec is generated from the same routes that production
 * serves. Add new route groups here, not directly in [module].
 */
fun Application.configureApiRoutes(dependencies: ApiDependencies) {
    configureRouting()
    configureAuthentication(dependencies.cloudflareAccess, dependencies.appUserService, dependencies.apiKeys)
    configureAuthRoutes(dependencies.guestProfileService, dependencies.guestRepository)
    configureGuestRoutes(
        dependencies.guestRepository,
        dependencies.guestProfileService,
        dependencies.guestAdminService,
    )
    configureStatsRoutes(dependencies.statsService)
    configureOpenApi()
}

/***
 * Initial load of historical data from a file or external storage.
 *
 * Loads historical data for
 * * vehicle trips from a file
 * * check in/out sensor data from InfluxDb
 * * updates the check in/out status of reservations by using check in/out sensor data and vehicle trips
 *
 */
suspend fun Application.initialLoad(
    vehicleTripService: VehicleTripService,
    checkInOutSensorService: CheckInOutSensorService,
    checkInOutService: CheckInOutService,
) {
    log.info("Starting initial load...")

    // The following data are decoupled from each other and could potentially be parallelized
    vehicleTripService.insertVehicleTripsFromFile()
    checkInOutSensorService.fetchCheckInOut()

    // The following data must be loaded after previous steps since they depend on them.
    checkInOutService.updateCheckInOutStatusForAllReservations()

    log.info("Initial load complete!")
}

fun Application.startBackgroundSync(
    reservationRepository: ReservationRepository,
    vehicleTripService: VehicleTripService,
    checkInOutSensorService: CheckInOutSensorService,
    checkInOutService: CheckInOutService,
) {
    val influxdbProperties: InfluxDbProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb
    val checkInOutSensorProperties: CheckInProperties = influxdbProperties.checkIn
    val checkInOutSyncFrequency: Duration = checkInOutSensorProperties.syncFrequencyMinutes.minutes

    val schedulerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    log.info("Launching background sync tasks.")

    monitor.subscribe(ApplicationStopped) {
        log.info("Stopping background sync tasks.")
        schedulerScope.cancel()
    }

    // Fetch vehicle trips and check-in/out sensor data and update the check-in / out status for all reservations
    // (sync task).
    // This task is scheduled only within the daytime and during the reservation window (between from and end time),
    // to reduce unnecessary data fetching from the external sources outside the reservation window.
    schedulerScope.launch {
        while (isActive) {
            runCatchingLogged("Background sync") {
                if (isDaytime() && isWithinReservationWindow(reservationRepository)) {
                    runCatchingLogged("Fetch vehicle trips") { vehicleTripService.fetchVehicleTrips() }
                    runCatchingLogged("Fetch check in/out sensor data") { checkInOutSensorService.fetchCheckInOut() }
                    runCatchingLogged("Update check in/out status") {
                        checkInOutService.updateCheckInOutStatusForAllReservations()
                    }
                }
            }
            delay(duration = checkInOutSyncFrequency)
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
