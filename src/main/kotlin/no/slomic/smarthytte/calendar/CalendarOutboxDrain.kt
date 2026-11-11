package no.slomic.smarthytte.calendar

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.sync.Mutex
import no.slomic.smarthytte.common.runCatchingLogged
import no.slomic.smarthytte.properties.CalendarOutboxProperties
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val logger = KtorSimpleLogger("no.slomic.smarthytte.calendar.CalendarOutboxDrain")

/**
 * Pushes the pending rows of the outbox to Google Calendar. One run is single-flight (a second call while one runs
 * does nothing), reads at most `batchSize` due rows, pushes the rows of one reservation together (the oldest first,
 * changes collapse into one push) and deletes only the rows it pushed. A failed push keeps its rows with a growing
 * wait; nothing is given up except the cases of [CalendarEventPusher] that can never work. A reservation write never
 * waits for this.
 */
class CalendarOutboxDrain(
    private val outbox: CalendarOutbox,
    private val pusher: CalendarEventPusher,
    private val properties: CalendarOutboxProperties,
    private val metrics: CalendarOutboxMetrics = CalendarOutboxMetrics(),
    private val clock: Clock = Clock.System,
) {
    private val running = Mutex()

    suspend fun drainOnce() {
        if (!running.tryLock()) return

        try {
            val now = clock.now()
            outbox.dueRows(now, properties.batchSize).groupBy { it.reservationId }.forEach { (reservationId, rows) ->
                runCatchingLogged("Pushing the calendar changes of reservation $reservationId") {
                    push(reservationId, rows, now)
                }
            }
            reportStatus(now)
        } finally {
            running.unlock()
        }
    }

    private suspend fun push(reservationId: String, rows: List<OutboxRow>, now: Instant) {
        val ids = rows.map { it.id }
        val outcome = if (rows.last().operation == CalendarOperation.DELETE) {
            pusher.delete(reservationId)
        } else {
            pusher.upsert(reservationId, rows.first().operation)
        }

        when (outcome) {
            PushOutcome.Done -> outbox.delete(ids)

            is PushOutcome.Retry -> {
                val failure = outcome.failure
                val action = failure.action()
                val wait = if (action == FailureAction.RETRY_SLOWLY) {
                    properties.backoffCapSeconds.seconds
                } else {
                    backoff(rows.maxOf { it.attempts } + 1)
                }
                if (action != FailureAction.RETRY) {
                    logger.error(
                        "The calendar push for reservation $reservationId needs attention (${action.name}): " +
                            "${failure.status} ${failure.reason}",
                    )
                } else {
                    logger.warn(
                        "The calendar push for reservation $reservationId failed, will retry: ${failure.status}",
                    )
                }
                outbox.markFailed(ids, "${failure.status} ${failure.reason}: ${failure.message}", now + wait)
            }
        }
    }

    /** The wait after the [attempt]th failure: start, times factor for each further failure, at most the cap. */
    private fun backoff(attempt: Int): Duration {
        val seconds = properties.backoffStartSeconds * properties.backoffFactor.pow(attempt - 1)

        return seconds.coerceAtMost(properties.backoffCapSeconds.toDouble()).seconds
    }

    private suspend fun reportStatus(now: Instant) {
        val status = outbox.status()
        val oldestAge = status.oldestCreatedTime?.let { (now - it).inWholeSeconds } ?: 0L
        metrics.update(status.pending, oldestAge)

        if (oldestAge > properties.stuckThresholdMinutes.minutes.inWholeSeconds) {
            logger.error(
                "The calendar outbox has stuck rows: ${status.pending} pending, the oldest has waited $oldestAge s",
            )
        }
    }
}
