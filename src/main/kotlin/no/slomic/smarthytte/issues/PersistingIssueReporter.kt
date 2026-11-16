package no.slomic.smarthytte.issues

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.common.runCatchingLogged

private val defaultLogger: Logger = KtorSimpleLogger("no.slomic.smarthytte.issues.PersistingIssueReporter")

/**
 * Logs every issue through [logging] first, then stores it. A storage failure is logged and never reaches the caller:
 * reporting a problem must not fail the work that found it (a cancellation is still passed on).
 */
class PersistingIssueReporter(
    private val logging: IssueReporter,
    private val repository: IssueRepository,
    private val logger: Logger = defaultLogger,
) : IssueReporter {
    override suspend fun report(code: IssueCode, key: String, message: String, cause: Throwable?) {
        logging.report(code = code, key = key, message = message, cause = cause)
        runCatchingLogged(label = "Store issue ${code.name} key=$key", logger = logger) {
            repository.record(
                code = code,
                key = key,
                message = message,
                causeType = cause?.let { it::class.simpleName },
                actor = SYSTEM_ACTOR,
            )
        }
    }

    override suspend fun resolve(code: IssueCode, key: String) {
        logging.resolve(code = code, key = key)
        runCatchingLogged(label = "Resolve issue ${code.name} key=$key", logger = logger) {
            val wasOpen = repository.resolve(code = code, key = key, actor = SYSTEM_ACTOR)
            if (!wasOpen) logger.debug("[${code.name}] key=$key has no open issue")
        }
    }
}
