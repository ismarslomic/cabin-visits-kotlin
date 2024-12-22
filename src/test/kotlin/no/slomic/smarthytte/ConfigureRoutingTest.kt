package no.slomic.smarthytte

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import no.slomic.smarthytte.plugins.configureRouting
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigureRoutingTest {
    @Test
    fun testGetHi() =
        testApplication {
            application {
                configureRouting()
            }
            client.get("/").apply {
                assertEquals("Hello GraalVM!", bodyAsText())
            }
        }
}
