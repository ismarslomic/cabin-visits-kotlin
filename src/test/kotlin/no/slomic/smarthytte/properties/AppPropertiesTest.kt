package no.slomic.smarthytte.properties

import com.sksamuel.hoplite.ConfigException
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class AppPropertiesTest :
    BehaviorSpec({
        val envVarCredentialsFilePath = "/data/google-service-account-credentials.json"
        val envVarCalendarId = "abcdefghijkl"
        val envVarSummaryToGuestFilePath = "/data/summaryToGuestIds.json"
        val envVarGuestFilePath = "/data/guests.json"
        val envVarInfluxDbUrl = "http://localhost:8086"
        val envVarInfluxDbToken = "foobar"
        val envVarInfluxDbOrg = "my.org"
        val envVarInfluxDbBucket = "my_bucket"
        val envVarInfluxDbCheckInMeasurement = "my_measurement"
        val envVarInfluxDbCheckInRangeStart = "2019-01-01T00:00:00Z"
        val envVarInfluxDbCheckInRangeStop = "2025-01-09T00:00:00Z"
        val envVarVehicleTripFilePath = "/data/vehicle-trips.json"

        Given("required environment variables are set") {
            val requiredEnvVars = mapOf(
                "GOOGLE_CREDENTIALS_FILE_PATH" to envVarCredentialsFilePath,
                "GOOGLE_CALENDAR_ID" to envVarCalendarId,
                "GOOGLE_CALENDAR_SUMMARY_TO_GUEST_FILE_PATH" to envVarSummaryToGuestFilePath,
                "GUEST_FILE_PATH" to envVarGuestFilePath,
                "INFLUXDB_URL" to envVarInfluxDbUrl,
                "INFLUXDB_TOKEN" to envVarInfluxDbToken,
                "INFLUXDB_ORG" to envVarInfluxDbOrg,
                "INFLUXDB_BUCKET" to envVarInfluxDbBucket,
                "INFLUXDB_CHECK_IN_MEASUREMENT" to envVarInfluxDbCheckInMeasurement,
                "INFLUXDB_CHECK_IN_RANGE_START" to envVarInfluxDbCheckInRangeStart,
                "VEHICLE_TRIP_FILE_PATH" to envVarVehicleTripFilePath,
            )
            withEnvironment(requiredEnvVars) {
                When("reading google properties") {
                    val googleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar

                    Then("credentialsFilePath should be set to the environment variable value") {
                        googleCalendarProperties.credentialsFilePath shouldBe envVarCredentialsFilePath
                    }
                    Then("calendarId should be set to the environment variable value") {
                        googleCalendarProperties.calendarId shouldBe envVarCalendarId
                    }
                    Then("syncFromDateTime should be set to the default value") {
                        val defaultSyntFromDateTime = "2024-01-01T00:00:00Z"
                        googleCalendarProperties.syncFromDateTime shouldBe defaultSyntFromDateTime
                    }
                    Then("summaryToGuestFilePath should be set to the environment variable value") {
                        googleCalendarProperties.summaryToGuestFilePath shouldBe envVarSummaryToGuestFilePath
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

                When("reading guest properties") {
                    val guestProperties = loadProperties<GuestPropertiesHolder>().guest
                    val guestFilePath = guestProperties.filePath

                    Then("guest.filePath should be set to the environment variable value") {
                        guestFilePath shouldBe envVarGuestFilePath
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
                }
            }
            And("optional environment variables are set") {
                val envVarSyncFromDateTime = "2020-05-07T00:00:00Z"
                val envVarDataFolder = "/my/data/"
                val optionalEnvVars = mapOf(
                    "GOOGLE_CALENDAR_SYNC_FROM_DATE_TIME" to envVarSyncFromDateTime,
                    "DATA_FOLDER" to envVarDataFolder,
                    "INFLUXDB_CHECK_IN_RANGE_STOP" to envVarInfluxDbCheckInRangeStop,
                )
                val requiredAndOptionalEnvVars = requiredEnvVars + optionalEnvVars

                withEnvironment(requiredAndOptionalEnvVars) {
                    When("reading google properties") {
                        val googleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar
                        val syncFromDateTime = googleCalendarProperties.syncFromDateTime

                        Then("syncFromDateTime should be set to the environment variable value") {
                            syncFromDateTime shouldBe envVarSyncFromDateTime
                        }
                    }

                    When("reading database properties") {
                        val databaseProperties = loadProperties<DatabasePropertiesHolder>().database
                        val databaseFilePath = databaseProperties.filePath

                        val expectedDatabaseFilePath = "$envVarDataFolder/app.db"

                        Then("database.filePath should use the data folder set from the environment variable value") {
                            databaseFilePath shouldBe expectedDatabaseFilePath
                        }
                    }

                    When("reading influxdb properties") {
                        val influxDbProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb

                        Then("checkIn.rangeStop should be set to the environment variable value") {
                            influxDbProperties.checkIn.rangeStop shouldBe envVarInfluxDbCheckInRangeStop
                        }
                    }
                }
            }
        }

        Given("required environment variable for google credentials file path is not set") {
            val envVars = mapOf(
                "GOOGLE_CALENDAR_ID" to envVarCalendarId,
            )
            withEnvironment(envVars) {
                When("reading google properties") {
                    Then("ConfigException should be thrown") {
                        val exception = shouldThrowExactly<ConfigException> {
                            loadProperties<GoogleCalendarPropertiesHolder>()
                        }

                        exception.message shouldContain "Unresolved substitution \${GOOGLE_CREDENTIALS_FILE_PATH}"
                    }
                }
            }
        }

        Given("required environment variable for calendar id not set") {
            val envVars = mapOf(
                "GOOGLE_CREDENTIALS_FILE_PATH" to envVarCredentialsFilePath,
            )
            withEnvironment(envVars) {
                When("reading google properties") {
                    Then("ConfigException should be thrown") {
                        val exception = shouldThrowExactly<ConfigException> {
                            loadProperties<GoogleCalendarPropertiesHolder>()
                        }

                        exception.message shouldContain "Unresolved substitution \${GOOGLE_CALENDAR_ID}"
                    }
                }
            }
        }
    })
