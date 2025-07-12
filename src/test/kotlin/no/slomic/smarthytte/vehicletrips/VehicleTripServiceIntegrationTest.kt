@file:Suppress("HttpUrlsUsage")

package no.slomic.smarthytte.vehicletrips

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.BaseDbTest
import no.slomic.smarthytte.properties.VehicleTripProperties
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import java.nio.file.Files
import java.nio.file.Paths

class VehicleTripServiceIntegrationTest :
    BaseDbTest({
        val propertiesHolder = VehicleTripPropertiesHolder(
            vehicleTrip = VehicleTripProperties(
                filePath = "/foo/bar",
                loginUrl = "http://my-vehicle-api.com/login",
                tripsUrl = "http://my-vehicle-api.com/trips",
                username = "foo",
                password = "bar",
                syncFromDate = "2025-03-01",
                syncFrequencyMinutes = 60,
                userAgent = "Wget/1.21.4",
                referrer = "http://my-vehicle-api.com/trips",
                locale = "locale=nb_NO",
            ),
        )

        // Read vehicleTrips.json from test resources
        fun readJsonResource(resource: String): String {
            val uri = this::class.java.classLoader.getResource(resource)!!.toURI()
            require(uri != null) { "resource $resource not found" }
            return String(Files.readAllBytes(Paths.get(uri)))
        }

        // Set up the Ktor mock engine to return vehicleTrips.json on the expected endpoint
        fun createMockHttpClient(json: String): HttpClient = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    when {
                        request.url.toString() == propertiesHolder.vehicleTrip.tripsUrl -> {
                            respond(
                                content = json,
                                status = HttpStatusCode.Companion.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        request.url.toString() == propertiesHolder.vehicleTrip.loginUrl -> {
                            respond(
                                content = "",
                                status = HttpStatusCode.Companion.OK,
                                headers = headersOf(HttpHeaders.ContentType, "text/html"),
                            )
                        }

                        else -> error("Unhandled url: ${request.url}")
                    }
                }
            }
        }

        "fetchVehicleTrips adds the trips from API to the database" {
            // Arrange
            val tripJson = readJsonResource("vehicleTrips.json")
            val mockHttpClient = createMockHttpClient(tripJson)
            // Provide actual implementations or test fakes for what VehicleTripService needs:
            val vehicleTripRepository = SqliteVehicleTripRepository()
            val syncCheckpointService =
                SyncCheckpointService(SqliteSyncCheckpointRepository())

            val tripService = VehicleTripService(
                vehicleTripRepository = vehicleTripRepository,
                syncCheckpointService = syncCheckpointService,
                httpClient = mockHttpClient,
                vehicleTripPropertiesHolder = propertiesHolder,
            )

            // Act
            tripService.fetchVehicleTrips() // Will call the mocked HTTP endpoint

            // Assert
            val tripsInDb: List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
            tripsInDb.size shouldBe 9
            // Further field checks if needed
            tripsInDb.first().startAddress shouldBe "Foo address 2, 0550 Oslo, Norge"
        }
    })
