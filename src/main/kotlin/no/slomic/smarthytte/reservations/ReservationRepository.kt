package no.slomic.smarthytte.reservations

import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.PersistenceResult

interface ReservationRepository {
    suspend fun allReservations(): List<Reservation>
    suspend fun addVehicleTripLink(
        reservationId: String,
        vehicleTripId: String,
        type: ReservationVehicleTripType,
    ): PersistenceResult

    suspend fun reservationById(id: String): Reservation?
    suspend fun addOrUpdate(reservation: Reservation): PersistenceResult
    suspend fun deleteReservation(id: String): PersistenceResult
    suspend fun setNotionId(notionId: String, id: String): PersistenceResult
    suspend fun setCheckIn(checkIn: CheckIn, id: String): PersistenceResult
    suspend fun setCheckOut(checkOut: CheckOut, id: String): PersistenceResult
}
