package no.slomic.smarthytte.reservations

import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.PersistenceResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Access to reservations for background tasks and for statistics, in other packages: the calendar push, the
 * derivation of check-in/out and the statistics. **It has no caller and applies no visibility rule**: never use it
 * from a route to answer a user's request, use [ReservationService] for that, which limits what a guest user sees.
 *
 * The derived-data writes ([setCheckIn], [setCheckOut], [addVehicleTripLink]) do not bump the version of the
 * reservation and skip a reservation that changed since the snapshot the caller computed from, see
 * [ReservationRepository].
 */
class ReservationSystemService(private val reservationRepository: ReservationRepository) {
    suspend fun all(): List<Reservation> = reservationRepository.allReservations()

    suspend fun byId(id: String): Reservation? = reservationRepository.reservationById(id)

    /** True if [now] is within a day before the start and a day after the end of any reservation. */
    suspend fun isWithinReservationWindow(now: Instant = Clock.System.now()): Boolean =
        all().any { now >= it.startTime.minus(1.days) && now <= it.endTime.plus(1.days) }

    suspend fun setCheckIn(checkIn: CheckIn, id: String, startTime: Instant, endTime: Instant): PersistenceResult =
        reservationRepository.setCheckIn(checkIn, id, startTime, endTime)

    suspend fun setCheckOut(checkOut: CheckOut, id: String, startTime: Instant, endTime: Instant): PersistenceResult =
        reservationRepository.setCheckOut(checkOut, id, startTime, endTime)

    suspend fun addVehicleTripLink(
        reservationId: String,
        vehicleTripId: String,
        type: ReservationVehicleTripType,
        startTime: Instant,
        endTime: Instant,
    ): PersistenceResult =
        reservationRepository.addVehicleTripLink(reservationId, vehicleTripId, type, startTime, endTime)
}
