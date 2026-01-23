@file:Suppress("HttpUrlsUsage")

package no.slomic.smarthytte.vehicletrips

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.charsets.Charset
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.common.readContentFromFile
import no.slomic.smarthytte.properties.VehicleTripProperties
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.getResourceFilePath

class VehicleTripServiceIntegrationTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val propertiesHolder = VehicleTripPropertiesHolder(
            vehicleTrip = VehicleTripProperties(
                filePath = getResourceFilePath("vehicleTrips.json"),
                loginUrl = "http://my-vehicle-api.com/login",
                tripsUrl = "http://my-vehicle-api.com/trips",
                username = "foo",
                password = "bar",
                syncFromDate = "2025-03-01",
                syncFrequencyMinutes = 60,
                userAgent = "Wget/1.21.4",
                referrer = "http://my-vehicle-api.com/trips",
                locale = "locale=nb_NO",
                pageSize = 100,
            ),
        )

        // Read vehicleTrips.json from test resources
        fun readJsonResource(fileName: String): String {
            val resourceFilePath = getResourceFilePath(fileName)
            return readContentFromFile(resourceFilePath)
        }

        // Set up the Ktor mock engine to return vehicleTrips.json on the expected endpoint
        fun createMockHttpClient(vehicleTripsPage1: String, vehicleTripsPage2: String): HttpClient =
            HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                engine {
                    addHandler { request ->
                        when {
                            request.url.toString() == propertiesHolder.vehicleTrip.tripsUrl -> {
                                // Read the body as ByteArray and convert to JSON text
                                val bodyContent = request.body
                                val jsonText = when (bodyContent) {
                                    is TextContent -> bodyContent.bytes().toString(Charset.defaultCharset())
                                    else -> ""
                                }
                                // Parse the JSON text and check "currentPage"
                                val jsonElement = Json.parseToJsonElement(jsonText)
                                val currentPage: Int = jsonElement.jsonObject["currentPage"]!!.jsonPrimitive.int
                                respond(
                                    content = if (currentPage == 1) vehicleTripsPage1 else vehicleTripsPage2,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }

                            request.url.toString() == propertiesHolder.vehicleTrip.loginUrl -> {
                                respond(
                                    content = "",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                                )
                            }

                            else -> error("Unhandled url: ${request.url}")
                        }
                    }
                }
            }

        "should fetch vehicle trips using pagination and add fetched trips to the database" {
            // Mock the JSON response returned by the fetch API
            val vehicleTripsPage1 = readJsonResource("vehicleTripsPage1Response.json")
            val vehicleTripsPage2 = readJsonResource("vehicleTripsPage2Response.json")

            val mockHttpClient = createMockHttpClient(vehicleTripsPage1, vehicleTripsPage2)
            val vehicleTripRepository = SqliteVehicleTripRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
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
            tripsInDb.first().id shouldBe "1740825126884"
        }

        "should set the checkpoint for vehicle trips to latest endTime when fetched trip list is not empty" {
            // Mock the JSON response returned by the fetch API
            val vehicleTripsPage1 = readJsonResource("vehicleTripsPage1Response.json")
            val vehicleTripsPage2 = readJsonResource("vehicleTripsPage2Response.json")

            val mockHttpClient = createMockHttpClient(vehicleTripsPage1, vehicleTripsPage2)
            val vehicleTripRepository = SqliteVehicleTripRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            val tripService = VehicleTripService(
                vehicleTripRepository = vehicleTripRepository,
                syncCheckpointService = syncCheckpointService,
                httpClient = mockHttpClient,
                vehicleTripPropertiesHolder = propertiesHolder,
            )

            val checkPointBeforeFetching: LocalDate? = syncCheckpointService.checkpointForVehicleTrips()

            // Act
            tripService.fetchVehicleTrips() // Will call the mocked HTTP endpoint

            // Assert
            val checkPointAfterFetching: LocalDate? = syncCheckpointService.checkpointForVehicleTrips()
            checkPointBeforeFetching.shouldBeNull()
            checkPointAfterFetching shouldBe LocalDate(year = 2025, month = 3, day = 15)
        }

        "should not update the checkpoint for vehicle trips when fetched trip list is empty" {
            // Mock the JSON response returned by the fetch API
            val vehicleTripsPage1 = "vehicleTripsNoTripsResponse.json"
            val vehicleTripsPage2 = ""

            val mockHttpClient = createMockHttpClient(vehicleTripsPage1, vehicleTripsPage2)
            val vehicleTripRepository = SqliteVehicleTripRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            val tripService = VehicleTripService(
                vehicleTripRepository = vehicleTripRepository,
                syncCheckpointService = syncCheckpointService,
                httpClient = mockHttpClient,
                vehicleTripPropertiesHolder = propertiesHolder,
            )

            val checkPointBeforeFetching: LocalDate? = syncCheckpointService.checkpointForVehicleTrips()

            // Act
            tripService.fetchVehicleTrips() // Will call the mocked HTTP endpoint

            // Assert
            val checkPointAfterFetching: LocalDate? = syncCheckpointService.checkpointForVehicleTrips()
            checkPointBeforeFetching.shouldBeNull()
            checkPointAfterFetching.shouldBeNull()
        }

        "should use saved checkpoint as the time filter in the fetch request body" {
            // Arrange
            val vehicleTripsPage1 = readJsonResource("vehicleTripsPage1Response.json")

            var requestFromDate: String? = null

            val mockHttpClient = HttpClient(MockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                engine {
                    addHandler { request ->
                        if (request.url.toString() == propertiesHolder.vehicleTrip.tripsUrl) {
                            val bodyContent = request.body
                            val jsonText = when (bodyContent) {
                                is TextContent -> bodyContent.bytes().toString(Charset.defaultCharset())
                                else -> ""
                            }
                            val jsonElement = Json.parseToJsonElement(jsonText)
                            requestFromDate = jsonElement.jsonObject["fromDateInUserFormat"]!!.jsonPrimitive.content
                            respond(
                                content = vehicleTripsPage1,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        } else {
                            respond("", HttpStatusCode.OK)
                        }
                    }
                }
            }

            val vehicleTripRepository = SqliteVehicleTripRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            val tripService = VehicleTripService(
                vehicleTripRepository = vehicleTripRepository,
                syncCheckpointService = syncCheckpointService,
                httpClient = mockHttpClient,
                vehicleTripPropertiesHolder = propertiesHolder,
            )
            // Simulate setting checkpoint
            syncCheckpointService.addOrUpdateCheckpointForVehicleTrips(
                value = LocalDate(
                    year = 2025,
                    month = 3,
                    day = 2,
                ),
            )

            // Act
            tripService.fetchVehicleTrips()

            // Assert
            requestFromDate shouldBe "02.03.2025"
        }

        "should read vehicle trips from file and add trips to database without updating checkpoint" {
            val vehicleTripsPage1 = "vehicleTripsNoTripsResponse.json"
            val mockHttpClient = createMockHttpClient(vehicleTripsPage1, "")
            val vehicleTripRepository = SqliteVehicleTripRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            val tripService = VehicleTripService(
                vehicleTripRepository = vehicleTripRepository,
                syncCheckpointService = syncCheckpointService,
                httpClient = mockHttpClient,
                vehicleTripPropertiesHolder = propertiesHolder,
            )
            val checkPointBeforeFetching: LocalDate? = syncCheckpointService.checkpointForVehicleTrips()

            // Act
            tripService.insertVehicleTripsFromFile()

            // Assert
            val checkPointAfterFetching: LocalDate? = syncCheckpointService.checkpointForVehicleTrips()
            val tripsInDb: List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
            tripsInDb.size shouldBe 2
            tripsInDb.first().id shouldBe "1741200104073"
            checkPointBeforeFetching.shouldBeNull()
            checkPointAfterFetching.shouldBeNull()
        }
    })
