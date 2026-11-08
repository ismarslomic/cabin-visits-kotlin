@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)

package no.slomic.smarthytte.guests

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.put
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.common.ApiError
import no.slomic.smarthytte.common.RequestBody
import no.slomic.smarthytte.common.receiveBinaryBody

private const val ME_AVATAR_PATH = "/api/me/avatar"
private const val BYTES_PER_KB = 1024

/** `PUT /api/me/avatar`: the authenticated user uploads their own avatar. Register it in a group for every role. */
fun Route.uploadOwnAvatarRoute(guestAvatarService: GuestAvatarService) {
    put(ME_AVATAR_PATH) {
        val user = checkNotNull(call.principal<UserPrincipal>()) { "Authenticated route without a user" }
        call.receiveAvatar { call.respondUploadResult(guestAvatarService.upload(user.id, it, user.id)) }
    }.describeUploadMyAvatar()
}

/** `PUT /api/guests/{guestId}/avatar`: an admin uploads the avatar of any guest. Register it in the admin group. */
fun Route.uploadGuestAvatarRoute(guestAvatarService: GuestAvatarService) {
    put(GUEST_AVATAR_PATH) {
        val actor = checkNotNull(call.principal<UserPrincipal>()) { "Authenticated route without a user" }
        val guestId = call.parameters["guestId"]
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing guestId")
        call.receiveAvatar {
            when (val result = guestAvatarService.upload(guestId, it, actor.id)) {
                AvatarUploadResult.GuestNotFound ->
                    call.respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND, "The guest does not exist"))

                else -> call.respondUploadResult(result)
            }
        }
    }.describeUploadGuestAvatar()
}

/** Reads the avatar body (JPEG, at most [MAX_AVATAR_BYTES]) and passes it to [onImage], or answers the error. */
private suspend fun ApplicationCall.receiveAvatar(onImage: suspend (ByteArray) -> Unit) {
    when (val body = receiveBinaryBody(ContentType.Image.JPEG, MAX_AVATAR_BYTES)) {
        is RequestBody.Ok -> onImage(body.value)

        RequestBody.UnsupportedMediaType -> respond(
            HttpStatusCode.UnsupportedMediaType,
            ApiError(ApiError.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be image/jpeg"),
        )

        RequestBody.TooLarge -> respond(
            HttpStatusCode.PayloadTooLarge,
            ApiError(ApiError.BODY_TOO_LARGE, "The image is larger than ${MAX_AVATAR_BYTES / BYTES_PER_KB} KB"),
        )

        RequestBody.Malformed -> error("A binary body is never malformed")
    }
}

private suspend fun ApplicationCall.respondUploadResult(result: AvatarUploadResult) {
    when (result) {
        AvatarUploadResult.Saved -> respond(HttpStatusCode.NoContent)

        // The guest of the authenticated user always exists. The route with a guest id answers 404 itself
        AvatarUploadResult.GuestNotFound -> error("The guest of the authenticated user does not exist")

        is AvatarUploadResult.InvalidImage ->
            respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_IMAGE, result.reason))
    }
}

private const val UPLOAD_DESCRIPTION = "The body is the raw JPEG (Content-Type image/jpeg, at most 4 MB, at most " +
    "4000 x 4000 pixels). The server removes all metadata (Exif including GPS and orientation, XMP, comments, " +
    "thumbnails) without re-encoding, so the client must upload upright pixels: draw the image to a canvas, which " +
    "applies the orientation. Last write wins: no version is sent, and the same image changes nothing. A changed " +
    "image gives the guest a new avatarUrl (the v parameter). The guest's own version is not changed. Errors are " +
    "JSON with a code and a message."

private fun Route.describeUploadGuestAvatar() = describeUpload(
    operationId = "uploadGuestAvatar",
    tag = "Guests",
    summary = "Upload the avatar of a guest",
    description = "Admin only. Creates or replaces the avatar of any guest. $UPLOAD_DESCRIPTION",
    withGuestId = true,
)

private fun Route.describeUploadMyAvatar() = describeUpload(
    operationId = "uploadMyAvatar",
    tag = "Auth",
    summary = "Upload the avatar of the authenticated user",
    description = "Every role. Creates or replaces the avatar of the authenticated user's own guest. " +
        UPLOAD_DESCRIPTION,
    withGuestId = false,
)

private fun Route.describeUpload(
    operationId: String,
    tag: String,
    summary: String,
    description: String,
    withGuestId: Boolean,
) = describe {
    this.operationId = operationId
    tag(tag)
    if (withGuestId) {
        parameters {
            path("guestId") { this.description = "Internal unique identifier of the guest." }
        }
    }
    this.summary = summary
    this.description = description
    requestBody {
        this.description = "The raw JPEG bytes of the avatar, not JSON and not multipart."
        required = true
        ContentType.Image.JPEG {
            schema = JsonSchema(type = JsonType.STRING, format = "binary")
        }
    }
    responses {
        HttpStatusCode.NoContent { this.description = "The avatar is stored." }
        HttpStatusCode.BadRequest { this.description = "The body is not a valid JPEG (INVALID_IMAGE)." }
        HttpStatusCode.Unauthorized { this.description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { this.description = "The caller is not allowed to upload this avatar." }
        // The guest of the authenticated user always exists, so only the route with a guest id can answer 404
        if (withGuestId) HttpStatusCode.NotFound { this.description = "The guest does not exist." }
        HttpStatusCode.PayloadTooLarge { this.description = "The image is larger than 4 MB." }
        HttpStatusCode.UnsupportedMediaType { this.description = "The Content-Type is not image/jpeg." }
        HttpStatusCode.ServiceUnavailable {
            this.description = "Cloudflare Access is not configured for the application."
        }
    }
}
