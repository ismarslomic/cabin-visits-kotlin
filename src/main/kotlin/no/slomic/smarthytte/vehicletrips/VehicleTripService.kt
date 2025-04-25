package no.slomic.smarthytte.vehicletrips

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.common.UpsertStatus
import no.slomic.smarthytte.common.readVehicleTripFromJsonFile
import no.slomic.smarthytte.common.utcDateNow
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

class VehicleTripService(private val vehicleTripRepository: VehicleTripRepository) {
    private val logger: Logger = KtorSimpleLogger(VehicleTripService::class.java.name)
    private val vehicleTripProperties = loadProperties<VehicleTripPropertiesHolder>().vehicleTrip
    private val filePath = vehicleTripProperties.filePath

    suspend fun insertVehicleTripsFromFile() {
        logger.info("Reading vehicle trips from file $filePath and updating database..")

        val upsertStatus: MutableList<UpsertStatus> = mutableListOf()

        val tripsFromFile: List<VehicleTripResponse> = readVehicleTripFromJsonFile(filePath)
        for (trip in tripsFromFile) {
            upsertStatus.add(vehicleTripRepository.addOrUpdate(trip.toInternal()))
        }

        val addedCount = upsertStatus.count { it == UpsertStatus.ADDED }
        val updatedCount = upsertStatus.count { it == UpsertStatus.UPDATED }
        val noActionCount = upsertStatus.count { it == UpsertStatus.NO_ACTION }

        logger.info(
            "Updating vehicle trips in database complete. " +
                "Total trips: ${tripsFromFile.size}, added: $addedCount, " +
                "updated: $updatedCount, no actions: $noActionCount",
        )
    }
}

fun Application.synchronizeVehicleTrips() {
    val vehicleTripProperties = loadProperties<VehicleTripPropertiesHolder>().vehicleTrip

    // Create an HTTP client with a persistent cookie storage
    val client = HttpClient(CIO) {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage() // Stores cookies automatically
        }
        install(HttpRedirect) {
            checkHttpMethod = false // Allow redirects on POST
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true }) // Allows parsing JSON responses
        }
    }

    // Configuration
    val username = vehicleTripProperties.username
    val password = vehicleTripProperties.password
    val fromDate = LocalDate.parse(vehicleTripProperties.syncFromDate)
    val toDate = utcDateNow()
    val loginUrl = vehicleTripProperties.loginUrl
    val tripsUrl = vehicleTripProperties.tripsUrl
    val userAgent = vehicleTripProperties.userAgent
    val referrer = vehicleTripProperties.referrer
    val localeKeyValue = vehicleTripProperties.locale

    log.info("Starting syncing the vehicle trips")

    runBlocking {
        // Perform login (assuming form-based login)
        val loginResponse: HttpResponse = client.submitForm(
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
            // Now perform an authenticated request using the stored session cookie
            val loginCookies: List<Cookie> = client.cookies(loginUrl)
            val cookieHeader = loginCookies.joinToString("; ") { "${it.name}=${it.value}" }
            val request = createRequest(fromDate = fromDate, toDate = toDate)
            val response: GetVehicleTripsResponse = client.post(tripsUrl) {
                setBody(request)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Cookie, cookieHeader)
                    append(HttpHeaders.Referrer, referrer)
                    append(HttpHeaders.UserAgent, userAgent)
                }
            }.body()

            log.info("Vehicle trip response: $response")
        } else {
            log.error("Authentication to the vehicle trip service failed with status: ${loginResponse.status}")
        }

        client.close()
        log.info("Completed syncing the vehicle trips")
    }
}
