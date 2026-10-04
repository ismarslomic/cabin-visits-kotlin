@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)

package no.slomic.smarthytte.guests

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.header
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.routing

// 24 hours
private const val AVATAR_MAX_AGE_SECONDS = 86_400

fun Application.configureGuestRoutes(guestRepository: GuestRepository) {
    routing {
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
}

private fun Route.describeGuestAvatar() = describe {
    tag("Guests")
    summary = "Guest avatar"
    description = "Returns the guest's avatar as a JPEG image. The response has an ETag and Cache-Control header, " +
        "and a conditional request with If-None-Match returns 304. Use the avatarUrl from the live stats response, " +
        "which changes when the image does."
    responses {
        HttpStatusCode.OK { description = "The avatar image (image/jpeg)." }
        HttpStatusCode.NotModified { description = "The avatar has not changed since the ETag in If-None-Match." }
        HttpStatusCode.NotFound { description = "The guest does not exist or has no avatar." }
    }
}
