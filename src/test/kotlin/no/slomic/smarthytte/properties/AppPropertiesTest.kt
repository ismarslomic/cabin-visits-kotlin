package no.slomic.smarthytte.properties

import com.sksamuel.hoplite.ConfigException
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.utils.withTestEnvironment

class AppPropertiesTest :
    BehaviorSpec({
        val envVarCredentialsFilePath = "/data/google-service-account-credentials.json"
        val envVarCalendarId = "abcdefghijkl"
        val envVarInfluxDbUrl = "http://localhost:8086"
        val envVarInfluxDbToken = "foobar"
        val envVarInfluxDbOrg = "my.org"
        val envVarInfluxDbBucket = "my_bucket"
        val envVarInfluxDbCheckInMeasurement = "my_measurement"
        val envVarInfluxDbCheckInRangeStart = "2019-01-01T00:00:00Z"
        val envVarInfluxDbCheckInRangeStop = "2025-01-09T00:00:00Z"
        val envVarVehicleTripFilePath = "/data/vehicle-trips.json"
        val envVarVehicleTripSyncFromDate = "2020-01-01"
        val envVarJlrClientId = "client-1"
        val envVarJlrVin = "SAL0000000VIN0001"
        val envVarJlrPrivateKey = "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----"
        val envVarStatisticsDataStartDate = "2020-09-01"

        Given("required environment variables are set") {
            val requiredEnvVars = mapOf(
                "GOOGLE_CREDENTIALS_FILE_PATH" to envVarCredentialsFilePath,
                "GOOGLE_CALENDAR_ID" to envVarCalendarId,
                "INFLUXDB_URL" to envVarInfluxDbUrl,
                "INFLUXDB_TOKEN" to envVarInfluxDbToken,
                "INFLUXDB_ORG" to envVarInfluxDbOrg,
                "INFLUXDB_BUCKET" to envVarInfluxDbBucket,
                "INFLUXDB_CHECK_IN_MEASUREMENT" to envVarInfluxDbCheckInMeasurement,
                "INFLUXDB_CHECK_IN_RANGE_START" to envVarInfluxDbCheckInRangeStart,
                "VEHICLE_TRIP_FILE_PATH" to envVarVehicleTripFilePath,
                "VEHICLE_TRIP_SYNC_FROM_DATE" to envVarVehicleTripSyncFromDate,
                "JLR_CLIENT_ID" to envVarJlrClientId,
                "JLR_VIN" to envVarJlrVin,
                "JLR_PRIVATE_KEY" to envVarJlrPrivateKey,
                "STATISTICS_DATA_START_DATE" to envVarStatisticsDataStartDate,
                "HOME_LATITUDE" to "59.1",
                "HOME_LONGITUDE" to "10.1",
                "HOME_ADDRESS" to "Homestreet 1, 0100 Oslo",
                "CABIN_LATITUDE" to "60.2",
                "CABIN_LONGITUDE" to "8.2",
                "CABIN_ADDRESS" to "Cabinroad 2, 3560 Hemsedal",
            )
            withTestEnvironment(requiredEnvVars) {
                When("reading google properties") {
                    val googleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar

                    Then("credentialsFilePath should be set to the environment variable value") {
                        googleCalendarProperties.credentialsFilePath shouldBe envVarCredentialsFilePath
                    }
                    Then("calendarId should be set to the environment variable value") {
                        googleCalendarProperties.calendarId shouldBe envVarCalendarId
                    }
                }

                When("reading database properties") {
                    val databaseProperties = loadProperties<DatabasePropertiesHolder>().database
                    val databaseFilePath = databaseProperties.filePath

                    val defaultDataFolder = "/data"
                    val defaultDatabaseFilePath = "$defaultDataFolder/app.db"

                    Then("database.filePath should be set to the default value") {
                        databaseFilePath shouldBe defaultDatabaseFilePath
                    }
                }

                When("reading jlr properties") {
                    val jlr = loadProperties<JlrPropertiesHolder>().jlr

                    Then("client id, VIN and private key come from the environment variables") {
                        jlr.clientId shouldBe envVarJlrClientId
                        jlr.vin shouldBe envVarJlrVin
                        jlr.privateKey.value shouldBe envVarJlrPrivateKey
                    }

                    Then("the private key is masked when the properties are printed") {
                        jlr.privateKey.toString() shouldNotContain "abc"
                        jlr.toString() shouldNotContain "abc"
                    }

                    Then("the fetch interval is 12 hours by default") {
                        jlr.fetchIntervalHours shouldBe 12
                    }
                }

                When("reading places properties") {
                    val places = loadProperties<PlacesPropertiesHolder>().places

                    Then("home and cabin come from the environment variables") {
                        places.home shouldBe PlaceProperties(59.1, 10.1, "Homestreet 1, 0100 Oslo", 200.0)
                        places.cabin shouldBe PlaceProperties(60.2, 8.2, "Cabinroad 2, 3560 Hemsedal", 200.0)
                    }
                }

                When("reading statistics properties") {
                    val statisticsProperties = loadProperties<StatisticsPropertiesHolder>().statistics

                    Then("statistics.dataStartDate should be set to the default value") {
                        statisticsProperties.dataStartDate shouldBe envVarStatisticsDataStartDate
                    }
                }

                When("reading influxdb properties") {
                    val influxDbProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb

                    Then("url should be set to the environment variable value") {
                        influxDbProperties.url shouldBe envVarInfluxDbUrl
                    }

                    Then("url should be set to the environment variable value") {
                        influxDbProperties.url shouldBe envVarInfluxDbUrl
                    }

                    Then("token should be set to the environment variable value") {
                        influxDbProperties.token shouldBe envVarInfluxDbToken
                    }

                    Then("org should be set to the environment variable value") {
                        influxDbProperties.org shouldBe envVarInfluxDbOrg
                    }

                    Then("bucket should be set to the environment variable value") {
                        influxDbProperties.bucket shouldBe envVarInfluxDbBucket
                    }

                    Then("checkIn.syncEnabled should be set to the default value true") {
                        influxDbProperties.checkIn.syncEnabled shouldBe true
                    }

                    Then("checkIn.measurement should be set to the environment variable value") {
                        influxDbProperties.checkIn.measurement shouldBe envVarInfluxDbCheckInMeasurement
                    }

                    Then("checkIn.rangeStart should be set to the environment variable value") {
                        influxDbProperties.checkIn.rangeStart shouldBe envVarInfluxDbCheckInRangeStart
                    }
                }

                When("reading vehicle trip properties") {
                    val vehicleTripProperties = loadProperties<VehicleTripPropertiesHolder>().vehicleTrip

                    Then("filePath should be set to the environment variable value") {
                        vehicleTripProperties.filePath shouldBe envVarVehicleTripFilePath
                    }

                    Then("syncEnabled should be set to the default value true") {
                        vehicleTripProperties.syncEnabled shouldBe true
                    }

                    Then("properties for external service should be set to the environment variable value") {
                        LocalDate.parse(vehicleTripProperties.syncFromDate) shouldBe
                            LocalDate.parse(envVarVehicleTripSyncFromDate)
                    }
                }
            }
            And("optional environment variables are set") {
                val envVarDataFolder = "/my/data/"
                val optionalEnvVars = mapOf(
                    "DATA_FOLDER" to envVarDataFolder,
                    "INFLUXDB_CHECK_IN_RANGE_STOP" to envVarInfluxDbCheckInRangeStop,
                    "INFLUXDB_CHECK_IN_SYNC_ENABLED" to "false",
                    "VEHICLE_TRIP_SYNC_ENABLED" to "false",
                    "JLR_FETCH_INTERVAL_HOURS" to "6",
                    "HOME_RADIUS_METERS" to "150",
                    "CABIN_RADIUS_METERS" to "250",
                )
                val requiredAndOptionalEnvVars = requiredEnvVars + optionalEnvVars

                withTestEnvironment(requiredAndOptionalEnvVars) {
                    When("reading database properties") {
                        val databaseProperties = loadProperties<DatabasePropertiesHolder>().database
                        val databaseFilePath = databaseProperties.filePath

                        val expectedDatabaseFilePath = "$envVarDataFolder/app.db"

                        Then("database.filePath should use the data folder set from the environment variable value") {
                            databaseFilePath shouldBe expectedDatabaseFilePath
                        }
                    }

                    When("reading jlr properties") {
                        Then("the fetch interval should be set to the environment variable value") {
                            loadProperties<JlrPropertiesHolder>().jlr.fetchIntervalHours shouldBe 6
                        }
                    }

                    When("reading places properties") {
                        val places = loadProperties<PlacesPropertiesHolder>().places

                        Then("the radii should be set to the environment variable values") {
                            places.home.radiusMeters shouldBe 150.0
                            places.cabin.radiusMeters shouldBe 250.0
                        }
                    }

                    When("reading influxdb properties") {
                        val influxDbProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb

                        Then("checkIn.rangeStop should be set to the environment variable value") {
                            influxDbProperties.checkIn.rangeStop shouldBe envVarInfluxDbCheckInRangeStop
                        }

                        Then("checkIn.syncEnabled should be set to the environment variable value") {
                            influxDbProperties.checkIn.syncEnabled shouldBe false
                        }
                    }

                    When("reading vehicle trip properties") {
                        val vehicleTripProperties = loadProperties<VehicleTripPropertiesHolder>().vehicleTrip

                        Then("syncEnabled should be set to the environment variable value") {
                            vehicleTripProperties.syncEnabled shouldBe false
                        }
                    }
                }
            }
        }

        Given("required environment variable for google credentials file path is not set") {
            val envVars = mapOf(
                "GOOGLE_CALENDAR_ID" to envVarCalendarId,
            )
            withTestEnvironment(envVars) {
                When("reading google properties") {
                    Then("ConfigException should be thrown") {
                        val exception = shouldThrowExactly<ConfigException> {
                            loadProperties<GoogleCalendarPropertiesHolder>()
                        }

                        exception.message shouldContain $$"Unresolved substitution ${GOOGLE_CREDENTIALS_FILE_PATH}"
                    }
                }
            }
        }

        Given("required environment variable for calendar id not set") {
            val envVars = mapOf(
                "GOOGLE_CREDENTIALS_FILE_PATH" to envVarCredentialsFilePath,
            )
            withTestEnvironment(envVars) {
                When("reading google properties") {
                    Then("ConfigException should be thrown") {
                        val exception = shouldThrowExactly<ConfigException> {
                            loadProperties<GoogleCalendarPropertiesHolder>()
                        }

                        exception.message shouldContain $$"Unresolved substitution ${GOOGLE_CALENDAR_ID}"
                    }
                }
            }
        }
    })
