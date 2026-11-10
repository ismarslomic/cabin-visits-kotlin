package no.slomic.smarthytte.reservations

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.common.toUtcDate
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.GuestRepository
import kotlin.time.Clock

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

class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val guestRepository: GuestRepository,
    private val clock: Clock = Clock.System,
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

    // One guest query and one avatar query per request, not per reservation
    private suspend fun toResponses(reservations: List<Reservation>): List<ReservationResponse> {
        val guestsById = guestRepository.allGuests().associateBy { it.id }
        val avatarVersionTimes = guestRepository.allAvatarVersionTimes()
        val today = clock.now().toUtcDate()

        return reservations.map { it.toResponse(guestsById, avatarVersionTimes, today) }
    }
}

/** The guest id a read is limited to: only a guest user is limited, to the reservations of their own guest. */
private fun UserPrincipal?.visibleGuestId(): String? = this?.takeIf { it.role == AppRole.GUEST_USER }?.id
