@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)

package no.slomic.smarthytte.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestUpdateResult
import no.slomic.smarthytte.guests.receiveGuestUpdate
import no.slomic.smarthytte.guests.respondNotUpdated

fun Application.configureAuthRoutes(guestProfileService: GuestProfileService) {
    routing {
        authorized(AppRole.ADMIN, AppRole.FAMILY_USER, AppRole.GUEST_USER) {
            get("/api/me") {
                val user = checkNotNull(call.principal<AppUser>()) { "Authenticated route without a user" }
                call.respond(user)
            }.describeMe()

            post("/api/me") {
                val user = checkNotNull(call.principal<AppUser>()) { "Authenticated route without a user" }
                val update = call.receiveGuestUpdate()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid request body")

                when (val result = guestProfileService.update(user, user.id, update)) {
                    is GuestUpdateResult.Updated -> call.respond(checkNotNull(result.guest.toAppUser()))
                    else -> call.respondNotUpdated(result)
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
        "against Cloudflare's signing keys) together with the application role stored for that person."
    responses {
        HttpStatusCode.OK { description = "The user, with id, name, email and role (ADMIN, FAMILY_USER, GUEST_USER)." }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The authenticated person has no role in the application." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}

private fun Route.describeUpdateMe() = describe {
    operationId = "updateMe"
    tag("Auth")
    summary = "Update the authenticated user"
    description = "Changes the properties of the authenticated user's own guest that are set in the body, and leaves " +
        "the others unchanged. Every role may change firstName, lastName, birthYear and gender. An admin may also " +
        "change email, isFamily and appRole (but not remove their own admin role). Changed properties are not " +
        "overwritten by the guests.json sync at startup."
    responses {
        HttpStatusCode.OK { description = "The updated user, with id, name, email and role." }
        HttpStatusCode.BadRequest { description = "The body is not valid JSON or a property has an invalid value." }
        HttpStatusCode.Unauthorized { description = "The Cloudflare Access token is missing or invalid." }
        HttpStatusCode.Forbidden { description = "The body changes a property the role may not change." }
        HttpStatusCode.ServiceUnavailable { description = "Cloudflare Access is not configured for the application." }
    }
}
