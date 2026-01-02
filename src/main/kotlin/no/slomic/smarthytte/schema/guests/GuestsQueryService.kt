package no.slomic.smarthytte.schema.guests

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Query
import no.slomic.smarthytte.guests.GuestRepository

@Suppress("unused")
class GuestsQueryService(private val guestRepository: GuestRepository) : Query {

    @GraphQLDescription("Get all guests")
    suspend fun allGuests(): List<Guest> = guestRepository.allGuests().map { it.toGql() }

    @GraphQLDescription("Get guest by id")
    suspend fun guestById(id: String): Guest? = guestRepository.guestById(id)?.toGql()
}
