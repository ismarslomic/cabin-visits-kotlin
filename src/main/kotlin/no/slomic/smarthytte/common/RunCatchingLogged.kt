package no.slomic.smarthytte.common

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CancellationException

private val defaultLogger: Logger = KtorSimpleLogger("no.slomic.smarthytte.common.RunCatchingLogged")

/**
 * Runs [block] and logs any exception as an error with [label], instead of letting it escape.
 *
 * Used around the steps of the background loops, so one failing step does not end the loop until the next restart.
 * [CancellationException] is always rethrown, otherwise stopping the application or cancelling the scope would break.
 *
 * @return `true` if [block] completed, `false` if it threw
 */
@Suppress("TooGenericExceptionCaught")
suspend fun runCatchingLogged(label: String, logger: Logger = defaultLogger, block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    logger.error("$label failed: ${e.message}", e)
    false
}
