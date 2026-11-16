package no.slomic.smarthytte

import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.micrometer.core.instrument.MeterRegistry
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
import no.slomic.smarthytte.calendar.pendingCalendarOperations
import no.slomic.smarthytte.calendar.startCalendarOutboxDrain
import no.slomic.smarthytte.checkinouts.CheckInOutService
import no.slomic.smarthytte.common.runCatchingLogged
import no.slomic.smarthytte.guests.BootstrapAdmin
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestAvatarService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestQueryService
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.guests.configureGuestRoutes
import no.slomic.smarthytte.issues.IssueReporter
import no.slomic.smarthytte.issues.LoggingIssueReporter
import no.slomic.smarthytte.issues.PersistingIssueReporter
import no.slomic.smarthytte.issues.SqliteIssueRepository
import no.slomic.smarthytte.plugins.HttpClientProvider
import no.slomic.smarthytte.plugins.configureDatabases
import no.slomic.smarthytte.plugins.configureMonitoring
import no.slomic.smarthytte.plugins.configureRouting
import no.slomic.smarthytte.properties.ApiKeyPropertiesHolder
import no.slomic.smarthytte.properties.BootstrapAdminPropertiesHolder
import no.slomic.smarthytte.properties.CheckInProperties
import no.slomic.smarthytte.properties.CloudflareAccessPropertiesHolder
import no.slomic.smarthytte.properties.InfluxDbProperties
import no.slomic.smarthytte.properties.InfluxDbPropertiesHolder
import no.slomic.smarthytte.properties.JlrPropertiesHolder
import no.slomic.smarthytte.properties.KtorPropertiesHolder
import no.slomic.smarthytte.properties.PlacesPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.ReservationService
import no.slomic.smarthytte.reservations.ReservationSystemService
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.configureReservationRoutes
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorQueryService
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorRepository
import no.slomic.smarthytte.sensors.checkinouts.CheckInOutSensorService
import no.slomic.smarthytte.sensors.checkinouts.SqliteCheckInOutSensorRepository
import no.slomic.smarthytte.statistics.StatsService
import no.slomic.smarthytte.statistics.configureOpenApi
import no.slomic.smarthytte.statistics.configureStatsRoutes
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.vehicletrips.BackgroundSyncMode
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTripQueryService
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import no.slomic.smarthytte.vehicletrips.VehicleTripService
import no.slomic.smarthytte.vehicletrips.backgroundSyncMode
import no.slomic.smarthytte.vehicletrips.jlr.JlrAuthService
import no.slomic.smarthytte.vehicletrips.jlr.JlrJourneyClient
import no.slomic.smarthytte.vehicletrips.jlr.JlrJwtSigner
import no.slomic.smarthytte.vehicletrips.jlr.SqliteJlrTokenRepository
import no.slomic.smarthytte.vehicletrips.places.KartverketPlaceResolver
import no.slomic.smarthytte.vehicletrips.places.TripPlaceService
import no.slomic.smarthytte.vehicletrips.places.toKnownPlaces
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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

@Suppress("LongMethod") // wiring of the services, one line per service
fun Application.module() {
    // Initialize database
    configureDatabases()

    // Initialize monitoring
    val meterRegistry = configureMonitoring()

    // One reporter for every service that reports operational issues
    val issueReporter = createIssueReporter()

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
    val vehicleTripService =
        createVehicleTripService(vehicleTripRepository, syncCheckpointService, httpClient, issueReporter)
    val checkInOutSensorService =
        CheckInOutSensorService(checkInOutSensorRepository, syncCheckpointService, issueReporter)
    val guestQueryService = GuestQueryService(guestRepository)
    val guestAvatarService = GuestAvatarService(guestRepository)
    val checkInOutService =
        createCheckInOutService(reservationRepository, checkInOutSensorRepository, vehicleTripRepository, issueReporter)
    val statsService =
        StatsService(ReservationSystemService(reservationRepository), guestQueryService, guestAvatarService)
    val reservationService =
        createReservationService(reservationRepository, guestQueryService, guestAvatarService, checkInOutService)
    val appUserService = AppUserService(guestQueryService)
    val guestProfileService = GuestProfileService(guestRepository)
    val guestAdminService = GuestAdminService(guestRepository)

    // An empty database gets its first admin from BOOTSTRAP_ADMIN_*, before requests are handled
    runBlocking {
        BootstrapAdmin(
            guestRepository,
            guestAdminService,
        ).run(loadProperties<BootstrapAdminPropertiesHolder>().bootstrapAdmin)
    }

    // Run initial load BEFORE starting to handle requests and running synchronization processes in the background
    runBlocking {
        initialLoad(vehicleTripService, checkInOutSensorService, checkInOutService)
    }

    // 🔁 Start background sync after the initial load
    startBackgroundSync(
        ReservationSystemService(reservationRepository),
        vehicleTripService,
        checkInOutSensorService,
        checkInOutService,
        meterRegistry,
        issueReporter,
    )

    // Configure Ktor routing (after the initial load is completed)
    val cloudflareAccess = loadProperties<CloudflareAccessPropertiesHolder>().cloudflareAccess.toConfig()
    val apiKeys = loadProperties<ApiKeyPropertiesHolder>().apiKey.toConfig()
    configureApiRoutes(
        ApiDependencies(
            guestQueryService = guestQueryService,
            guestAvatarService = guestAvatarService,
            guestProfileService = guestProfileService,
            guestAdminService = guestAdminService,
            statsService = statsService,
            reservationService = reservationService,
            appUserService = appUserService,
            cloudflareAccess = cloudflareAccess,
            apiKeys = apiKeys,
        ),
    )
}

private fun createCheckInOutService(
    reservationRepository: ReservationRepository,
    checkInOutSensorRepository: CheckInOutSensorRepository,
    vehicleTripRepository: VehicleTripRepository,
    issueReporter: IssueReporter,
) = CheckInOutService(
    ReservationSystemService(reservationRepository),
    CheckInOutSensorQueryService(checkInOutSensorRepository),
    VehicleTripQueryService(vehicleTripRepository),
    issueReporter,
)

/** The reservation service with the calendar outbox depth and the derivation of check-in/out after a write. */
private fun createReservationService(
    reservationRepository: ReservationRepository,
    guestQueryService: GuestQueryService,
    guestAvatarService: GuestAvatarService,
    checkInOutService: CheckInOutService,
) = ReservationService(
    reservationRepository = reservationRepository,
    guestQueryService = guestQueryService,
    guestAvatarService = guestAvatarService,
    pendingCalendarOperations = ::pendingCalendarOperations,
    onTimesChanged = checkInOutService::updateCheckInOutStatusForReservation,
)

/**
 * Installs every HTTP route group served by the application, except monitoring ([configureMonitoring]).
 *
 * Shared with `OpenApiSpecTest`, so the committed OpenAPI spec is generated from the same routes that production
 * serves. Add new route groups here, not directly in [module].
 */
fun Application.configureApiRoutes(dependencies: ApiDependencies) {
    configureRouting()
    configureAuthentication(dependencies.cloudflareAccess, dependencies.appUserService, dependencies.apiKeys)
    configureAuthRoutes(dependencies.guestProfileService, dependencies.guestAvatarService)
    configureGuestRoutes(
        dependencies.guestQueryService,
        dependencies.guestProfileService,
        dependencies.guestAdminService,
        dependencies.guestAvatarService,
    )
    configureReservationRoutes(dependencies.reservationService)
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
    reservationService: ReservationSystemService,
    vehicleTripService: VehicleTripService,
    checkInOutSensorService: CheckInOutSensorService,
    checkInOutService: CheckInOutService,
    meterRegistry: MeterRegistry,
    issueReporter: IssueReporter,
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
    // (sync task). Only in the daytime. Within the reservation window every step runs on every pass. Outside it only
    // the vehicle trips are fetched, and only when the last fetch is old: the Data Hub history may be a rolling window.
    schedulerScope.launch {
        while (isActive) {
            runCatchingLogged("Background sync") {
                val mode = backgroundSyncMode(
                    isDaytime = isDaytime(),
                    isWithinReservationWindow = reservationService.isWithinReservationWindow(),
                    isTripFetchDue = vehicleTripService.isFetchDue(),
                )
                if (mode != BackgroundSyncMode.NONE) {
                    runCatchingLogged("Fetch vehicle trips") { vehicleTripService.fetchVehicleTrips() }
                }
                if (mode == BackgroundSyncMode.ALL) {
                    runCatchingLogged("Fetch check in/out sensor data") { checkInOutSensorService.fetchCheckInOut() }
                    runCatchingLogged("Update check in/out status") {
                        checkInOutService.updateCheckInOutStatusForAllReservations()
                    }
                }
            }
            delay(duration = checkInOutSyncFrequency)
        }
    }

    // Push the reservation changes in the calendar outbox to Google Calendar
    startCalendarOutboxDrain(reservationService, meterRegistry, issueReporter)
}

fun isDaytime(): Boolean {
    val zone = kotlinx.datetime.TimeZone.of(System.getenv("DAYTIME_TIMEZONE") ?: "Europe/Oslo")
    val now = Clock.System.now().toLocalDateTime(zone).time

    val startTime = LocalTime.parse(System.getenv("DAYTIME_START") ?: "08:00")
    val endTime = LocalTime.parse(System.getenv("DAYTIME_END") ?: "23:00")

    return now in startTime..endTime
}

/** Logs every issue and stores it, so it can be shown to an admin. */
private fun createIssueReporter(): IssueReporter =
    PersistingIssueReporter(LoggingIssueReporter(), SqliteIssueRepository())

private fun createVehicleTripService(
    vehicleTripRepository: VehicleTripRepository,
    syncCheckpointService: SyncCheckpointService,
    httpClient: HttpClient,
    issueReporter: IssueReporter,
): VehicleTripService {
    val jlr = loadProperties<JlrPropertiesHolder>().jlr
    val knownPlaces = loadProperties<PlacesPropertiesHolder>().places.toKnownPlaces()

    val signer = JlrJwtSigner(privateKeyPem = jlr.privateKey.value)
    val authService = JlrAuthService(
        httpClient = httpClient,
        signer = signer,
        tokenRepository = SqliteJlrTokenRepository(),
        issueReporter = issueReporter,
        clientId = jlr.clientId,
    )
    val journeyClient = JlrJourneyClient(httpClient = httpClient, authService = authService, vin = jlr.vin)
    val placeResolver = KartverketPlaceResolver(httpClient = httpClient, knownPlaces = knownPlaces)
    val tripPlaceService = TripPlaceService(placeResolver, vehicleTripRepository, issueReporter)

    return VehicleTripService(
        vehicleTripRepository = vehicleTripRepository,
        syncCheckpointService = syncCheckpointService,
        journeyClient = journeyClient,
        tripPlaceService = tripPlaceService,
        issueReporter = issueReporter,
        fetchInterval = jlr.fetchIntervalHours.hours,
    )
}
