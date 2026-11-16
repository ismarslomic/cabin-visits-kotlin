package no.slomic.smarthytte.vehicletrips

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.common.osloTimeZone
import no.slomic.smarthytte.common.readVehicleTripsFromJsonFile
import no.slomic.smarthytte.common.toOsloDate
import no.slomic.smarthytte.issues.IssueCode
import no.slomic.smarthytte.issues.IssueReporter
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.vehicletrips.jlr.JlrAuthException
import no.slomic.smarthytte.vehicletrips.jlr.JlrJourneyClient
import no.slomic.smarthytte.vehicletrips.jlr.JlrJourneyResponse
import no.slomic.smarthytte.vehicletrips.jlr.JlrRequestException
import no.slomic.smarthytte.vehicletrips.jlr.MATCH_CANDIDATE_WINDOW
import no.slomic.smarthytte.vehicletrips.jlr.findMatchIn
import no.slomic.smarthytte.vehicletrips.jlr.toGeoPoint
import no.slomic.smarthytte.vehicletrips.jlr.toVehicleTrip
import no.slomic.smarthytte.vehicletrips.places.TripPlaceService
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What happened to one journey from the Data Hub. */
private enum class JourneyResult { ADDED, MATCHED, KNOWN }

/** The problems of a fetch that a successful fetch resolves. Each is reported with its code as key. */
private val FETCH_ISSUE_CODES = listOf(IssueCode.JLR_AUTH_FAILED, IssueCode.JLR_REQUEST_FAILED, IssueCode.FETCH_FAILED)

class VehicleTripService(
    private val vehicleTripRepository: VehicleTripRepository,
    private val syncCheckpointService: SyncCheckpointService,
    private val journeyClient: JlrJourneyClient,
    private val tripPlaceService: TripPlaceService,
    private val issueReporter: IssueReporter,
    vehicleTripPropertiesHolder: VehicleTripPropertiesHolder = loadProperties<VehicleTripPropertiesHolder>(),
    private val fetchInterval: Duration = 12.hours,
    private val clock: Clock = Clock.System,
) {
    private val logger: Logger = KtorSimpleLogger(VehicleTripService::class.java.name)
    private val vehicleTripProperties = vehicleTripPropertiesHolder.vehicleTrip
    private val filePath = vehicleTripProperties.filePath
    private val syncEnabled = vehicleTripProperties.syncEnabled
    private val fullSyncFromDate = LocalDate.parse(vehicleTripProperties.syncFromDate)

    // Kept in memory: after a restart the first pass fetches. A failed fetch does not count, the next pass retries.
    @Volatile
    private var lastSuccessfulFetch: Instant? = null

    suspend fun insertVehicleTripsFromFile() {
        logger.info("Reading vehicle trips from file $filePath and updating database..")

        val persistenceResults: MutableList<PersistenceResult> = mutableListOf()

        val tripsFromFile: List<VehicleTripResponse> = readVehicleTripsFromJsonFile(filePath)
        for (trip in tripsFromFile) {
            persistenceResults.add(vehicleTripRepository.addOrUpdate(trip.toInternal(), SYSTEM_ACTOR))
        }

        val addedCount = persistenceResults.count { it == PersistenceResult.ADDED }
        val updatedCount = persistenceResults.count { it == PersistenceResult.UPDATED }
        val noActionCount = persistenceResults.count { it == PersistenceResult.NO_ACTION }

        logger.info(
            "Reading vehicle trips from file complete. " +
                "Total trips in file: ${tripsFromFile.size}, added: $addedCount, " +
                "updated: $updatedCount, no actions: $noActionCount",
        )
    }

    /**
     * True when no fetch has succeeded since startup, or the last one is [fetchInterval] old. Never due when the sync
     * is disabled, so the background loop does not call a fetch that does nothing.
     */
    fun isFetchDue(now: Instant = clock.now()): Boolean =
        syncEnabled && isFetchDue(lastSuccessfulFetch, now, fetchInterval)

    /**
     * Fetches the journeys from the JLR Data Hub since the checkpoint, links them to the trips of the archive or stores
     * them as new trips, and looks up the places of trips that are missing them. Problems are reported through the
     * [IssueReporter] and do not escape, so the background loop goes on.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun fetchVehicleTrips() {
        if (!syncEnabled) {
            logger.info("Vehicle trip sync is disabled, skipping fetch of vehicle trips from external source.")
            return
        }

        try {
            fetchAndStoreJourneys()
        } catch (e: CancellationException) {
            throw e
        } catch (e: JlrAuthException) {
            report(IssueCode.JLR_AUTH_FAILED, e)
        } catch (e: JlrRequestException) {
            report(IssueCode.JLR_REQUEST_FAILED, e)
        } catch (e: Exception) {
            report(IssueCode.FETCH_FAILED, e)
        }
    }

    private suspend fun fetchAndStoreJourneys() {
        // Trips that lack a city first, so the check-in/out step in the same pass sees them
        val reResolved = tripPlaceService.reResolvePlaceMissing()

        val from: Instant = fetchFrom()
        val journeys: List<JlrJourneyResponse> = journeyClient.fetchJourneys(from)
        val results: List<JourneyResult> = journeys.map { storeJourney(it) }

        journeys.maxOfOrNull { it.endTime }?.let {
            syncCheckpointService.addOrUpdateCheckpointForVehicleTrips(it.toOsloDate())
        }
        lastSuccessfulFetch = clock.now()
        FETCH_ISSUE_CODES.forEach { issueReporter.resolve(it, key = it.name) }

        logger.info(
            "Vehicle trips fetched: from=$from journeys=${journeys.size} " +
                "added=${results.count { it == JourneyResult.ADDED }} " +
                "matched=${results.count { it == JourneyResult.MATCHED }} " +
                "known=${results.count { it == JourneyResult.KNOWN }} " +
                "places_healed=${reResolved.healed} places_still_missing=${reResolved.stillMissing}",
        )
    }

    /** A journey that is stored already (second run) is left alone, a journey that is an archive trip is linked. */
    private suspend fun storeJourney(journey: JlrJourneyResponse): JourneyResult {
        val candidates = vehicleTripRepository.findByEndTimeBetween(
            from = journey.endTime - MATCH_CANDIDATE_WINDOW,
            to = journey.endTime + MATCH_CANDIDATE_WINDOW,
        )
        return when {
            candidates.any { it.jlrJourneyId == journey.journeyId } -> JourneyResult.KNOWN

            else -> when (val archiveTrip = journey.findMatchIn(candidates)) {
                null -> addNewTrip(journey)
                else -> linkToArchiveTrip(archiveTrip, journey)
            }
        }
    }

    private suspend fun addNewTrip(journey: JlrJourneyResponse): JourneyResult {
        val start = journey.startLocation.toGeoPoint()
        val end = journey.endLocation.toGeoPoint()
        val tripId = journey.endTime.toEpochMilliseconds().toString()
        val places = tripPlaceService.resolvePlaces(tripId = tripId, start = start, end = end)
        vehicleTripRepository.addOrUpdate(journey.toVehicleTrip(places), SYSTEM_ACTOR)
        return JourneyResult.ADDED
    }

    /** Only the link and the coordinates are set: the archive row keeps its values and needs no place lookup. */
    private suspend fun linkToArchiveTrip(archiveTrip: VehicleTrip, journey: JlrJourneyResponse): JourneyResult {
        vehicleTripRepository.setJlrData(
            id = archiveTrip.id,
            jlrJourneyId = journey.journeyId,
            startPoint = journey.startLocation.toGeoPoint(),
            endPoint = journey.endLocation.toGeoPoint(),
            actor = SYSTEM_ACTOR,
        )
        return JourneyResult.MATCHED
    }

    private suspend fun fetchFrom(): Instant {
        val checkpoint: LocalDate? = syncCheckpointService.checkpointForVehicleTrips()
        return (checkpoint ?: fullSyncFromDate).atStartOfDayIn(osloTimeZone)
    }

    private suspend fun report(code: IssueCode, e: Exception) {
        issueReporter.report(
            code = code,
            key = code.name,
            message = "Fetching vehicle trips failed: ${e.message}",
            cause = e,
        )
    }
}
