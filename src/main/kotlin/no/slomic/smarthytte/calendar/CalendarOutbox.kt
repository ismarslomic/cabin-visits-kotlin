package no.slomic.smarthytte.calendar

import no.slomic.smarthytte.common.suspendReadTransaction
import no.slomic.smarthytte.common.suspendTransaction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant

/** The longest `last_error` that is stored. */
const val MAX_LAST_ERROR_LENGTH = 500

/** One pending calendar operation. */
data class OutboxRow(val id: Int, val reservationId: String, val operation: CalendarOperation, val attempts: Int)

/** How many operations wait, and since when the oldest one has been waiting. */
data class OutboxStatus(val pending: Long, val oldestCreatedTime: Instant?)

/**
 * Reads and updates the rows of the outbox for the drain. The rows are written by the reservation writes
 * (`enqueueCalendarEvent*`), in their transaction. Every call here is its own short transaction: a push to Google never
 * runs inside one.
 */
class CalendarOutbox {
    /** The rows whose next attempt is due, oldest id first, at most [limit]. */
    suspend fun dueRows(now: Instant, limit: Int): List<OutboxRow> = suspendReadTransaction {
        CalendarOutboxTable.selectAll()
            .where { CalendarOutboxTable.nextAttemptTime lessEq now }
            .orderBy(CalendarOutboxTable.id, SortOrder.ASC)
            .limit(limit)
            .map {
                OutboxRow(
                    it[CalendarOutboxTable.id].value,
                    it[CalendarOutboxTable.reservationId],
                    it[CalendarOutboxTable.operation],
                    it[CalendarOutboxTable.attempts],
                )
            }
    }

    /** Deletes the processed rows only: a change written while the push ran has its own row and stays. */
    suspend fun delete(ids: List<Int>) {
        suspendTransaction { CalendarOutboxTable.deleteWhere { CalendarOutboxTable.id inList ids } }
    }

    /** Records one more failed attempt, keeps the (truncated) error and sets when to try again. */
    suspend fun markFailed(ids: List<Int>, error: String, nextAttemptTime: Instant) {
        suspendTransaction {
            CalendarOutboxTable.update({ CalendarOutboxTable.id inList ids }) {
                it[attempts] = attempts + 1
                it[lastError] = error.take(MAX_LAST_ERROR_LENGTH)
                it[this.nextAttemptTime] = nextAttemptTime
            }
        }
    }

    suspend fun status(): OutboxStatus = suspendReadTransaction {
        val count = CalendarOutboxTable.id.count()
        val oldest = CalendarOutboxTable.createdTime.min()

        CalendarOutboxTable.select(count, oldest).single().let { OutboxStatus(it[count], it[oldest]) }
    }
}
