package no.slomic.smarthytte.utils

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.slomic.smarthytte.ApiDependencies
import no.slomic.smarthytte.configureApiRoutes
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val HTTP_SERVER_ERROR = 500
private const val SPEC_PATH = "openapi/cabin-visits.json"
private val httpMethods = setOf("get", "put", "post", "delete", "patch")

/** A response that a test received: the HTTP method in lower case, the concrete path and the status code. */
private data class ObservedResponse(val method: String, val path: String, val status: Int)

/**
 * Checks that every status code the OpenAPI spec documents for a route is answered by at least one test.
 *
 * The test apps that go through [configureTestApiRoutes] record every response they send. When the whole suite has run
 * (see [ResponseCodeAuditListener]), each documented `method path status` of `openapi/cabin-visits.json` must have been
 * observed, or be listed in [untestable] with a reason. A code that nobody tests is a promise to API clients that
 * nothing verifies, in particular the authorisation codes (401, 403) and the 503 of a missing Cloudflare Access setup.
 */
object ResponseCodeAudit {
    private val observed = ConcurrentHashMap.newKeySet<ObservedResponse>()

    /** `method path status` of documented codes that cannot be provoked in a test, each with the reason. */
    val untestable: Map<String, String> = emptyMap()

    fun record(method: String, path: String, status: Int) {
        observed.add(ObservedResponse(method.lowercase(), path, status))
    }

    /** Documented `method template status` that no test received, sorted. */
    fun missing(): List<String> {
        val paths = Json.parseToJsonElement(File(SPEC_PATH).readText(Charsets.UTF_8))
            .jsonObject.getValue("paths").jsonObject

        return paths.flatMap { (template, item) ->
            val matcher = Regex(template.replace(Regex("\\{[^/]+}"), "[^/]+"))
            item.jsonObject.filterKeys { it in httpMethods }.flatMap { (method, operation) ->
                operation.jsonObject.getValue("responses").jsonObject.keys.map { status ->
                    // "default" is the unexpected server error: a 500 stands for it
                    val code = if (status == "default") HTTP_SERVER_ERROR else status.toInt()
                    val isObserved = observed.any {
                        it.method == method && it.status == code && matcher.matches(it.path)
                    }
                    "$method $template $status".takeUnless { isObserved }
                }
            }
        }.filterNotNull().filterNot { it in untestable }.sorted()
    }
}

private val RecordResponses = createApplicationPlugin("RecordResponses") {
    on(ResponseSent) { call ->
        call.response.status()?.value?.let {
            ResponseCodeAudit.record(call.request.httpMethod.value, call.request.path(), it)
        }
    }
    // An exception that no route handles becomes a 500, which the server sends without ResponseSent
    on(CallFailed) { call, _ ->
        ResponseCodeAudit.record(call.request.httpMethod.value, call.request.path(), HTTP_SERVER_ERROR)
    }
}

/** Records the responses of a test app for the [ResponseCodeAudit]. */
fun Application.recordResponses() {
    install(RecordResponses)
}

/** [configureApiRoutes] for a test app, which also records the responses for the [ResponseCodeAudit]. */
fun Application.configureTestApiRoutes(dependencies: ApiDependencies) {
    recordResponses()
    configureApiRoutes(dependencies)
}
