package no.slomic.smarthytte.reservations

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.calendar.CalendarOperation
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

    /**
     * Creates a reservation with a generated id (32 lowercase hex characters, a valid Google Calendar event id) for the
     * [guests], and queues a calendar [CalendarOperation.INSERT]. The overlap check, the write and the outbox row are
     * one transaction, which is safe to re-run (the id is generated inside it). Sets `created_*` ([actor]). The service
     * validates the request, this only guards what must hold in the transaction: no overlap and existing guests.
     */
    suspend fun createReservation(
        startTime: Instant,
        endTime: Instant,
        guests: List<ReservationGuest>,
        actor: String,
    ): ReservationCreateOutcome

    /**
     * Replaces the times and guests of the reservation if the stored version is [expectedVersion]. A change bumps
     * `version` and sets `updated_*` ([actor]). When the times change, the overlap with other reservations is checked,
     * the trip links and the check-in/out are cleared (derived data, nothing else is touched) and a calendar
     * [CalendarOperation.UPDATE] is queued; a guest-only change queues nothing. Times and guests that equal the stored
     * ones change nothing ([ReservationUpdateOutcome.Unchanged]). The check, the write and the outbox row are one
     * transaction.
     */
    suspend fun updateReservation(
        id: String,
        expectedVersion: Short,
        startTime: Instant,
        endTime: Instant,
        guests: List<ReservationGuest>,
        actor: String,
    ): ReservationUpdateOutcome

    /**
     * Deletes the reservation if the stored version is [expectedVersion], with its guest links, trip links and
     * check-in/out, and queues a calendar [CalendarOperation.DELETE]. Logs one INFO line with what was deleted and the
     * [actor], so an accidental delete can be recreated by hand.
     */
    suspend fun deleteReservation(id: String, expectedVersion: Short, actor: String): ReservationDeleteOutcome
    suspend fun setCheckIn(checkIn: CheckIn, id: String, startTime: Instant, endTime: Instant): PersistenceResult
    suspend fun setCheckOut(checkOut: CheckOut, id: String, startTime: Instant, endTime: Instant): PersistenceResult
}
