package no.slomic.smarthytte.issues

enum class IssueSeverity { WARN, ERROR }

/**
 * Stable codes for operational problems. The code and the dedupe key are the contract, the message is for humans.
 *
 * The names are stored in `issue.code`, so a name can not be changed without a data migration. The severity is not
 * stored, it is always read from here.
 */
enum class IssueCode(val severity: IssueSeverity) {
    JLR_APPROVAL_REQUIRED(IssueSeverity.WARN),
    JLR_AUTH_FAILED(IssueSeverity.ERROR),
    JLR_REQUEST_FAILED(IssueSeverity.ERROR),
    PLACE_LOOKUP_FAILED(IssueSeverity.WARN),
    PLACE_MISSING(IssueSeverity.WARN),
    FETCH_FAILED(IssueSeverity.ERROR),
    CALENDAR_PUSH_FAILED(IssueSeverity.ERROR),
    CALENDAR_EVENT_LOST(IssueSeverity.ERROR),
    CALENDAR_EVENT_NOT_CREATABLE(IssueSeverity.WARN),
    CALENDAR_OUTBOX_STUCK(IssueSeverity.ERROR),
    SENSOR_FETCH_FAILED(IssueSeverity.ERROR),
    CHECK_IN_OUT_FAILED(IssueSeverity.WARN),
}
