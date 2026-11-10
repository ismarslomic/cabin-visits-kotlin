package no.slomic.smarthytte

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.plugins.configureMonitoring
import no.slomic.smarthytte.reservations.ReservationService
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

        "every put and post route should declare a request body in the served spec" {
            // The plugin infers a body only from call.receive<T>(), not from our receiveJsonBody<T>() or
            // receiveBinaryBody(), so every write route declares it in its describe {} block. The drift test above
            // cannot see a missing body.
            val operations = servedSpec().operations().filter { it.method in setOf("put", "post") }

            operations.map { it.name }.containsAll(
                listOf(
                    "put /api/me",
                    "post /api/guests",
                    "put /api/guests/{guestId}",
                    "put /api/me/avatar",
                    "put /api/guests/{guestId}/avatar",
                    "post /api/reservations",
                    "put /api/reservations/{id}",
                ),
            ) shouldBe true

            operations.forEach { operation ->
                withClue(operation.name) {
                    val content = operation.body["requestBody"]?.jsonObject?.get("content")?.jsonObject
                    content shouldNotBe null
                    content!!.keys.size shouldBe 1

                    val (mediaType, media) = content.entries.single()
                    val schema = media.jsonObject["schema"]?.jsonObject
                    when (mediaType) {
                        "application/json" ->
                            schema?.get($$"$ref")?.jsonPrimitive?.content?.startsWith("#/components/schemas/") shouldBe
                                true

                        "image/jpeg" -> schema?.get("format")?.jsonPrimitive?.content shouldBe "binary"

                        else -> error("Unexpected request media type $mediaType")
                    }
                }
            }
        }

        "every property and parameter in the served spec should have a description" {
            // The generator emits a second schema (GuestStats2) for a property that refers to a class and has its own
            // description, so such a property is documented by the description of the class it refers to instead.
            val spec = servedSpec()
            val schemas = spec.jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject

            schemas.forEach { (schemaName, schema) ->
                schema.jsonObject["properties"]?.jsonObject?.forEach { (property, definition) ->
                    withClue("$schemaName.$property") {
                        val description = definition.jsonObject["description"]?.jsonPrimitive?.content
                        val reference = definition.jsonObject[$$"$ref"]?.jsonPrimitive?.content
                        val referenced = reference?.substringAfterLast('/')?.let { schemas[it] }

                        val documented = description?.isNotBlank() == true ||
                            referenced?.jsonObject?.get("description")?.jsonPrimitive?.content?.isNotBlank() == true
                        documented shouldBe true
                    }
                }
            }

            spec.operations().forEach { operation ->
                operation.body["parameters"]?.jsonArray?.forEach { parameter ->
                    withClue("${operation.name} ${parameter.jsonObject["name"]}") {
                        parameter.jsonObject["description"]?.jsonPrimitive?.content?.isNotBlank() shouldBe true
                    }
                }
            }
        }

        "served OpenAPI spec should not contain a schema or parameter that failed to resolve" {
            // The generator does not fail on an invalid annotation (for example an @JsonSchema.Example that is not a
            // JSON literal), it writes "Failed to resolve ..." into the description instead.
            servedSpec().toString().contains("Failed to resolve") shouldBe false
        }

        "every property and parameter in the served spec should have an example" {
            // API design principle 11. A property that refers to a class (or a list of one) gets its examples from
            // that class.
            val spec = servedSpec()
            val schemas = spec.jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject

            schemas.forEach { (schemaName, schema) ->
                schema.jsonObject["properties"]?.jsonObject?.forEach { (property, definition) ->
                    withClue("$schemaName.$property") {
                        val refersToClass = definition.jsonObject.containsKey($$"$ref") ||
                            definition.jsonObject["items"]?.jsonObject?.containsKey($$"$ref") == true
                        (refersToClass || definition.jsonObject.containsKey("example")) shouldBe true
                    }
                }
            }

            spec.operations().forEach { operation ->
                operation.body["parameters"]?.jsonArray?.forEach { parameter ->
                    withClue("${operation.name} ${parameter.jsonObject["name"]}") {
                        parameter.jsonObject.containsKey("example") shouldBe true
                    }
                }
            }
        }

        "every example in the served spec should match the type and the enum values of its property" {
            val schemas = servedSpec().jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject

            schemas.forEach { (schemaName, schema) ->
                schema.jsonObject["properties"]?.jsonObject?.forEach { (property, definition) ->
                    val example = definition.jsonObject["example"] ?: return@forEach
                    withClue("$schemaName.$property example $example") {
                        val types = when (val type = definition.jsonObject["type"]) {
                            is JsonArray -> type.map { it.jsonPrimitive.content }
                            else -> listOfNotNull(type?.jsonPrimitive?.content)
                        }
                        (example.jsonTypeName() in types) shouldBe true

                        val allowed = definition.jsonObject["enum"]?.jsonArray?.map { it.jsonPrimitive.content }
                        if (allowed != null) (example.jsonPrimitive.content in allowed) shouldBe true
                    }
                }
            }
        }

        "every response in the served spec should have a description" {
            servedSpec().operations().forEach { operation ->
                operation.body.getValue("responses").jsonObject.forEach { (status, response) ->
                    withClue("${operation.name} $status") {
                        response.jsonObject["description"]?.jsonPrimitive?.content?.isNotBlank() shouldBe true
                    }
                }
            }
        }
    })

/** The JSON schema type name of a value; a whole number is an integer, any other number a number. */
private fun JsonElement.jsonTypeName(): String = when {
    this is JsonArray -> "array"
    this is JsonObject -> "object"
    jsonPrimitive.isString -> "string"
    jsonPrimitive.content in setOf("true", "false") -> "boolean"
    jsonPrimitive.content.matches(Regex("-?\\d+")) -> "integer"
    else -> "number"
}

private class SpecOperation(val method: String, path: String, val body: JsonObject) {
    val name = "$method $path"
}

private val httpMethods = setOf("get", "put", "post", "delete", "patch", "head", "options")

private fun JsonElement.operations(): List<SpecOperation> =
    jsonObject.getValue("paths").jsonObject.flatMap { (path, item) ->
        item.jsonObject.filterKeys { it in httpMethods }.map { (method, body) ->
            SpecOperation(method, path, body.jsonObject)
        }
    }

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
                    reservationService = mockk<ReservationService>(),
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
