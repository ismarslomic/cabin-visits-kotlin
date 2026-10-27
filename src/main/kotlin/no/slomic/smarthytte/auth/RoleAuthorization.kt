

package no.slomic.smarthytte.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import no.slomic.smarthytte.guests.AppRole

private class RoleAuthorizationConfig {
    var allowedRoles: Set<AppRole> = emptySet()
}

/**
 * Responds 403 when the authenticated [AppUser] does not have one of the allowed roles.
 *
 * Calls without a user are left alone: the authentication challenge responds 401/403/503 to those.
 */
private val RoleAuthorization = createRouteScopedPlugin("RoleAuthorization", ::RoleAuthorizationConfig) {
    val allowedRoles = pluginConfig.allowedRoles

    on(AuthenticationChecked) { call ->
        val user = call.principal<AppUser>() ?: return@on
        if (user.role !in allowedRoles) {
            call.respond(HttpStatusCode.Forbidden, "Insufficient permissions")
        }
    }
}

/**
 * Protects the routes in [build]: the request must be authenticated by Cloudflare Access (401/403/503 otherwise) and
 * the user must have one of the [roles] (403 otherwise).
 *
 * Authentication and authorization are always combined here, so a role check cannot end up without authentication.
 */
fun Route.authorized(vararg roles: AppRole, build: Route.() -> Unit): Route = authenticate(CLOUDFLARE_ACCESS_AUTH) {
    val authorizedRoute = createChild(AuthorizedRouteSelector)
    authorizedRoute.install(RoleAuthorization) { allowedRoles = roles.toSet() }
    authorizedRoute.build()
}

/** Like [authorized], for routes that every user with a role may call. */
fun Route.authorizedAnyRole(build: Route.() -> Unit): Route = authorized(*AppRole.entries.toTypedArray(), build = build)

private object AuthorizedRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Constant

    override fun toString(): String = "(authorize)"
}
