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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestProfileService
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

        "served OpenAPI spec should not contain duplicate schemas of the same class" {
            // A property annotated with its own description makes the generator emit a second schema with a number
            // suffix (for example GuestStats2). Put the description on the class or the referenced fields instead.
            val schemas = servedSpec().jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject

            schemas.keys.filter { it.last().isDigit() } shouldBe emptyList()
        }

        "full update request should require every property, also the nullable email and role" {
            val schemas = servedSpec().jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject
            val update = schemas.getValue("GuestUpdate").jsonObject

            val required = update.getValue("required").jsonArray.map { it.jsonPrimitive.content }

            required.toSet() shouldBe update.getValue("properties").jsonObject.keys
            required.containsAll(listOf("email", "role", "isFamily", "version")) shouldBe true
        }

        "write routes should declare their request body in the served spec" {
            // The plugin infers a body only from call.receive<T>(), not from our receiveJsonBody<T>(), so every
            // write route declares it in its describe {} block. The drift test above cannot see a missing body.
            val paths = servedSpec().jsonObject.getValue("paths").jsonObject
            listOf("put" to "/api/me", "post" to "/api/guests", "put" to "/api/guests/{guestId}").forEach { (m, path) ->
                withClue("$m $path") {
                    val schema = paths.getValue(path).jsonObject.getValue(m).jsonObject["requestBody"]
                        ?.jsonObject?.get("content")?.jsonObject?.get("application/json")?.jsonObject?.get("schema")

                    schema?.jsonObject?.get(
                        "\$ref",
                    )?.jsonPrimitive?.content?.startsWith("#/components/schemas/") shouldBe
                        true
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
            configureApiRoutes(
                ApiDependencies(
                    guestRepository = mockk<GuestRepository>(),
                    guestProfileService = mockk<GuestProfileService>(),
                    guestAdminService = mockk<GuestAdminService>(),
                    statsService = mockk<StatsService>(),
                    appUserService = mockk<AppUserService>(),
                    cloudflareAccess = null,
                ),
            )
        }
        // Decode as UTF-8 explicitly: the route sends application/json without charset, and the client would
        // otherwise fall back to ISO-8859-1.
        val body = client.get("/openapi.json").bodyAsBytes().toString(Charsets.UTF_8)
        spec = Json.parseToJsonElement(body)
    }
    return checkNotNull(spec) { "No OpenAPI spec was served" }
}
