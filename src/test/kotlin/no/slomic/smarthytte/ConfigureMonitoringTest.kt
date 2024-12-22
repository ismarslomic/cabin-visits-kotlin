package no.slomic.smarthytte

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import no.slomic.smarthytte.plugins.configureMonitoring
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.Test

class ConfigureMonitoringTest {
    @Test
    fun testGetMetrics() =
        testApplication {
            application {
                configureMonitoring()
            }
            client.get("/metrics").apply {
                assertNotNull("metrics", bodyAsText())
            }
        }
}
