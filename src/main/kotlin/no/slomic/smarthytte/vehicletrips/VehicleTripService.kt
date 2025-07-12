package no.slomic.smarthytte.vehicletrips

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.PersistenceResults
import no.slomic.smarthytte.common.osloDateNow
import no.slomic.smarthytte.common.readVehicleTripFromJsonFile
import no.slomic.smarthytte.common.toOsloDate
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService

class VehicleTripService(
    private val vehicleTripRepository: VehicleTripRepository,
    private val syncCheckpointService: SyncCheckpointService,
    private val httpClient: HttpClient,
    vehicleTripPropertiesHolder: VehicleTripPropertiesHolder =
        loadProperties<VehicleTripPropertiesHolder>(),
) {
    private val logger: Logger = KtorSimpleLogger(VehicleTripService::class.java.name)
    private val vehicleTripProperties = vehicleTripPropertiesHolder.vehicleTrip
    private val filePath = vehicleTripProperties.filePath
    private val username = vehicleTripProperties.username
    private val password = vehicleTripProperties.password
    private val fullSyncFromDate = LocalDate.parse(vehicleTripProperties.syncFromDate)
    private val loginUrl = vehicleTripProperties.loginUrl
    private val tripsUrl = vehicleTripProperties.tripsUrl
    private val userAgent = vehicleTripProperties.userAgent
    private val referrer = vehicleTripProperties.referrer
    private val localeKeyValue = vehicleTripProperties.locale

    suspend fun insertVehicleTripsFromFile() {
        logger.info("Reading vehicle trips from file $filePath and updating database..")

        val persistenceResults: MutableList<PersistenceResult> = mutableListOf()

        val tripsFromFile: List<VehicleTripResponse> = readVehicleTripFromJsonFile(filePath)
        for (trip in tripsFromFile) {
            persistenceResults.add(vehicleTripRepository.addOrUpdate(trip.toInternal()))
        }

        val addedCount = persistenceResults.count { it == PersistenceResult.ADDED }
        val updatedCount = persistenceResults.count { it == PersistenceResult.UPDATED }
        val noActionCount = persistenceResults.count { it == PersistenceResult.NO_ACTION }

        logger.info(
            "Updating vehicle trips in database complete. " +
                "Total trips in file: ${tripsFromFile.size}, added: $addedCount, " +
                "updated: $updatedCount, no actions: $noActionCount",
        )
    }

    suspend fun fetchVehicleTrips() {
        logger.info("Started fetching vehicle trips from external source")

        val filterTimeRange: FilterTimeRange = createFilterTimeRange()

        val vehicleTrips: List<VehicleTrip> = fetchVehicleTrips(
            fromDate = filterTimeRange.fromDate,
            toDate = filterTimeRange.toDate,
        )

        val persistenceResults: PersistenceResults = addOrUpdateVehicleTrips(vehicleTrips)

        val latestDate: LocalDate? = vehicleTrips.maxByOrNull { it.endTime }?.endTime?.toOsloDate()

        if (latestDate != null) {
            syncCheckpointService.addOrUpdateCheckpointForVehicleTrips(latestDate)
        }

        logger.info(
            "Fetching vehicle trips complete. " +
                "Total trips in response: ${vehicleTrips.size}, added: ${persistenceResults.addedCount}, " +
                "updated: ${persistenceResults.updatedCount}, no actions: ${persistenceResults.noActionCount}",
        )
    }

    private suspend fun addOrUpdateVehicleTrips(trips: List<VehicleTrip>): PersistenceResults {
        val results = PersistenceResults()

        for (trip in trips) {
            results.add(vehicleTripRepository.addOrUpdate(trip))
        }

        return results
    }

    private suspend fun fetchVehicleTrips(fromDate: LocalDate, toDate: LocalDate): List<VehicleTrip> {
        val vehicleTrips: MutableList<VehicleTrip> = mutableListOf()

        @Suppress("TooGenericExceptionCaught")
        try {
            // Perform login (assuming form-based login)
            val loginResponse: HttpResponse = httpClient.submitForm(
                url = loginUrl,
                formParameters = parameters {
                    append("loginName", username)
                    append("password", password)
                },
            ) {
                headers {
                    append(HttpHeaders.Cookie, localeKeyValue)
                    append(HttpHeaders.Referrer, referrer)
                    append(HttpHeaders.UserAgent, userAgent)
                }
            }

            if (loginResponse.status == HttpStatusCode.OK) {
                var currentPage = 0
                var totalNumberOfPages: Int

                val loginCookies: List<Cookie> = httpClient.cookies(loginUrl)
                val cookieHeader = loginCookies.joinToString("; ") { "${it.name}=${it.value}" }

                do {
                    currentPage = currentPage + 1

                    val request = createRequest(
                        fromDate = fromDate,
                        toDate = toDate,
                        currentPage = currentPage,
                    )

                    val response: GetVehicleTripsResponse = httpClient.post(tripsUrl) {
                        setBody(request)
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        headers {
                            append(HttpHeaders.Cookie, cookieHeader)
                            append(HttpHeaders.Referrer, referrer)
                            append(HttpHeaders.UserAgent, userAgent)
                        }
                    }.body()

                    vehicleTrips.addAll(response.journeys.map { it.toInternal() })

                    totalNumberOfPages = response.totalNumberOfPages
                } while (currentPage < totalNumberOfPages)
            } else {
                logger.error("Authentication to the vehicle trip service failed with status: ${loginResponse.status}")
            }
        } catch (e: Exception) {
            logger.error("Error occurred during fetching of vehicle trips from external service", e)
        }

        return vehicleTrips
    }

    private suspend fun createFilterTimeRange(): FilterTimeRange {
        val lastTripDate: LocalDate? = syncCheckpointService.checkpointForVehicleTrips()

        val filterTimeRange = if (lastTripDate == null) {
            val range = FilterTimeRange(
                fromDate = fullSyncFromDate,
                toDate = osloDateNow(),
            )
            logger.info("Performing full fetch of vehicle trips between {}", range)

            range
        } else {
            val range = FilterTimeRange(
                fromDate = lastTripDate,
                toDate = osloDateNow(),
            )
            logger.info("Performing incremental fetch of vehicle trips between {}", range)

            range
        }

        return filterTimeRange
    }

    private data class FilterTimeRange(val fromDate: LocalDate, val toDate: LocalDate) {
        override fun toString(): String = "$fromDate - $toDate"
    }
}
