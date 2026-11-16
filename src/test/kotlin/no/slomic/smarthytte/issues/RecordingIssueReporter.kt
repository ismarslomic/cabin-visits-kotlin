package no.slomic.smarthytte.issues

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hand-written fake: remembers every report so a test can assert code, key and message. The lists are thread safe,
 * because a background task can write while the test reads.
 */
class RecordingIssueReporter : IssueReporter {
    data class Report(val code: IssueCode, val key: String, val message: String, val cause: Throwable?)

    val reports: MutableList<Report> = CopyOnWriteArrayList()
    val resolved: MutableList<Pair<IssueCode, String>> = CopyOnWriteArrayList()

    override suspend fun report(code: IssueCode, key: String, message: String, cause: Throwable?) {
        reports += Report(code, key, message, cause)
    }

    override suspend fun resolve(code: IssueCode, key: String) {
        resolved += code to key
    }
}
