package no.slomic.smarthytte.properties

import com.sksamuel.hoplite.ConfigException
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class GoogleCalendarPropertiesTest :
    BehaviorSpec({
        val envVarCredentialsFilePath = "/data/google-service-account-credentials.json"
        val envVarCalendarId = "abcdefghijkl"

        Given("required and optional environment variables are set") {
            val requiredEnvVars = mapOf(
                "GOOGLE_CREDENTIALS_FILE_PATH" to envVarCredentialsFilePath,
                "GOOGLE_CALENDAR_ID" to envVarCalendarId,
            )
            withEnvironment(requiredEnvVars) {
                When("reading google properties") {
                    val googleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar
                    val (credentialsFilePath, calendarId, syncFromDateTime) = googleCalendarProperties

                    Then("credentialsFilePath should be set to the environment variable value") {
                        credentialsFilePath shouldBe envVarCredentialsFilePath
                    }
                    Then("calendarId should be set to the environment variable value") {
                        calendarId shouldBe envVarCalendarId
                    }
                    Then("syncFromDateTime should be set to the default value") {
                        val defaultSyntFromDateTime = "2024-01-01T00:00:00Z"
                        syncFromDateTime shouldBe defaultSyntFromDateTime
                    }
                }
            }
            And("optional environment variables are set") {
                val envVarSyncFromDateTime = "2020-05-07T00:00:00Z"
                val requiredAndOptionalEnvVars =
                    requiredEnvVars + ("GOOGLE_CALENDAR_SYNC_FROM_DATE_TIME" to envVarSyncFromDateTime)

                withEnvironment(requiredAndOptionalEnvVars) {
                    When("reading google properties") {
                        val googleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar
                        val syncFromDateTime = googleCalendarProperties.syncFromDateTime

                        Then("syncFromDateTime should be set to the environment variable value") {
                            syncFromDateTime shouldBe envVarSyncFromDateTime
                        }
                    }
                }
            }
        }

        Given("required environment variable for credentials file path is not set") {
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
