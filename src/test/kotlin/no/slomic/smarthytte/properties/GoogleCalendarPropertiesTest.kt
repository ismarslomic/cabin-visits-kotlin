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
        Given("required environment variable set") {
            val envVars = mapOf(
                "GOOGLE_CREDENTIALS_FILE_PATH" to envVarCredentialsFilePath,
                "GOOGLE_CALENDAR_ID" to envVarCalendarId,
            )
            withEnvironment(envVars) {
                When("reading google properties") {
                    val googleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar
                    val (credentialsFilePath, calendarId) = googleCalendarProperties

                    Then("credentialsFilePath should be set to the environment variable value") {
                        credentialsFilePath shouldBe envVarCredentialsFilePath
                    }
                    Then("calendarId should be set to the environment variable value") {
                        calendarId shouldBe envVarCalendarId
                    }
                }
            }
        }

        Given("required environment variable for credentials file path not set") {
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
