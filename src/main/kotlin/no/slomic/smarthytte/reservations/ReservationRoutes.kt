@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)

package no.slomic.smarthytte.reservations

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.routing
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.auth.authorized
import no.slomic.smarthytte.auth.authorizedAnyRole
import no.slomic.smarthytte.common.ApiError
import no.slomic.smarthytte.guests.AppRole

private const val RESERVATIONS_PATH = "/api/reservations"
private const val RESERVATION_PATH = "/api/reservations/{id}"

fun Application.configureReservationRoutes(reservationService: ReservationService) {
    routing {
        // The service user (MagicMirror) may read reservations too
        authorizedAnyRole(allowService = true) {
            get(RESERVATIONS_PATH) {
                val from = call.dateParameter("from") ?: return@get
                val to = call.dateParameter("to") ?: return@get

                when (val result = reservationService.list(call.principal<UserPrincipal>(), from.value, to.value)) {
                    is ReservationListResult.Listed -> call.respond(result.reservations)

                    is ReservationListResult.InvalidRange ->
                        call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_REQUEST, result.message))
                }
            }.describeListReservations()

            get(RESERVATION_PATH) {
                val id = checkNotNull(call.parameters["id"]) { "The route has an id" }

                when (val result = reservationService.byId(call.principal<UserPrincipal>(), id)) {
                    is ReservationReadResult.Found -> call.respond(result.reservation)

                    ReservationReadResult.NotFound -> call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(ApiError.NOT_FOUND, "The reservation does not exist"),
                    )
                }
            }.describeGetReservation()
        }

        authorized(AppRole.ADMIN, AppRole.FAMILY_USER) {
            reservationWriteRoutes(reservationService)
        }
    }
}

/** A query parameter that is absent ([value] null) or an ISO date; for an invalid date the 400 is already sent. */
private class DateParameter(val value: LocalDate?)

private suspend fun ApplicationCall.dateParameter(name: String): DateParameter? {
    val text = request.queryParameters[name] ?: return DateParameter(null)
    val date = runCatching { LocalDate.parse(text) }.getOrNull()

    if (date == null) {
        respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_REQUEST, "$name must be a date (yyyy-MM-dd)"))
    }
    return date?.let { DateParameter(it) }
}

private fun Route.describeListReservations() = describe {
    operationId = "listReservations"
    tag("Reservations")
    summary = "List reservations"
    parameters {
        query("from") {
            description = "First day of the window (inclusive), a UTC date yyyy-MM-dd. Omit for no lower limit."
            required = false
            example = GenericElement("2026-07-01")
            schema = JsonSchema(type = JsonType.STRING, format = "date")
        }
        query("to") {
            description = "Day after the last day of the window (exclusive), a UTC date yyyy-MM-dd. Must be after " +
                "from. Omit for no upper limit."
            required = false
            example = GenericElement("2026-08-01")
            schema = JsonSchema(type = JsonType.STRING, format = "date")
        }
    }
    description = "All roles and the service user. Returns every reservation that overlaps the window, i.e. its " +
        "start date is before to and its end date is on or after from (UTC dates), so a reservation that spans a " +
        "month boundary appears in both months. Without parameters it returns all reservations. Admin, family " +
        "user and the service user see every reservation; a guest user only sees the reservations their own guest " +
        "is part of, whole stay or part of it. Sorted by startTime, then id. Errors are JSON with a code and a message."
    responses {
        HttpStatusCode.OK { description = "The reservations in the window, sorted by start time." }
        HttpStatusCode.BadRequest { description = "from or to is not a date (yyyy-MM-dd), or to is not after from." }
        HttpStatusCode.Unauthorized {
            description = "The Cloudflare Access token or the API key is missing or invalid."
        }
        HttpStatusCode.Forbidden { description = "The caller has no role in the application." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}

private fun Route.describeGetReservation() = describe {
    operationId = "getReservation"
    tag("Reservations")
    summary = "Get a reservation"
    parameters {
        path("id") {
            description = "Unique identifier of the reservation. Old reservations can have an id that starts with " +
                "an underscore."
            example = GenericElement("a3f9c2e17b4d4e5f8a1b2c3d4e5f6a7b")
        }
    }
    description = "All roles and the service user. A guest user only gets a reservation their own guest is part " +
        "of, for any other reservation the answer is the same as for an unknown id. Errors are JSON with a code " +
        "and a message."
    responses {
        HttpStatusCode.OK { description = "The reservation." }
        HttpStatusCode.Unauthorized {
            description = "The Cloudflare Access token or the API key is missing or invalid."
        }
        HttpStatusCode.Forbidden { description = "The caller has no role in the application." }
        HttpStatusCode.NotFound {
            description = "The reservation does not exist, or the caller is a guest user who is not a guest of it."
        }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}
