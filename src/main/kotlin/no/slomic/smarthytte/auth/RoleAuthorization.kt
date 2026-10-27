

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
    var allowService: Boolean = false
}

/**
 * Responds 403 when the authenticated [UserPrincipal] does not have one of the allowed roles, or when a
 * [ServicePrincipal] calls a route that does not allow services.
 *
 * Calls without a principal are left alone: the authentication challenge responds 401/403/503 to those.
 */
private val RoleAuthorization = createRouteScopedPlugin("RoleAuthorization", ::RoleAuthorizationConfig) {
    val allowedRoles = pluginConfig.allowedRoles
    val allowService = pluginConfig.allowService

    on(AuthenticationChecked) { call ->
        val user = call.principal<UserPrincipal>()
        val service = call.principal<ServicePrincipal>()
        val isAllowed = when {
            user != null -> user.role in allowedRoles
            service != null -> allowService
            else -> return@on
        }
        if (!isAllowed) {
            call.respond(HttpStatusCode.Forbidden, "Insufficient permissions")
        }
    }
}

/**
 * Protects the routes in [build]: the request must be authenticated, by Cloudflare Access as a user (401/403/503
 * otherwise) or by an API key as a service, and the caller must be allowed (403 otherwise):
 * - a user must have one of the [roles],
 * - a service is only allowed when [allowService] is true. Services have no role and are denied by default.
 *
 * Authentication and authorization are always combined here, so a role check cannot end up without authentication.
 */
fun Route.authorized(vararg roles: AppRole, allowService: Boolean = false, build: Route.() -> Unit): Route =
    authenticate(CLOUDFLARE_ACCESS_AUTH, API_KEY_AUTH) {
        val authorizedRoute = createChild(AuthorizedRouteSelector)
        authorizedRoute.install(RoleAuthorization) {
            allowedRoles = roles.toSet()
            this.allowService = allowService
        }
        authorizedRoute.build()
    }

/** Like [authorized], for routes that every user with a role may call. */
fun Route.authorizedAnyRole(allowService: Boolean = false, build: Route.() -> Unit): Route =
    authorized(*AppRole.entries.toTypedArray(), allowService = allowService, build = build)

private object AuthorizedRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Constant

    override fun toString(): String = "(authorize)"
}
