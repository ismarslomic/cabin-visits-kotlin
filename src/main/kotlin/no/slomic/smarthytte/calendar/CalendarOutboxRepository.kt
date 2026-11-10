@file:Suppress("UnusedReceiverParameter")

package no.slomic.smarthytte.calendar

import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.time.Clock

/*
 * The enqueue functions are not `suspend` and open no transaction: call them inside the transaction of the reservation
 * write, so both are committed or neither is, and a calendar that is down never fails the write. A `suspend` function
 * would hop to another thread and start a second transaction that waits for the write lock of the first.
 *
 * Exposed finds the current transaction itself, so the `Transaction` receiver is never used. It is there so that the
 * compiler rejects a call outside a transaction.
 */

/** Queues the creation of the calendar event of the reservation. */
fun Transaction.enqueueCalendarEventCreated(reservationId: String) =
    enqueueCalendarOperation(reservationId, CalendarOperation.INSERT)

/** Queues the move of the calendar event of the reservation (its times changed). */
fun Transaction.enqueueCalendarEventMoved(reservationId: String) =
    enqueueCalendarOperation(reservationId, CalendarOperation.UPDATE)

/** Queues the removal of the calendar event of the reservation. */
fun Transaction.enqueueCalendarEventDeleted(reservationId: String) =
    enqueueCalendarOperation(reservationId, CalendarOperation.DELETE)

private fun enqueueCalendarOperation(reservationId: String, operation: CalendarOperation) {
    val now = Clock.System.now().truncatedToMillis()

    CalendarOutboxTable.insert {
        it[this.reservationId] = reservationId
        it[this.operation] = operation
        it[createdTime] = now
        it[nextAttemptTime] = now
    }
}
