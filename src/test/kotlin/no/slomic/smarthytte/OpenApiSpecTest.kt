package no.slomic.smarthytte

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.plugins.configureMonitoring
import no.slomic.smarthytte.statistics.StatsService
import java.io.File

private const val SPEC_PATH = "openapi/cabin-visits.json"
private const val UPDATE_ENV = "UPDATE_OPENAPI"
private const val REGENERATE_COMMAND = "$UPDATE_ENV=true ./gradlew test --tests '*OpenApiSpecTest' --rerun"

private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/**
 * Guards the OpenAPI contract that API clients (for example the MMM-CabinStats MagicMirror module) generate their
 * types from. The spec served at `/openapi.json` must equal the committed [SPEC_PATH], so every contract change shows
 * up in the diff of a pull request.
 *
 * After an intended API change, regenerate the committed file with:
 * `UPDATE_OPENAPI=true ./gradlew test --tests '*OpenApiSpecTest' --rerun` (see [REGENERATE_COMMAND])
 */
class OpenApiSpecTest :
    StringSpec({
        "committed OpenAPI spec should match the spec served by the application" {
            val served = servedSpec()
            val specFile = File(SPEC_PATH)

            if (System.getenv(UPDATE_ENV) == "true") {
                specFile.parentFile.mkdirs()
                specFile.writeText(prettyJson.encodeToString(JsonElement.serializer(), served) + "\n", Charsets.UTF_8)
            } else {
                withClue("$SPEC_PATH is missing or outdated. Regenerate it with: $REGENERATE_COMMAND") {
                    specFile.exists() shouldBe true
                    Json.parseToJsonElement(specFile.readText(Charsets.UTF_8)) shouldBe served
                }
            }
        }
    })

private fun servedSpec(): JsonElement {
    var spec: JsonElement? = null
    testApplication {
        application {
            // Same route groups as Application.module(); repositories and services are never called.
            configureMonitoring()
            configureApiRoutes(mockk<GuestRepository>(), mockk<StatsService>())
        }
        // Decode as UTF-8 explicitly: the route sends application/json without charset, and the client would
        // otherwise fall back to ISO-8859-1.
        val body = client.get("/openapi.json").bodyAsBytes().toString(Charsets.UTF_8)
        spec = Json.parseToJsonElement(body)
    }
    return checkNotNull(spec) { "No OpenAPI spec was served" }
}
