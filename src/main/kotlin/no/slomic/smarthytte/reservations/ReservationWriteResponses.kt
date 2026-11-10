package no.slomic.smarthytte.reservations

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import no.slomic.smarthytte.common.ApiError

private const val RESERVATIONS_PATH = "/api/reservations"

// One function per route, each with only the results that route can have: the OpenAPI plugin infers the documented
// statuses of a route from what it responds, and a status that cannot occur must not be documented
internal suspend fun ApplicationCall.respondCreateResult(result: ReservationWriteResult) {
    when (result) {
        is ReservationWriteResult.Created -> {
            response.header(HttpHeaders.Location, "$RESERVATIONS_PATH/${result.reservation.id}")
            respond(HttpStatusCode.Created, result.reservation)
        }

        is ReservationWriteResult.Invalid -> respondInvalid(result)

        ReservationWriteResult.Conflict -> respondOverlap()

        is ReservationWriteResult.Unavailable -> respondUnavailable(result)

        else -> error("A create cannot answer $result")
    }
}

internal suspend fun ApplicationCall.respondUpdateResult(result: ReservationWriteResult) {
    when (result) {
        is ReservationWriteResult.Updated -> respond(result.reservation)
        ReservationWriteResult.NotFound -> respondNotFound()
        ReservationWriteResult.VersionMismatch -> respondVersionMismatch()
        is ReservationWriteResult.Invalid -> respondInvalid(result)
        ReservationWriteResult.Conflict -> respondOverlap()
        is ReservationWriteResult.Unavailable -> respondUnavailable(result)
        else -> error("An update cannot answer $result")
    }
}

internal suspend fun ApplicationCall.respondDeleteResult(result: ReservationWriteResult) {
    when (result) {
        ReservationWriteResult.Deleted -> respond(HttpStatusCode.NoContent)
        ReservationWriteResult.NotFound -> respondNotFound()
        ReservationWriteResult.VersionMismatch -> respondVersionMismatch()
        is ReservationWriteResult.Unavailable -> respondUnavailable(result)
        else -> error("A delete cannot answer $result")
    }
}

private suspend fun ApplicationCall.respondNotFound() =
    respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND, "The reservation does not exist"))

private suspend fun ApplicationCall.respondInvalid(result: ReservationWriteResult.Invalid) =
    respond(HttpStatusCode.BadRequest, ApiError(result.code, result.message))

private suspend fun ApplicationCall.respondOverlap() =
    respond(HttpStatusCode.Conflict, ApiError(ApiError.OVERLAP, "The times overlap another reservation"))

private suspend fun ApplicationCall.respondVersionMismatch() = respond(
    HttpStatusCode.PreconditionFailed,
    ApiError(ApiError.VERSION_MISMATCH, "The reservation has changed since the version you read"),
)

private suspend fun ApplicationCall.respondUnavailable(result: ReservationWriteResult.Unavailable) =
    respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.TEMPORARILY_UNAVAILABLE, result.message))
