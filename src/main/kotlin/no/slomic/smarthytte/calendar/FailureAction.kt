package no.slomic.smarthytte.calendar

import io.ktor.http.HttpStatusCode

/** What the outbox does with a row whose push failed. */
enum class FailureAction {
    /** A temporary problem (network, 429, 5xx, rate limit): try again after the normal backoff. */
    RETRY,

    /** Google's protective quota (403 `quotaExceeded`): try again at the longest wait and alert. */
    RETRY_SLOWLY,

    /** Probably a configuration problem (credentials, no permission on the calendar): keep the row and alert. */
    KEEP_AND_ALERT,
}

private const val SERVER_ERRORS_FROM = 500
private const val SERVER_ERRORS_TO = 599
private val rateLimitReasons = setOf("rateLimitExceeded", "userRateLimitExceeded")

private val statusTooManyRequests = HttpStatusCode.TooManyRequests.value
private val statusForbidden = HttpStatusCode.Forbidden.value

/** Classifies by the Google error reason first (it can be null when the answer is not JSON), then by the status. */
fun CalendarCallResult.Failure.action(): FailureAction = when (status) {
    null -> FailureAction.RETRY
    statusTooManyRequests, in SERVER_ERRORS_FROM..SERVER_ERRORS_TO -> FailureAction.RETRY
    statusForbidden if reason in rateLimitReasons -> FailureAction.RETRY
    statusForbidden if reason == "quotaExceeded" -> FailureAction.RETRY_SLOWLY
    else -> FailureAction.KEEP_AND_ALERT
}

/** Google accepts the characters `a`-`v` and `0`-`9`, 5-1024 long, as a client chosen event id. */
private val insertableEventId = Regex("^[a-v0-9]{5,1024}$")

/** An old reservation id (110 of 139 start with `_`) cannot be inserted, only patched or deleted. */
fun isInsertableEventId(eventId: String): Boolean = insertableEventId.matches(eventId)
