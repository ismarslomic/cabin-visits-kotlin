package no.slomic.smarthytte.reservations

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import kotlin.time.Instant

/**
 * `version`, `updated_time` and `updated_by` of a reservation change only for user edits (times, guests). Check-in/out
 * and trip link writes are derived data: they bump and set nothing. They take the `startTime` and `endTime` of the
 * reservation snapshot the caller computed from, re-read the reservation in their own transaction and do nothing
 * ([PersistenceResult.NO_ACTION]) if it is gone or its times differ, so a concurrent change or delete is not
 * overwritten with stale derived data.
 */
interface ReservationRepository {
    suspend fun allReservations(): List<Reservation>

    /**
     * The reservations that overlap the UTC dates [from] (inclusive) and [toExclusive], i.e. `startDate < toExclusive`
     * and `endDate >= from`; a null bound is open. With a [guestId] only the reservations that link that guest (the
     * whole stay or a partial one) are returned. Sorted by `startTime`, then `id`.
     */
    suspend fun reservations(
        guestId: String? = null,
        from: LocalDate? = null,
        toExclusive: LocalDate? = null,
    ): List<Reservation>
    suspend fun addVehicleTripLink(
        reservationId: String,
        vehicleTripId: String,
        type: ReservationVehicleTripType,
        startTime: Instant,
        endTime: Instant,
    ): PersistenceResult

    suspend fun reservationById(id: String): Reservation?

    /**
     * Fixture/import upsert: overwrites all editable properties. The [actor] goes in `created_by` (or `updated_by`
     * for a change).
     */
    suspend fun addOrUpdate(reservation: Reservation, actor: String = SYSTEM_ACTOR): PersistenceResult
    suspend fun deleteReservation(id: String): PersistenceResult
    suspend fun setCheckIn(checkIn: CheckIn, id: String, startTime: Instant, endTime: Instant): PersistenceResult
    suspend fun setCheckOut(checkOut: CheckOut, id: String, startTime: Instant, endTime: Instant): PersistenceResult
}
