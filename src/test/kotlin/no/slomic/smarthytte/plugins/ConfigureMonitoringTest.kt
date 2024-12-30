package no.slomic.smarthytte.plugins

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication

class ConfigureMonitoringTest :
    StringSpec({
        "should respond with metrics not null" {
            testApplication {
                application { configureMonitoring() }

                client.get("/metrics").apply {
                    bodyAsText().shouldNotBeNull()
                }
            }
        }
    })
