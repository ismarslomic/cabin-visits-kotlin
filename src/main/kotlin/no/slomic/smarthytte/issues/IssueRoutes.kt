@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)

package no.slomic.smarthytte.issues

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.auth.authorized
import no.slomic.smarthytte.common.ApiError
import no.slomic.smarthytte.common.RequestBody
import no.slomic.smarthytte.common.allPropertiesRequired
import no.slomic.smarthytte.common.receiveJsonBody
import no.slomic.smarthytte.common.respondApiBodyError
import no.slomic.smarthytte.guests.AppRole

private const val ISSUES_PATH = "/api/issues"
private const val UNREAD_COUNT_PATH = "/api/issues/unread-count"
private const val ISSUE_PATH = "/api/issues/{id}"
private const val EXAMPLE_ISSUE_ID = 42
private const val ISSUE_NOT_FOUND = "The issue does not exist"

/** The operational problems for the admin. The service user (MagicMirror) gets 403: the messages are not for it. */
fun Application.configureIssueRoutes(issueService: IssueService) {
    routing {
        authorized(AppRole.ADMIN) {
            get(ISSUES_PATH) {
                val parameters = call.request.queryParameters

                when (val result = issueService.list(parameters["read"], parameters["status"], parameters["limit"])) {
                    is IssueListResult.Listed -> call.respond(result.issues.map { it.toResponse() })

                    is IssueListResult.Invalid ->
                        call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_REQUEST, result.message))
                }
            }.describeListIssues()

            get(UNREAD_COUNT_PATH) {
                call.respond(UnreadCountResponse(issueService.unreadCount()))
            }.describeUnreadCount()

            put(ISSUE_PATH) {
                val actor = checkNotNull(call.principal<UserPrincipal>()) { "Authenticated route without a user" }
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND, ISSUE_NOT_FOUND))
                val update = when (val body = call.receiveJsonBody<IssueUpdate>()) {
                    is RequestBody.Ok -> body.value
                    else -> return@put call.respondApiBodyError(body)
                }

                when (val result = issueService.markRead(actor.id, id, update)) {
                    is IssueUpdateResult.Updated -> call.respond(result.issue.toResponse())

                    IssueUpdateResult.NotFound ->
                        call.respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND, ISSUE_NOT_FOUND))

                    IssueUpdateResult.VersionMismatch -> call.respond(
                        HttpStatusCode.PreconditionFailed,
                        ApiError(ApiError.VERSION_MISMATCH, "The issue has changed since the version in the body"),
                    )
                }
            }.describeUpdateIssue()
        }
    }
}

private fun Route.describeListIssues() = describe {
    operationId = "listIssues"
    tag("Issues")
    summary = "List issues"
    parameters {
        query("read") {
            description = "true for read issues only, false for unread issues only. Omit for both."
            required = false
            example = GenericElement(false)
            schema = JsonSchema(type = JsonType.BOOLEAN)
        }
        query("status") {
            description = "OPEN or RESOLVED. Omit for both."
            required = false
            example = GenericElement("OPEN")
            schema = JsonSchema(type = JsonType.STRING)
        }
        query("limit") {
            description = "The most issues to return, from 1 to $MAX_ISSUE_LIMIT. Default $DEFAULT_ISSUE_LIMIT."
            required = false
            example = GenericElement(DEFAULT_ISSUE_LIMIT)
            schema = JsonSchema(type = JsonType.INTEGER)
        }
    }
    description = "Admin only. The operational problems that the application reported, with the latest " +
        "occurrence first (lastOccurredTime, then id, both descending). A problem is one issue that counts its " +
        "occurrences, and a resolved problem that comes back becomes OPEN and unread again. Resolved issues are " +
        "deleted 30 days after they were resolved or read, whichever is later. The service user gets 403. Errors " +
        "are JSON with a code and a message."
    responses {
        HttpStatusCode.OK { description = "The issues that match the filters, as a plain list." }
        HttpStatusCode.BadRequest { description = "read, status or limit is not valid." }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The caller is not an admin, or is the service user." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}

private fun Route.describeUnreadCount() = describe {
    operationId = "countUnreadIssues"
    tag("Issues")
    summary = "Count unread issues"
    description = "Admin only. The number of issues that are not resolved and not read, for a badge in the " +
        "application. A resolved issue is not counted, even when it is unread. The service user gets 403."
    responses {
        HttpStatusCode.OK { description = "The number of unread issues that are not resolved." }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The caller is not an admin, or is the service user." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}

private fun Route.describeUpdateIssue() = describe {
    operationId = "updateIssue"
    tag("Issues")
    parameters {
        path("id") {
            description = "Identifier of the issue, from GET /api/issues."
            example = GenericElement(EXAMPLE_ISSUE_ID)
            schema = JsonSchema(type = JsonType.INTEGER)
        }
    }
    summary = "Mark an issue as read or unread"
    description = "Admin only. A full replace: the body must carry the version the client read and read. " +
        "Marking an issue the way it already is changes nothing and keeps the version. Any issue can be marked, " +
        "also a resolved one. Errors are JSON with a code and a message."
    requestBody {
        description = "The version the client read and the wanted read state."
        required = true
        schema = jsonSchema<IssueUpdate>().allPropertiesRequired()
    }
    responses {
        HttpStatusCode.OK { description = "The updated issue." }
        HttpStatusCode.BadRequest { description = "The body is not valid JSON, or the version or read is missing." }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The caller is not an admin, or is the service user." }
        HttpStatusCode.NotFound { description = "The issue does not exist." }
        HttpStatusCode.PreconditionFailed { description = "The issue has changed since the version in the body." }
        HttpStatusCode.PayloadTooLarge { description = "The request body is larger than 64 KB." }
        HttpStatusCode.UnsupportedMediaType { description = "The Content-Type is not application/json." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}
