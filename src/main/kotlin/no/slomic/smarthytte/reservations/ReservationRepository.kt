package no.slomic.smarthytte.reservations

import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.UpsertStatus

interface ReservationRepository {
    suspend fun allReservations(): List<Reservation>
    suspend fun reservationById(id: String): Reservation?
    suspend fun addOrUpdate(reservation: Reservation): Reservation
    suspend fun deleteReservation(id: String): Boolean
    suspend fun setNotionId(notionId: String, id: String): UpsertStatus
    suspend fun setCheckIn(checkIn: CheckIn, id: String): UpsertStatus
    suspend fun setCheckOut(checkOut: CheckOut, id: String): UpsertStatus
}
