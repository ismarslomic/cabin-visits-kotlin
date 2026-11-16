package no.slomic.smarthytte.issues

import kotlin.time.Instant

enum class IssueStatus {
    /** The problem is still there, or was reported again after it had been resolved. */
    OPEN,

    /** The code that reported the problem has seen that it is gone. */
    RESOLVED,
}

/** A stored operational problem, one per [code] and [key]. */
data class Issue(
    val id: Long,
    val code: IssueCode,
    val key: String,
    val status: IssueStatus,
    val message: String,
    val causeType: String?,
    val occurrenceCount: Int,
    val firstOccurredTime: Instant,
    val lastOccurredTime: Instant,
    val resolvedTime: Instant?,
    val readTime: Instant?,
    val version: Short,
) {
    val severity: IssueSeverity get() = code.severity
}
