package no.slomic.smarthytte.issues

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** A RESOLVED issue is kept this long after the later of its resolve time and its read time. */
internal val RESOLVED_RETENTION = 30.days

private val defaultLogger: Logger = KtorSimpleLogger("no.slomic.smarthytte.issues.IssueCleanup")

/**
 * Housekeeping of the issues, run once an hour by [startIssueCleanup]. [approvalExpiry] is how long the owner has to
 * approve a JLR device login:
 * 1. Resolves `JLR_APPROVAL_REQUIRED` when it was last reported more than [approvalExpiry] ago. `JlrAuthService`
 *    resolves it when the login ends, this is the fallback for a login that was cut off (for example by a restart).
 * 2. Deletes RESOLVED issues [RESOLVED_RETENTION] after the later of `resolvedTime` and `readTime`, read or not. An
 *    OPEN issue is never deleted.
 */
class IssueCleanup(
    private val repository: IssueRepository,
    private val approvalExpiry: Duration,
    private val clock: Clock = Clock.System,
    private val logger: Logger = defaultLogger,
) {
    suspend fun cleanOnce() {
        val now = clock.now()
        val expired = repository.resolveOpenBefore(
            code = IssueCode.JLR_APPROVAL_REQUIRED,
            occurredBefore = now - approvalExpiry,
            actor = SYSTEM_ACTOR,
        )
        val deleted = repository.deleteResolvedBefore(cutoff = now - RESOLVED_RETENTION)

        if (expired > 0 || deleted > 0) logger.info("Issue cleanup: resolvedExpired=$expired deleted=$deleted")
    }
}
