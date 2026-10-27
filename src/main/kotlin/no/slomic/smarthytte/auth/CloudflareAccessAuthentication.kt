package no.slomic.smarthytte.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import no.slomic.smarthytte.properties.CloudflareAccessProperties
import java.net.URI
import java.util.concurrent.TimeUnit

/** Name of the authentication provider, for use with `authenticate(CLOUDFLARE_ACCESS_AUTH) { ... }`. */
const val CLOUDFLARE_ACCESS_AUTH = "cloudflare-access"

const val ACCESS_JWT_HEADER = "Cf-Access-Jwt-Assertion"

private const val JWKS_CACHE_SIZE = 10L
private const val JWKS_CACHE_HOURS = 24L
private const val JWKS_REFRESHES_PER_MINUTE = 10L
private const val CLOCK_LEEWAY_SECONDS = 30L

/** Set when the token is valid, but its email has no [AppRole] in the application. */
private val UnknownUser = AttributeKey<Boolean>("CloudflareAccessUnknownUser")

/**
 * What it takes to verify the tokens of a Cloudflare Access application.
 *
 * @property issuer `https://<team-domain>`.
 * @property audience The `AUD` tag of the Access application.
 * @property jwkProvider Provides the Cloudflare signing keys by key id.
 */
data class CloudflareAccessConfig(val issuer: String, val audience: String, val jwkProvider: JwkProvider)

/** Null when Cloudflare Access is not configured, so that endpoints requiring authentication respond 503. */
fun CloudflareAccessProperties.toConfig(): CloudflareAccessConfig? {
    if (!isConfigured) return null

    val issuer = "https://${teamDomain.removePrefix("https://").trimEnd('/')}"

    // Caches the keys and limits how often Cloudflare is called when tokens have unknown key ids
    val jwkProvider = JwkProviderBuilder(URI("$issuer/cdn-cgi/access/certs").toURL())
        .cached(JWKS_CACHE_SIZE, JWKS_CACHE_HOURS, TimeUnit.HOURS)
        .rateLimited(JWKS_REFRESHES_PER_MINUTE, 1, TimeUnit.MINUTES)
        .build()

    return CloudflareAccessConfig(issuer, audience, jwkProvider)
}

/**
 * Installs authentication of requests that are routed through Cloudflare Access.
 *
 * Authentication (Cloudflare Access): the JWT in [ACCESS_JWT_HEADER] is verified by the Ktor JWT provider against
 * the Cloudflare signing keys: signature, issuer, audience, expiry and not-before.
 * Identity mapping: the email in the token must belong to a guest that has an [AppRole]. The principal is an
 * [AppUser]. What the user may do is decided by [authorized], not here.
 *
 * Only routes wrapped in `authenticate(CLOUDFLARE_ACCESS_AUTH)` require it, so existing unauthenticated endpoints are
 * unaffected. A null [config] (Cloudflare Access is not configured) makes the protected routes respond 503.
 */
fun Application.configureAuthentication(config: CloudflareAccessConfig?, appUserService: AppUserService) {
    authentication {
        if (config == null) {
            this@configureAuthentication.log.warn(
                "Cloudflare Access is not configured, endpoints that require authentication will respond 503",
            )
            register(NotConfiguredProvider())
        } else {
            jwt(CLOUDFLARE_ACCESS_AUTH) {
                authHeader { call ->
                    call.request.header(ACCESS_JWT_HEADER)?.let { HttpAuthHeader.Single("Bearer", it) }
                }
                verifier(config.jwkProvider, config.issuer) {
                    withAudience(config.audience)
                    acceptLeeway(CLOCK_LEEWAY_SECONDS)
                }
                validate { credential ->
                    // Service tokens have no email: they are authenticated, but cannot be mapped to a person
                    val email = credential.payload.getClaim("email").asString()?.takeIf { it.isNotBlank() }
                    val user = appUserService.userByEmail(email)
                    if (email != null && user == null) {
                        application.log.warn(
                            "Authenticated Cloudflare Access user $email has no role in the application",
                        )
                        attributes.put(UnknownUser, true)
                    }
                    user
                }
                challenge { _, _ ->
                    if (call.attributes.contains(UnknownUser)) {
                        call.respond(HttpStatusCode.Forbidden, "No access to this application")
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "Invalid or missing authentication")
                    }
                }
            }
        }
    }
}

/** Fails closed: without configuration no token can be verified, so every protected request gets 503. */
private class NotConfiguredProvider : AuthenticationProvider(object : Config(CLOUDFLARE_ACCESS_AUTH) {}) {
    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val cause = AuthenticationFailedCause.Error("Cloudflare Access is not configured")
        context.challenge(CLOUDFLARE_ACCESS_AUTH, cause) { challenge, call ->
            call.respond(HttpStatusCode.ServiceUnavailable, "Authentication is not configured")
            challenge.complete()
        }
    }
}
