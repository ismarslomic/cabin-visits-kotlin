package no.slomic.smarthytte.properties

import com.sksamuel.hoplite.ConfigException
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.utils.withTestEnvironment

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
        val envVarVehicleTripLoginUrl = "https://mycar.com/login"
        val envVarVehicleTripTripsUrl = "https://mycar.com/trips"
        val envVarVehicleTripUsername = "secretName"
        val envVarVehicleTripPassword = "secretPassword"
        val envVarVehicleTripSyncFromDate = "2020-01-01"
        val envVarVehicleTripUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
        val envVarVehicleTripReferrer = "https://myapp.com"
        val envVarVehicleTripLocale = "key=nb_NO"
        val envVarSkiStatsBaseUrl = "https://skistats.com"
        val envVarSkiStatsAuthPath = "/auth"
        val envVarSkiStatsSeasonStatsPath = "/season-stats"
        val envVarSkiStatsAppInstanceId = "app-instance-id"
        val envVarSkiStatsAppPlatform = "app-platform"
        val envVarSkiStatsApiKey = "api-key"
        val envVarSkiStatsAppVersion = "1.0.0"
        val envVarSkiStatsCookie = "cookie"
        val envVarSkiStatsUserAgent = "user-agent"
        val envVarSkiStatsProfileIsmarUsername = "ismar-username"
        val envVarSkiStatsProfileIsmarPassword = "ismar-password"
        val envVarSkiStatsProfileIsmarAgentId = "ismar-agent-id"
        val envVarSkiStatsProfileIsmarClientSecret = "client-secret"
        val envVarSkiStatsProfileIsmarClientId = "client-id"

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
                "VEHICLE_TRIP_LOGIN_URL" to envVarVehicleTripLoginUrl,
                "VEHICLE_TRIP_TRIPS_URL" to envVarVehicleTripTripsUrl,
                "VEHICLE_TRIP_USERNAME" to envVarVehicleTripUsername,
                "VEHICLE_TRIP_PASSWORD" to envVarVehicleTripPassword,
                "VEHICLE_TRIP_SYNC_FROM_DATE" to envVarVehicleTripSyncFromDate,
                "VEHICLE_TRIP_USER_AGENT" to envVarVehicleTripUserAgent,
                "VEHICLE_TRIP_REFERRER" to envVarVehicleTripReferrer,
                "VEHICLE_TRIP_LOCALE" to envVarVehicleTripLocale,
                "SKI_STATS_BASE_URL" to envVarSkiStatsBaseUrl,
                "SKI_STATS_AUTH_PATH" to envVarSkiStatsAuthPath,
                "SKI_STATS_SEASON_STATS_PATH" to envVarSkiStatsSeasonStatsPath,
                "SKI_STATS_APP_INSTANCE_ID" to envVarSkiStatsAppInstanceId,
                "SKI_STATS_APP_PLATFORM" to envVarSkiStatsAppPlatform,
                "SKI_STATS_API_KEY" to envVarSkiStatsApiKey,
                "SKI_STATS_APP_VERSION" to envVarSkiStatsAppVersion,
                "SKI_STATS_COOKIE" to envVarSkiStatsCookie,
                "SKI_STATS_USER_AGENT" to envVarSkiStatsUserAgent,
                "SKI_STATS_PROFILE_ISMAR_CLIENT_ID" to envVarSkiStatsProfileIsmarClientId,
                "SKI_STATS_PROFILE_ISMAR_CLIENT_SECRET" to envVarSkiStatsProfileIsmarClientSecret,
                "SKI_STATS_PROFILE_ISMAR_USERNAME" to envVarSkiStatsProfileIsmarUsername,
                "SKI_STATS_PROFILE_ISMAR_PASSWORD" to envVarSkiStatsProfileIsmarPassword,
                "SKI_STATS_PROFILE_ISMAR_AGENT_ID" to envVarSkiStatsProfileIsmarAgentId,
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

                    Then("properties for external service should be set to the environment variable value") {
                        vehicleTripProperties.loginUrl shouldBe envVarVehicleTripLoginUrl
                        vehicleTripProperties.tripsUrl shouldBe envVarVehicleTripTripsUrl
                        vehicleTripProperties.username shouldBe envVarVehicleTripUsername
                        vehicleTripProperties.password shouldBe envVarVehicleTripPassword
                        LocalDate.parse(vehicleTripProperties.syncFromDate) shouldBe
                            LocalDate.parse(envVarVehicleTripSyncFromDate)
                        vehicleTripProperties.userAgent shouldBe envVarVehicleTripUserAgent
                        vehicleTripProperties.referrer shouldBe envVarVehicleTripReferrer
                        vehicleTripProperties.locale shouldBe envVarVehicleTripLocale
                    }
                }

                When("reading ski stats properties") {
                    val skiStatsProperties = loadProperties<SkiStatsPropertiesHolder>().skiStats

                    Then("properties should be set to the environment variable value") {
                        skiStatsProperties.core.baseUrl shouldBe envVarSkiStatsBaseUrl
                        skiStatsProperties.core.authPath shouldBe envVarSkiStatsAuthPath
                        skiStatsProperties.core.seasonStatsPath shouldBe envVarSkiStatsSeasonStatsPath
                        skiStatsProperties.core.appInstanceId shouldBe envVarSkiStatsAppInstanceId
                        skiStatsProperties.core.appPlatform shouldBe envVarSkiStatsAppPlatform
                        skiStatsProperties.core.apiKey shouldBe envVarSkiStatsApiKey
                        skiStatsProperties.core.appVersion shouldBe envVarSkiStatsAppVersion
                        skiStatsProperties.core.cookie shouldBe envVarSkiStatsCookie
                        skiStatsProperties.core.userAgent shouldBe envVarSkiStatsUserAgent
                        skiStatsProperties.profiles shouldHaveSize 1
                        val profileIsmar = skiStatsProperties.profiles.first()
                        profileIsmar.username shouldBe envVarSkiStatsProfileIsmarUsername
                        profileIsmar.password shouldBe envVarSkiStatsProfileIsmarPassword
                        profileIsmar.agentId shouldBe envVarSkiStatsProfileIsmarAgentId
                        profileIsmar.clientSecret shouldBe envVarSkiStatsProfileIsmarClientSecret
                        profileIsmar.clientId shouldBe envVarSkiStatsProfileIsmarClientId
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

                withTestEnvironment(requiredAndOptionalEnvVars) {
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
