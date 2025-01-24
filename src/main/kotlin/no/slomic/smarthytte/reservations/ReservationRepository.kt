package no.slomic.smarthytte.reservations

interface ReservationRepository {
    suspend fun allReservations(): List<Reservation>
    suspend fun reservationById(id: String): Reservation?
    suspend fun addOrUpdate(reservation: Reservation): Reservation
    suspend fun deleteReservation(id: String): Boolean
}
