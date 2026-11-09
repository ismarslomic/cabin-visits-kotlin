@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)

package no.slomic.smarthytte.auth

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import no.slomic.smarthytte.common.ApiError
import no.slomic.smarthytte.common.RequestBody
import no.slomic.smarthytte.common.receiveJsonBody
import no.slomic.smarthytte.common.respondApiBodyError
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.guests.GuestUpdateResult
import no.slomic.smarthytte.guests.MeUpdate

fun Application.configureAuthRoutes(guestProfileService: GuestProfileService, guestRepository: GuestRepository) {
    routing {
        authorizedAnyRole {
            get("/api/me") {
                val user = checkNotNull(call.principal<UserPrincipal>()) { "Authenticated route without a user" }
                call.respond(user.toMeResponse(guestRepository.avatarVersionTime(user.id)))
            }.describeMe()

            put("/api/me") {
                val user = checkNotNull(call.principal<UserPrincipal>()) { "Authenticated route without a user" }
                val update = when (val body = call.receiveJsonBody<MeUpdate>()) {
                    is RequestBody.Ok -> body.value
                    else -> return@put call.respondApiBodyError(body)
                }

                when (val result = guestProfileService.updateOwnProfile(user, update)) {
                    is GuestUpdateResult.Updated -> call.respond(
                        checkNotNull(result.guest.toUserPrincipal()).toMeResponse(
                            guestRepository.avatarVersionTime(user.id),
                        ),
                    )

                    is GuestUpdateResult.Invalid -> call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(ApiError.INVALID_GUEST, result.message),
                    )

                    is GuestUpdateResult.VersionMismatch -> call.respond(
                        HttpStatusCode.PreconditionFailed,
                        ApiError(ApiError.VERSION_MISMATCH, result.message),
                    )

                    GuestUpdateResult.NotFound -> call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(ApiError.NOT_FOUND, "The guest does not exist"),
                    )

                    // The email, which is what can conflict, cannot be changed here, and the role is not checked
                    is GuestUpdateResult.Forbidden, is GuestUpdateResult.Conflict ->
                        error("Updating the own profile cannot give $result")
                }
            }.describeUpdateMe()
        }
    }
}

private fun Route.describeMe() = describe {
    operationId = "getMe"
    tag("Auth")
    summary = "Authenticated user"
    description =
        "Returns the user that Cloudflare Access authenticated (the Cf-Access-Jwt-Assertion header, verified " +
        "against Cloudflare's signing keys) together with the profile and the application role stored for that " +
        "person."
    responses {
        HttpStatusCode.OK {
            description = "The user, with id, name (first and last name together), firstName, lastName, birthYear, " +
                "gender, email, isFamily, role (ADMIN, FAMILY_USER, GUEST_USER), avatarUrl (null without an avatar, " +
                "otherwise it changes when the image does) and version, which an update must send back."
        }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The authenticated person has no role in the application." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}

private fun Route.describeUpdateMe() = describe {
    operationId = "updateMe"
    tag("Auth")
    summary = "Update the authenticated user"
    description = "A full replace of the profile of the authenticated user's own guest. Every role sends firstName, " +
        "lastName, birthYear and gender, all required, together with the version the client read (from GET " +
        "/api/me). Other properties, such as email, isFamily and role, cannot be changed here and are ignored. " +
        "Errors are JSON with a code and a message."
    requestBody {
        description = "The version the client read and the four profile properties."
        required = true
        schema = jsonSchema<MeUpdate>()
    }
    responses {
        HttpStatusCode.OK { description = "The updated user, same as the response of GET /api/me." }
        HttpStatusCode.BadRequest {
            description = "The body is not valid JSON, a property or the version is missing or a property has an " +
                "invalid value."
        }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The authenticated person has no role in the application." }
        HttpStatusCode.NotFound { description = "The guest of the authenticated user does not exist." }
        HttpStatusCode.PreconditionFailed { description = "The guest has changed since the version in the body." }
        HttpStatusCode.PayloadTooLarge { description = "The request body is larger than 64 KB." }
        HttpStatusCode.UnsupportedMediaType { description = "The Content-Type is not application/json." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}
