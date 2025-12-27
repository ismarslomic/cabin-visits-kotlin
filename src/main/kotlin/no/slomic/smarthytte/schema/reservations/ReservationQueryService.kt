package no.slomic.smarthytte.schema.reservations

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Query
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.schema.guests.toGql

class ReservationQueryService(private val reservationRepository: ReservationRepository) : Query {

    @GraphQLDescription("Get all reservations ordered latest reservations first")
    suspend fun allReservations(): List<Reservation> =
        reservationRepository.allReservations(sortByLatestReservation = true).map { it.toGql() }

    @GraphQLDescription("Get reservation by id")
    suspend fun reservationById(id: String): Reservation? = reservationRepository.reservationById(id)?.toGql()
}
