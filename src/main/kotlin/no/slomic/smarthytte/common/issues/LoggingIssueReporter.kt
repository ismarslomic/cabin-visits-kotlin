package no.slomic.smarthytte.common.issues

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger

private val defaultLogger: Logger = KtorSimpleLogger("no.slomic.smarthytte.common.issues.LoggingIssueReporter")

/** Writes each issue as `[CODE] key=... message` at the severity level of the code. */
class LoggingIssueReporter(private val logger: Logger = defaultLogger) : IssueReporter {
    override fun report(code: IssueCode, key: String, message: String, cause: Throwable?) {
        val line = "[${code.name}] key=$key $message"
        when (code.severity) {
            IssueSeverity.WARN -> logger.warn(line, cause)
            IssueSeverity.ERROR -> logger.error(line, cause)
        }
    }
}
