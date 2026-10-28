@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)

package no.slomic.smarthytte.guests

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.auth.authorized
import no.slomic.smarthytte.auth.authorizedAnyRole

// 24 hours
private const val AVATAR_MAX_AGE_SECONDS = 86_400

private const val GUEST_PATH = "/api/guests/{guestId}"

fun Application.configureGuestRoutes(guestRepository: GuestRepository, guestProfileService: GuestProfileService) {
    routing {
        authorizedAnyRole(allowService = true) {
            get(GUEST_AVATAR_PATH) {
                val guestId = call.parameters["guestId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing guestId")
                val avatar = guestRepository.avatarById(guestId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                val eTag = "\"${avatar.updatedTime.toEpochMilliseconds()}\""
                call.response.header(HttpHeaders.ETag, eTag)
                call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = AVATAR_MAX_AGE_SECONDS))

                if (call.request.header(HttpHeaders.IfNoneMatch) == eTag) {
                    call.respond(HttpStatusCode.NotModified)
                } else {
                    call.respondBytes(avatar.image, ContentType.Image.JPEG)
                }
            }.describeGuestAvatar()
        }

        authorized(AppRole.ADMIN) {
            post(GUEST_PATH) {
                val actor = checkNotNull(call.principal<UserPrincipal>()) { "Authenticated route without a user" }
                val guestId = call.parameters["guestId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing guestId")
                val update = call.receiveGuestUpdate()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid request body")

                when (val result = guestProfileService.update(actor, guestId, update)) {
                    is GuestUpdateResult.Updated -> call.respond(result.guest)
                    else -> call.respondNotUpdated(result)
                }
            }.describeUpdateGuest()
        }
    }
}

/** The request body, or null if it is not a valid JSON [GuestUpdate]. */
suspend fun ApplicationCall.receiveGuestUpdate(): GuestUpdate? = try {
    receive<GuestUpdate>()
} catch (_: BadRequestException) {
    null
} catch (_: ContentTransformationException) {
    null
}

/** Responds the HTTP status that belongs to a [GuestUpdateResult] that did not update the guest. */
suspend fun ApplicationCall.respondNotUpdated(result: GuestUpdateResult) {
    when (result) {
        is GuestUpdateResult.Updated -> error("The guest was updated")
        GuestUpdateResult.NotFound -> respond(HttpStatusCode.NotFound)
        is GuestUpdateResult.Forbidden -> respond(HttpStatusCode.Forbidden, result.message)
        is GuestUpdateResult.Conflict -> respond(HttpStatusCode.Conflict, result.message)
        is GuestUpdateResult.Invalid -> respond(HttpStatusCode.BadRequest, result.message)
    }
}

private fun Route.describeUpdateGuest() = describe {
    operationId = "updateGuest"
    tag("Guests")
    parameters {
        path("guestId") { description = "Internal unique identifier of the guest." }
    }
    summary = "Update a guest"
    description = "Admin only. Changes the properties that are set in the body (firstName, lastName, birthYear, " +
        "gender, email, isFamily, appRole) and leaves the others unchanged. Changed properties are not overwritten " +
        "by the guests.json sync at startup. An admin cannot remove their own admin role."
    responses {
        HttpStatusCode.OK { description = "The updated guest." }
        HttpStatusCode.BadRequest { description = "The body is not valid JSON or a property has an invalid value." }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The caller is not an admin, or has no role in the application." }
        HttpStatusCode.NotFound { description = "The guest does not exist." }
        HttpStatusCode.Conflict { description = "The email is already used by another user." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}

private fun Route.describeGuestAvatar() = describe {
    operationId = "getGuestAvatar"
    tag("Guests")
    parameters {
        path("guestId") { description = "Internal unique identifier of the guest." }
    }
    summary = "Guest avatar"
    description = "Returns the guest's avatar as a JPEG image. The response has an ETag and Cache-Control header, " +
        "and a conditional request with If-None-Match returns 304. Use the avatarUrl from the live stats response, " +
        "which changes when the image does."
    responses {
        HttpStatusCode.OK { description = "The avatar image (image/jpeg)." }
        HttpStatusCode.NotModified { description = "The avatar has not changed since the ETag in If-None-Match." }
        HttpStatusCode.BadRequest { description = "The guestId path parameter is missing." }
        HttpStatusCode.Unauthorized {
            description = "The Cloudflare Access token or the API key is missing or invalid."
        }
        HttpStatusCode.Forbidden { description = "The caller has no role in the application." }
        HttpStatusCode.NotFound { description = "The guest does not exist or has no avatar." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}
