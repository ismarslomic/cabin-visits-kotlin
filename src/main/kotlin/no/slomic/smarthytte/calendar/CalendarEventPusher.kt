package no.slomic.smarthytte.calendar

import io.ktor.http.HttpStatusCode
import io.ktor.util.logging.KtorSimpleLogger
import no.slomic.smarthytte.reservations.ReservationSystemService

/** The result of pushing the changes of one reservation. */
sealed interface PushOutcome {
    /** Pushed, or nothing left to push (also a deliberate skip): the outbox rows are deleted. */
    data object Done : PushOutcome

    /** Failed: the rows stay and are tried again. */
    data class Retry(val failure: CalendarCallResult.Failure) : PushOutcome
}

private val logger = KtorSimpleLogger("no.slomic.smarthytte.calendar.CalendarEventPusher")
private val notFound = HttpStatusCode.NotFound.value
private val gone = HttpStatusCode.Gone.value
private val conflict = HttpStatusCode.Conflict.value
private val badRequest = HttpStatusCode.BadRequest.value

/**
 * Turns the outbox operations of one reservation into calls to the [CalendarGateway]. The event id is the reservation
 * id. Several pending changes collapse into one push, because an insert or a patch sends the current state of the
 * reservation; the title is only set by an insert.
 */
class CalendarEventPusher(
    private val gateway: CalendarGateway,
    private val reservationService: ReservationSystemService,
) {
    /** Removes the event. A missing (`404`) or already deleted (`410`) event is success. */
    suspend fun delete(reservationId: String): PushOutcome = when (val result = gateway.delete(reservationId)) {
        CalendarCallResult.Success -> {
            logger.info("Deleted the calendar event of reservation $reservationId")
            PushOutcome.Done
        }

        is CalendarCallResult.Failure -> when (result.status) {
            // 404 can also mean that the service account lost access to the calendar
            notFound -> {
                logger.warn("The calendar event of reservation $reservationId was not found when deleting it")
                PushOutcome.Done
            }

            gone -> PushOutcome.Done

            else -> PushOutcome.Retry(result)
        }
    }

    /**
     * Pushes the current times of the reservation: an insert if the oldest pending operation is [firstOperation]
     * `INSERT`, otherwise a patch. A reservation that is gone has nothing to push (its delete row follows).
     */
    suspend fun upsert(reservationId: String, firstOperation: CalendarOperation): PushOutcome {
        val reservation = reservationService.byId(reservationId)
        val times = reservation?.let { CalendarEventTimes(it.startTime, it.endTime) }

        return when {
            times == null -> {
                logger.info("Reservation $reservationId is gone, nothing to push to the calendar")
                PushOutcome.Done
            }

            firstOperation == CalendarOperation.INSERT -> insert(reservationId, times)

            else -> patch(reservationId, times)
        }
    }

    /** `409` means the event exists (a retry after a lost answer): patch it instead. */
    private suspend fun insert(reservationId: String, times: CalendarEventTimes): PushOutcome =
        if (!isInsertableEventId(reservationId)) {
            logger.error("Reservation $reservationId has an id that Google cannot insert, the event is not created")
            PushOutcome.Done
        } else {
            when (val result = gateway.insert(reservationId, times)) {
                CalendarCallResult.Success -> pushed("created", reservationId)

                is CalendarCallResult.Failure -> when (result.status) {
                    conflict -> outcome(gateway.patch(reservationId, times, restore = true), "restored", reservationId)

                    // A request Google rejects as invalid will never work: do not keep the row
                    badRequest -> terminal(reservationId, result)

                    else -> PushOutcome.Retry(result)
                }
            }
        }

    /** A patch of an event that is missing or cancelled restores it, and as a last resort creates it. */
    private suspend fun patch(reservationId: String, times: CalendarEventTimes): PushOutcome =
        when (val result = gateway.patch(reservationId, times, restore = false)) {
            CalendarCallResult.Success -> pushed("moved", reservationId)

            is CalendarCallResult.Failure ->
                if (result.status == notFound || result.status == gone) {
                    restore(reservationId, times)
                } else {
                    PushOutcome.Retry(result)
                }
        }

    private suspend fun restore(reservationId: String, times: CalendarEventTimes): PushOutcome =
        when (val result = gateway.patch(reservationId, times, restore = true)) {
            CalendarCallResult.Success -> pushed("restored", reservationId)

            is CalendarCallResult.Failure ->
                if (result.status in setOf(badRequest, notFound, gone)) {
                    insertAfterRestore(reservationId, times)
                } else {
                    PushOutcome.Retry(result)
                }
        }

    private suspend fun insertAfterRestore(reservationId: String, times: CalendarEventTimes): PushOutcome =
        if (!isInsertableEventId(reservationId)) {
            logger.error("The calendar event of reservation $reservationId is gone and its id cannot be inserted")
            PushOutcome.Done
        } else {
            when (val result = gateway.insert(reservationId, times)) {
                CalendarCallResult.Success -> pushed("created again", reservationId)

                is CalendarCallResult.Failure ->
                    // An insert after a failed restore that answers 409 or 400 is terminal
                    if (result.status == conflict || result.status == badRequest) {
                        terminal(reservationId, result)
                    } else {
                        PushOutcome.Retry(result)
                    }
            }
        }

    private fun outcome(result: CalendarCallResult, what: String, reservationId: String): PushOutcome = when (result) {
        CalendarCallResult.Success -> pushed(what, reservationId)
        is CalendarCallResult.Failure -> PushOutcome.Retry(result)
    }

    private fun pushed(what: String, reservationId: String): PushOutcome {
        logger.info("The calendar event of reservation $reservationId was $what")
        return PushOutcome.Done
    }

    private fun terminal(reservationId: String, failure: CalendarCallResult.Failure): PushOutcome {
        logger.error(
            "Giving up on the calendar event of reservation $reservationId: ${failure.status} ${failure.reason}",
        )
        return PushOutcome.Done
    }
}
