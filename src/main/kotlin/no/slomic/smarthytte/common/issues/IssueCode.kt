package no.slomic.smarthytte.common.issues

enum class IssueSeverity { WARN, ERROR }

/** Stable codes for operational problems. The code and the dedupe key are the contract, the message is for humans. */
enum class IssueCode(val severity: IssueSeverity) {
    JLR_APPROVAL_REQUIRED(IssueSeverity.WARN),
    JLR_AUTH_FAILED(IssueSeverity.ERROR),
    JLR_REQUEST_FAILED(IssueSeverity.ERROR),
    PLACE_LOOKUP_FAILED(IssueSeverity.WARN),
    PLACE_MISSING(IssueSeverity.WARN),
    FETCH_FAILED(IssueSeverity.ERROR),
}
