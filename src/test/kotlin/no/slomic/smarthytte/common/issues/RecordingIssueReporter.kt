package no.slomic.smarthytte.common.issues

/** Hand-written fake: remembers every report so a test can assert code, key and message. */
class RecordingIssueReporter : IssueReporter {
    data class Report(val code: IssueCode, val key: String, val message: String, val cause: Throwable?)

    val reports = mutableListOf<Report>()

    override fun report(code: IssueCode, key: String, message: String, cause: Throwable?) {
        reports += Report(code, key, message, cause)
    }
}
