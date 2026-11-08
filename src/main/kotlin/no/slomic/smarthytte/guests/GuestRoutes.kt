@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)

package no.slomic.smarthytte.guests

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.auth.authorized
import no.slomic.smarthytte.auth.authorizedAnyRole
import no.slomic.smarthytte.common.ApiError
import no.slomic.smarthytte.common.RequestBody
import no.slomic.smarthytte.common.allPropertiesRequired
import no.slomic.smarthytte.common.receiveJsonBody
import no.slomic.smarthytte.common.respondApiBodyError
import no.slomic.smarthytte.common.respondPlainBodyError

// 24 hours
private const val AVATAR_MAX_AGE_SECONDS = 86_400

private const val GUESTS_PATH = "/api/guests"
private const val GUEST_PATH = "/api/guests/{guestId}"

fun Application.configureGuestRoutes(
    guestRepository: GuestRepository,
    guestProfileService: GuestProfileService,
    guestAdminService: GuestAdminService,
    guestAvatarService: GuestAvatarService = GuestAvatarService(guestRepository),
) {
    routing {
        authorizedAnyRole(allowService = true) {
            get(GUEST_AVATAR_PATH) {
                val guestId = call.parameters["guestId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing guestId")
                val avatar = guestRepository.avatarById(guestId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                val eTag = "\"${avatar.versionTime.toEpochMilliseconds()}\""
                call.response.header(HttpHeaders.ETag, eTag)
                call.response.header("X-Content-Type-Options", "nosniff")
                call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = AVATAR_MAX_AGE_SECONDS))

                if (call.request.header(HttpHeaders.IfNoneMatch) == eTag) {
                    call.respond(HttpStatusCode.NotModified)
                } else {
                    call.respondBytes(avatar.image, ContentType.Image.JPEG)
                }
            }.describeGuestAvatar()
        }

        authorizedAnyRole {
            uploadOwnAvatarRoute(guestAvatarService)
        }

        authorized(AppRole.ADMIN, AppRole.FAMILY_USER) {
            get(GUESTS_PATH) {
                val avatarVersionTimes = guestRepository.allAvatarVersionTimes()
                val guests = guestRepository.allGuests().map { it.toResponse(avatarVersionTimes[it.id]) }

                call.respond(guests)
            }.describeListGuests()
        }

        authorized(AppRole.ADMIN) {
            post(GUESTS_PATH) {
                val actor = checkNotNull(call.principal<UserPrincipal>()) { "Authenticated route without a user" }
                val create = when (val body = call.receiveJsonBody<GuestCreate>()) {
                    is RequestBody.Ok -> body.value
                    else -> return@post call.respondApiBodyError(body)
                }

                call.respondCreateResult(guestAdminService.create(create, actor.id))
            }.describeCreateGuest()

            uploadGuestAvatarRoute(guestAvatarService)

            put(GUEST_PATH) {
                val actor = checkNotNull(call.principal<UserPrincipal>()) { "Authenticated route without a user" }
                val guestId = call.parameters["guestId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing guestId")
                val update = when (val body = call.receiveJsonBody<GuestUpdate>()) {
                    is RequestBody.Ok -> body.value
                    else -> return@put call.respondPlainBodyError(body)
                }

                when (val result = guestProfileService.update(actor, guestId, update)) {
                    is GuestUpdateResult.Updated -> call.respond(result.guest.toResponse(guestRepository))
                    else -> call.respondNotUpdated(result)
                }
            }.describeUpdateGuest()
        }
    }
}

/** The guest as the API returns it, with the avatar url if it has an avatar. */
suspend fun Guest.toResponse(guestRepository: GuestRepository): GuestResponse =
    toResponse(guestRepository.avatarById(id)?.versionTime)

private suspend fun ApplicationCall.respondCreateResult(result: GuestCreateResult) {
    when (result) {
        // A new guest has no avatar
        is GuestCreateResult.Created -> respond(HttpStatusCode.Created, result.guest.toResponse(null))

        is GuestCreateResult.Invalid ->
            respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_GUEST, result.message))

        GuestCreateResult.EmailInUse ->
            respond(HttpStatusCode.Conflict, ApiError(ApiError.EMAIL_IN_USE, EMAIL_IN_USE_MESSAGE))
    }
}

/** Responds the HTTP status that belongs to a [GuestUpdateResult] that did not update the guest. */
suspend fun ApplicationCall.respondNotUpdated(result: GuestUpdateResult) {
    when (result) {
        is GuestUpdateResult.Updated -> error("The guest was updated")
        GuestUpdateResult.NotFound -> respond(HttpStatusCode.NotFound)
        is GuestUpdateResult.Forbidden -> respond(HttpStatusCode.Forbidden, result.message)
        is GuestUpdateResult.Conflict -> respond(HttpStatusCode.Conflict, result.message)
        is GuestUpdateResult.Invalid -> respond(HttpStatusCode.BadRequest, result.message)
        is GuestUpdateResult.VersionMismatch -> respond(HttpStatusCode.PreconditionFailed, result.message)
    }
}

private fun Route.describeUpdateGuest() = describe {
    operationId = "updateGuest"
    tag("Guests")
    parameters {
        path("guestId") { description = "Internal unique identifier of the guest." }
    }
    summary = "Update a guest"
    description = "Admin only. A full replace: the body must carry the version the client read and every " +
        "writable property (firstName, lastName, birthYear, gender, email, isFamily, role). A null email or role " +
        "clears it, and a guest without a role has no access. Other properties in the body are ignored. An admin " +
        "cannot remove their own admin role or change their own email, and the last admin with an email cannot lose " +
        "the role or the email. The email is trimmed and stored in lowercase, and a blank email clears it. Errors " +
        "are plain text."
    requestBody {
        description = "The version the client read and every writable property."
        required = true
        schema = jsonSchema<GuestUpdate>().allPropertiesRequired()
    }
    responses {
        HttpStatusCode.OK { description = "The updated guest. An update that changes nothing keeps the version." }
        HttpStatusCode.BadRequest {
            description = "The body is not valid JSON, the version or a property is missing, or a value is invalid."
        }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The caller is not an admin, or has no role in the application." }
        HttpStatusCode.NotFound { description = "The guest does not exist." }
        HttpStatusCode.Conflict {
            description = "The email is already used by another user, or the last admin would be removed."
        }
        HttpStatusCode.PreconditionFailed { description = "The guest has changed since the version in the body." }
        HttpStatusCode.PayloadTooLarge { description = "The request body is larger than 64 KB." }
        HttpStatusCode.UnsupportedMediaType { description = "The Content-Type is not application/json." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}

private fun Route.describeListGuests() = describe {
    operationId = "listGuests"
    tag("Guests")
    summary = "List guests"
    description = "Admin and family user. All guests with every property, including email, role, version and the " +
        "audit fields. avatarUrl is null for a guest without an avatar. createdBy and updatedBy are a guest id or " +
        "the literal system, which is not a guest."
    responses {
        HttpStatusCode.OK { description = "The guests, sorted by first name." }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The caller is not an admin or a family user." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}

private fun Route.describeCreateGuest() = describe {
    operationId = "createGuest"
    tag("Guests")
    summary = "Create a guest"
    description = "Admin only. Required: firstName, lastName (1-20 characters), birthYear and gender. Optional: " +
        "email, role (no role means no access) and isFamily (default false). The id is generated from the name, " +
        "such as ola.nordmann or ola.nordmann-2. A guest with a role but no email cannot log in, and a guest that " +
        "has to log in also needs an entry in the Cloudflare Access policy. Errors are JSON with a code and a message."
    requestBody {
        description = "The guest to create."
        required = true
        schema = jsonSchema<GuestCreate>()
    }
    responses {
        HttpStatusCode.Created { description = "The created guest, with its generated id and version." }
        HttpStatusCode.BadRequest { description = "The body is not valid JSON or a property is missing or invalid." }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The caller is not an admin, or has no role in the application." }
        HttpStatusCode.Conflict { description = "The email is already used by another guest (EMAIL_IN_USE)." }
        HttpStatusCode.PayloadTooLarge { description = "The request body is larger than 64 KB." }
        HttpStatusCode.UnsupportedMediaType { description = "The Content-Type is not application/json." }
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
        HttpStatusCode.OK {
            description = "The avatar image (image/jpeg)."
            headers {
                header("X-Content-Type-Options") {
                    description = "Always nosniff: the browser must treat the bytes as image/jpeg and never guess " +
                        "another type, so an uploaded file can never be run as a script or page."
                    schema = JsonSchema(type = JsonType.STRING)
                }
            }
        }
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
