package no.slomic.smarthytte.properties

import com.sksamuel.hoplite.ConfigException
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe

class GooglePropertiesTest :
    BehaviorSpec({
        Given("required environment variable set for credentials file path") {
            val envVarCredentialsFilePath = "/data/google-client-secret.json"
            withEnvironment("GOOGLE_CREDENTIALS_FILE_PATH", envVarCredentialsFilePath) {
                When("reading google properties") {
                    val googleProperties = loadProperties<GooglePropertiesHolder>().google
                    val (credentialsFilePath, tokensDirectoryPath) = googleProperties

                    Then("credentialsFilePath should be set to the environment variable value") {
                        credentialsFilePath shouldBe envVarCredentialsFilePath
                    }
                    Then("tokensDirectoryPath should be set to default value") {
                        tokensDirectoryPath shouldBe "/data"
                    }
                }

                And("environment variable set for tokens directory path") {
                    val envVarTokensDirectoryPath = "/data2"
                    withEnvironment("GOOGLE_TOKENS_DIRECTORY_PATH", envVarTokensDirectoryPath) {
                        When("reading google properties") {
                            val googleProperties = loadProperties<GooglePropertiesHolder>().google
                            val tokensDirectoryPath = googleProperties.tokensDirectoryPath
                            Then("tokensDirectoryPath should be set to the environment variable value") {
                                tokensDirectoryPath shouldBe envVarTokensDirectoryPath
                            }
                        }
                    }
                }
            }
        }

        Given("required environment variable for credentials file path not set") {
            When("reading google properties") {
                Then("ConfigException should be thrown") {
                    shouldThrowExactly<ConfigException> {
                        loadProperties<GooglePropertiesHolder>()
                    }
                }
            }
        }
    })
