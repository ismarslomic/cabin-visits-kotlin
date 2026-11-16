package no.slomic.smarthytte.issues

/** Hand-written fake: remembers every report so a test can assert code, key and message. */
class RecordingIssueReporter : IssueReporter {
    data class Report(val code: IssueCode, val key: String, val message: String, val cause: Throwable?)

    val reports = mutableListOf<Report>()
    val resolved = mutableListOf<Pair<IssueCode, String>>()

    override suspend fun report(code: IssueCode, key: String, message: String, cause: Throwable?) {
        reports += Report(code, key, message, cause)
    }

    override suspend fun resolve(code: IssueCode, key: String) {
        resolved += code to key
    }
}
