package no.slomic.smarthytte.issues

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * An issue as the API returns it. [severity] is derived from the [code]. [version] is what `PUT /api/issues/{id}` must
 * send back: it changes when the status or the read state changes, not when the problem repeats.
 */
@Serializable
data class IssueResponse(
    @JsonSchema.Description("Identifier of the issue. Use it in PUT /api/issues/{id}.")
    @JsonSchema.Example("42")
    val id: Long,
    @JsonSchema.Description(
        "Stable code of the problem, for example JLR_APPROVAL_REQUIRED, JLR_AUTH_FAILED, JLR_REQUEST_FAILED, " +
            "PLACE_LOOKUP_FAILED, PLACE_MISSING, FETCH_FAILED, CALENDAR_PUSH_FAILED, CALENDAR_EVENT_LOST, " +
            "CALENDAR_EVENT_NOT_CREATABLE, CALENDAR_OUTBOX_STUCK, SENSOR_FETCH_FAILED or CHECK_IN_OUT_FAILED. " +
            "Clients map it to their own texts.",
    )
    @JsonSchema.Example("\"PLACE_MISSING\"")
    val code: IssueCode,
    @JsonSchema.Description(
        "What the problem is about, unique together with the code: for example PLACE_MISSING:<tripId> or a " +
            "reservation id. Use it to link to the object.",
    )
    @JsonSchema.Example("\"PLACE_MISSING:1767225600000\"")
    val key: String,
    @JsonSchema.Description("The severity of the code: WARN or ERROR.")
    @JsonSchema.Example("\"WARN\"")
    val severity: IssueSeverity,
    @JsonSchema.Description(
        "OPEN while the problem is there (or came back), RESOLVED when the code that reported it saw it was gone.",
    )
    @JsonSchema.Example("\"OPEN\"")
    val status: IssueStatus,
    @JsonSchema.Description(
        "English description of the latest occurrence. Never holds a response body, a URL, a token or a key. The " +
            "one exception is the JLR device login, which holds the approval link and code.",
    )
    @JsonSchema.Example("\"The trip 1767225600000 has no start city\"")
    val message: String,
    @JsonSchema.Description("The type of the exception behind the latest occurrence, or null if there was none.")
    @JsonSchema.Example("\"JlrRequestException\"")
    val causeType: String?,
    @JsonSchema.Description("How many times the problem has been reported, since it was first reported.")
    @JsonSchema.Example("3")
    val occurrenceCount: Int,
    @JsonSchema.Description("When the problem was first reported (UTC).")
    @JsonSchema.Example("\"2026-07-10T16:00:00Z\"")
    val firstOccurredTime: Instant,
    @JsonSchema.Description("When the problem was last reported (UTC).")
    @JsonSchema.Example("\"2026-07-10T18:30:00Z\"")
    val lastOccurredTime: Instant,
    @JsonSchema.Description("When the problem was resolved (UTC), or null while it is OPEN.")
    @JsonSchema.Example("\"2026-07-11T08:00:00Z\"")
    val resolvedTime: Instant?,
    @JsonSchema.Description(
        "True when an admin has marked the issue as read, false when it is unread. Same as readTime being set. " +
            "It is independent of status: a RESOLVED issue can be unread, but only OPEN unread issues are counted " +
            "by GET /api/issues/unread-count.",
    )
    @JsonSchema.Example("true")
    val read: Boolean,
    @JsonSchema.Description("When an admin marked the issue as read (UTC), or null if it is unread.")
    @JsonSchema.Example("\"2026-07-10T19:00:00Z\"")
    val readTime: Instant?,
    @JsonSchema.Description(
        "The version of the issue. Send it back unchanged in PUT /api/issues/{id}, an outdated version gives 412.",
    )
    @JsonSchema.Example("2")
    val version: Short,
)

/** The request body of `PUT /api/issues/{id}`: a full replace of the only writable property, [read]. */
@Serializable
data class IssueUpdate(
    @JsonSchema.Description(
        "The version of the issue that the client read, from GET /api/issues. An outdated version gives 412.",
    )
    @JsonSchema.Example("2")
    val version: Short,
    @JsonSchema.Description("True marks the issue as read, false marks it as unread.")
    @JsonSchema.Example("true")
    val read: Boolean,
)

/** The response of `GET /api/issues/unread-count`. */
@Serializable
data class UnreadCountResponse(
    @JsonSchema.Description("The number of issues that are not resolved and not read.")
    @JsonSchema.Example("2")
    val count: Int,
)

fun Issue.toResponse() = IssueResponse(
    id = id,
    code = code,
    key = key,
    severity = severity,
    status = status,
    message = message,
    causeType = causeType,
    occurrenceCount = occurrenceCount,
    firstOccurredTime = firstOccurredTime,
    lastOccurredTime = lastOccurredTime,
    resolvedTime = resolvedTime,
    read = readTime != null,
    readTime = readTime,
    version = version,
)
