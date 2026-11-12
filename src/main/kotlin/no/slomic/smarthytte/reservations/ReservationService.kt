package no.slomic.smarthytte.reservations

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.common.runCatchingLogged
import no.slomic.smarthytte.common.utcDateNow
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.GuestAvatarService
import no.slomic.smarthytte.guests.GuestQueryService
import kotlin.time.Clock

/** Pending calendar operations at which writes are refused. */
internal const val MAX_PENDING_CALENDAR_OPERATIONS = 200L

/** The result of reading one reservation. */
sealed interface ReservationReadResult {
    data class Found(val reservation: ReservationResponse) : ReservationReadResult

    /** The reservation does not exist, or the caller is a guest user who is not a guest of it (decision 68). */
    data object NotFound : ReservationReadResult
}

/** The result of listing reservations. */
sealed interface ReservationListResult {
    data class Listed(val reservations: List<ReservationResponse>) : ReservationListResult

    data class InvalidRange(val message: String) : ReservationListResult
}

/**
 * Reads and writes reservations. The writes validate the request, let the repository check the version, the overlap and
 * the guests inside its transaction, and map the outcome to a [ReservationWriteResult].
 *
 * @param pendingCalendarOperations how many calendar operations wait in the outbox; at [maxPendingCalendarOperations]
 * or more, writes are refused (503) so the queue and the Google quota cannot run away.
 * @param onTimesChanged runs after the commit of a create or a change of the times, with the reservation id: it
 * derives check-in/out and trip links for that reservation. A failure is logged and never fails the write.
 */
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val guestQueryService: GuestQueryService,
    private val guestAvatarService: GuestAvatarService,
    private val clock: Clock = Clock.System,
    private val pendingCalendarOperations: suspend () -> Long = { 0L },
    private val maxPendingCalendarOperations: Long = MAX_PENDING_CALENDAR_OPERATIONS,
    private val onTimesChanged: suspend (String) -> Unit = {},
) {
    /**
     * The reservations that overlap the UTC dates [from] (inclusive) and [toExclusive] (exclusive). A [caller] with the
     * `GUEST_USER` role only gets the reservations that link their guest; everyone else, and the service user
     * (a null [caller]), gets all of them.
     */
    suspend fun list(caller: UserPrincipal?, from: LocalDate?, toExclusive: LocalDate?): ReservationListResult {
        if (from != null && toExclusive != null && toExclusive <= from) {
            return ReservationListResult.InvalidRange("to must be after from")
        }

        val reservations = reservationRepository.reservations(caller.visibleGuestId(), from, toExclusive)
        return ReservationListResult.Listed(toResponses(reservations))
    }

    suspend fun byId(caller: UserPrincipal?, id: String): ReservationReadResult {
        val reservation = reservationRepository.reservationById(id)
            ?.takeIf { r -> caller.visibleGuestId()?.let { it in r.guestIds } ?: true }
            ?: return ReservationReadResult.NotFound

        return ReservationReadResult.Found(toResponses(listOf(reservation)).single())
    }

    suspend fun create(actor: UserPrincipal, request: ReservationCreate): ReservationWriteResult {
        val guestIds = request.guests.map { it.guestId }

        return invalidReservation(request.startTime, request.endTime, guestIds, today(), checkTimes = true)
            ?: queueFull()
            ?: guardedWrite {
                when (
                    val outcome = reservationRepository.createReservation(
                        request.startTime,
                        request.endTime,
                        guestIds.map(::ReservationGuest),
                        actor.id,
                    )
                ) {
                    is ReservationCreateOutcome.Created -> {
                        derive(outcome.reservation.id)
                        ReservationWriteResult.Created(toResponses(listOf(outcome.reservation)).single())
                    }

                    ReservationCreateOutcome.Overlap -> ReservationWriteResult.Conflict

                    is ReservationCreateOutcome.UnknownGuests -> unknownGuests(outcome.guestIds)
                }
            }
    }

    /** Order: exists (404), version (412), validation (400), then the transaction checks overlap (409) and guests. */
    suspend fun update(actor: UserPrincipal, id: String, request: ReservationUpdate): ReservationWriteResult {
        val stored = reservationRepository.reservationById(id)
        val guestIds = request.guests.map { it.guestId }

        return when {
            stored == null -> ReservationWriteResult.NotFound

            stored.version != request.version -> ReservationWriteResult.VersionMismatch

            else -> invalidReservation(
                request.startTime,
                request.endTime,
                guestIds,
                today(),
                // The range rules apply when the times change, so an old reservation can still get other guests
                checkTimes = !stored.hasTimes(request.startTime, request.endTime),
            ) ?: queueFull() ?: guardedWrite { persistUpdate(actor, id, request, guestIds) }
        }
    }

    suspend fun delete(actor: UserPrincipal, id: String, version: Short): ReservationWriteResult =
        queueFull() ?: guardedWrite {
            when (reservationRepository.deleteReservation(id, version, actor.id)) {
                ReservationDeleteOutcome.Deleted -> ReservationWriteResult.Deleted
                ReservationDeleteOutcome.NotFound -> ReservationWriteResult.NotFound
                ReservationDeleteOutcome.VersionMismatch -> ReservationWriteResult.VersionMismatch
            }
        }

    private suspend fun persistUpdate(
        actor: UserPrincipal,
        id: String,
        request: ReservationUpdate,
        guestIds: List<String>,
    ): ReservationWriteResult = when (
        val outcome = reservationRepository.updateReservation(
            id,
            request.version,
            request.startTime,
            request.endTime,
            guestIds.map(::ReservationGuest),
            actor.id,
        )
    ) {
        is ReservationUpdateOutcome.Updated -> {
            if (outcome.timesChanged) derive(id)
            ReservationWriteResult.Updated(toResponses(listOf(outcome.reservation)).single())
        }

        is ReservationUpdateOutcome.Unchanged -> ReservationWriteResult.Updated(
            toResponses(listOf(outcome.reservation)).single(),
        )

        ReservationUpdateOutcome.NotFound -> ReservationWriteResult.NotFound

        ReservationUpdateOutcome.VersionMismatch -> ReservationWriteResult.VersionMismatch

        ReservationUpdateOutcome.Overlap -> ReservationWriteResult.Conflict

        is ReservationUpdateOutcome.UnknownGuests -> unknownGuests(outcome.guestIds)
    }

    private suspend fun queueFull(): ReservationWriteResult.Unavailable? =
        if (pendingCalendarOperations() >= maxPendingCalendarOperations) {
            ReservationWriteResult.Unavailable("Too many calendar updates are waiting, try again later")
        } else {
            null
        }

    private suspend fun derive(id: String) {
        runCatchingLogged("Deriving check-in/out for reservation $id") { onTimesChanged(id) }
    }

    private fun today() = utcDateNow(clock)

    // One guest query and one avatar query per request, not per reservation
    private suspend fun toResponses(reservations: List<Reservation>): List<ReservationResponse> {
        val guestsById = guestQueryService.allGuests().associateBy { it.id }
        val avatarVersionTimes = guestAvatarService.allAvatarVersionTimes()
        val today = today()

        return reservations.map { it.toResponse(guestsById, avatarVersionTimes, today) }
    }
}

/** The guest id a read is limited to: only a guest user is limited, to the reservations of their own guest. */
private fun UserPrincipal?.visibleGuestId(): String? = this?.takeIf { it.role == AppRole.GUEST_USER }?.id
