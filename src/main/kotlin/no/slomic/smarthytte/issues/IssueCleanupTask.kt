package no.slomic.smarthytte.issues

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.slomic.smarthytte.common.runCatchingLogged
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private val CLEANUP_INTERVAL = 1.hours

/** Starts the hourly [IssueCleanup]. It runs around the clock, apart from the sync loop that is daytime only. */
fun Application.startIssueCleanup(
    repository: IssueRepository,
    approvalExpiry: Duration,
    interval: Duration = CLEANUP_INTERVAL,
) {
    val cleanup = IssueCleanup(repository, approvalExpiry)
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    monitor.subscribe(ApplicationStopped) { scope.cancel() }
    scope.launch {
        while (isActive) {
            runCatchingLogged("Issue cleanup") { cleanup.cleanOnce() }
            delay(interval)
        }
    }
}
