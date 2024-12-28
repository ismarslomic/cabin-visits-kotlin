package no.slomic.smarthytte

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import no.slomic.smarthytte.plugins.configureRouting

class ConfigureRoutingTest :
    StringSpec({
        "should respond with Hello GraalVM!" {
            testApplication {
                application { configureRouting() }

                client.get("/").apply {
                    bodyAsText() shouldBe "Hello GraalVM!"
                }
            }
        }
    })
