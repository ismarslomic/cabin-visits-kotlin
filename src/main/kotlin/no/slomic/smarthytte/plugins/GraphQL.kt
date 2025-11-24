package no.slomic.smarthytte.plugins

import com.expediagroup.graphql.server.ktor.GraphQL
import com.expediagroup.graphql.server.ktor.defaultGraphQLStatusPages
import com.expediagroup.graphql.server.ktor.graphQLGetRoute
import com.expediagroup.graphql.server.ktor.graphQLPostRoute
import com.expediagroup.graphql.server.ktor.graphQLSDLRoute
import com.expediagroup.graphql.server.ktor.graphiQLRoute
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.schema.GuestsQueryService
import no.slomic.smarthytte.schema.ReservationQueryService

fun Application.configureGraphQL(guestRepository: GuestRepository, reservationRepository: ReservationRepository) {
    install(GraphQL) {
        schema {
            packages = listOf("no.slomic.smarthytte.schema")
            queries = listOf(
                GuestsQueryService(guestRepository),
                ReservationQueryService(reservationRepository),
            )
            typeHierarchy = mapOf()
        }
    }

    routing {
        graphQLGetRoute("/graphql")
        graphQLPostRoute("/graphql")
        graphiQLRoute("/graphiql")
        graphQLSDLRoute("/sdl")
    }

    install(StatusPages) {
        defaultGraphQLStatusPages()
    }
}
