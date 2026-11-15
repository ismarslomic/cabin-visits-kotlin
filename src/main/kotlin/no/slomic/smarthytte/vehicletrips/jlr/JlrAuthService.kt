package no.slomic.smarthytte.vehicletrips.jlr

import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.slomic.smarthytte.common.issues.IssueCode
import no.slomic.smarthytte.common.issues.IssueReporter
import no.slomic.smarthytte.plugins.SignRequest
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
private const val PKCE_VERIFIER_BYTES = 48
private const val SLOW_DOWN_STEP_SECONDS = 5L
private const val APPROVAL_ISSUE_KEY = "JLR_APPROVAL_REQUIRED"

/** The access token is renewed this long before it expires, so a call never starts with a token about to expire. */
private val TOKEN_MARGIN = 60.seconds

/** The owner approves the device login within this time (the server says 900 s, this is a ceiling). */
private val DEVICE_LOGIN_MAX_WAIT = 15.minutes

/** After a failed or expired device login the next one starts at the earliest this much later. */
private val DEVICE_LOGIN_COOLDOWN = 1.hours

private val ERRORS_THAT_NEED_NEW_LOGIN = setOf("invalid_grant", "invalid_client", "unauthorized_client")

/**
 * Gives a valid access token for the Data Hub, and logs in again when there is none.
 *
 * - The access token (5 minutes) is cached in memory. The refresh token rotates on every use, so it is stored through
 *   [JlrTokenRepository] before anything else happens, and one caller at a time renews (mutex) so a rotated token is
 *   never spent twice.
 * - Without a stored refresh token it runs the device login inline: the link and code are reported as
 *   [IssueCode.JLR_APPROVAL_REQUIRED] and the owner approves in a browser while this polls.
 * - A rejected login throws [JlrAuthException] (needs the owner, never retried). Other failures throw
 *   [JlrRequestException].
 *
 * Client assertions and DPoP proofs are single use, so every request, also a retry, gets new ones (see
 * [SignRequest]). Tokens, keys, assertions and proofs are never logged or put in a message.
 */
@Suppress("LongParameterList")
class JlrAuthService(
    httpClient: HttpClient,
    private val signer: JlrJwtSigner,
    private val tokenRepository: JlrTokenRepository,
    private val issueReporter: IssueReporter,
    clientId: String,
    private val clock: Clock = Clock.System,
    private val pause: suspend (Duration) -> Unit = { delay(it) },
) {
    private val logger = KtorSimpleLogger(JlrAuthService::class.java.name)
    private val mutex = Mutex()
    private var cachedToken: CachedToken? = null

    // Kept in memory as well, so a rotated token survives a failed write and is stored with the next renewal
    private var refreshToken: String? = null
    private var persistedRefreshToken: String? = null
    private var isRefreshTokenLoaded = false
    private var nextDeviceLoginAllowedAt: Instant? = null

    private val endpoint = JlrTokenEndpoint(httpClient, signer, clientId)

    /** A valid access token, renewed first if needed. */
    suspend fun accessToken(): String = mutex.withLock { cachedOrRenewed() }

    /**
     * Call when the API rejected [rejectedToken] (403 `forbidden`). Renews unless another caller already did, so two
     * rejected calls renew once.
     */
    suspend fun accessTokenAfterRejection(rejectedToken: String): String = mutex.withLock {
        if (cachedToken?.value == rejectedToken) cachedToken = null
        cachedOrRenewed()
    }

    /** DPoP proof for an API call with [accessToken], with the server's nonce if it asked for one. */
    fun apiDpopProof(method: String, url: String, accessToken: String): String =
        signer.dpopProof(method, url, accessToken, endpoint.dpopNonce)

    /** Remember the nonce of a `use_dpop_nonce` answer from the API. */
    fun rememberDpopNonce(nonce: String) {
        endpoint.dpopNonce = nonce
    }

    private suspend fun cachedOrRenewed(): String {
        val usable = cachedToken?.takeIf { it.expiresAt - TOKEN_MARGIN > clock.now() }
        return usable?.value ?: renew()
    }

    private suspend fun renew(): String {
        if (!isRefreshTokenLoaded) {
            persistedRefreshToken = tokenRepository.findRefreshToken()
            refreshToken = persistedRefreshToken
            isRefreshTokenLoaded = true
        }
        val stored = refreshToken
        val tokens = if (stored == null) deviceLogin() else refresh(stored)

        // The rotated token first, before the access token is used for anything
        tokens.refreshToken?.let { refreshToken = it }
        val current = refreshToken
        if (current != null && current != persistedRefreshToken) {
            tokenRepository.saveRefreshToken(current)
            persistedRefreshToken = current
        }
        logger.info(
            "JLR token renewed: expires_in=${tokens.expiresIn} refresh_expires_in=${tokens.refreshExpiresIn} " +
                "login=${if (stored == null) "device" else "refresh"}",
        )
        cachedToken = CachedToken(tokens.accessToken, clock.now() + tokens.expiresIn.seconds)
        return tokens.accessToken
    }

    private suspend fun refresh(storedRefreshToken: String): JlrTokenResponse {
        val response = endpoint.postToken(
            listOf("grant_type" to "refresh_token", "refresh_token" to storedRefreshToken),
            retryOnException = false,
        )
        val error = response.errorCode()
        if (error == "invalid_grant") {
            // The refresh token is spent or revoked: forget it so the next call starts a device login
            refreshToken = null
            tokenRepository.deleteRefreshToken()
            persistedRefreshToken = null
        }
        return if (response.status == HttpStatusCode.OK) response.tokens() else throw refreshFailure(error, response)
    }

    private suspend fun deviceLogin(): JlrTokenResponse {
        val blockedUntil = nextDeviceLoginAllowedAt
        if (blockedUntil != null && blockedUntil > clock.now()) {
            throw JlrAuthException("The last JLR device login failed, the next one starts at $blockedUntil")
        }
        val verifier = pkceVerifier()
        val authorization = startDeviceAuthorization(pkceChallenge(verifier))
        val link = authorization.verificationUriComplete ?: authorization.verificationUri
        issueReporter.report(
            code = IssueCode.JLR_APPROVAL_REQUIRED,
            key = APPROVAL_ISSUE_KEY,
            message = "Approve the JLR login at $link with code ${authorization.userCode} " +
                "within ${authorization.expiresIn / SECONDS_PER_MINUTE} minutes",
        )
        return try {
            pollForApproval(authorization, verifier).also { nextDeviceLoginAllowedAt = null }
        } catch (e: JlrAuthException) {
            nextDeviceLoginAllowedAt = clock.now() + DEVICE_LOGIN_COOLDOWN
            throw e
        }
    }

    private suspend fun startDeviceAuthorization(codeChallenge: String): JlrDeviceAuthorizationResponse {
        // The device endpoint takes a client assertion but no DPoP proof, and the assertion's audience is the token URL
        val response = endpoint.postDevice(
            listOf(
                "response_type" to "device_code",
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
            ),
        )
        if (response.status != HttpStatusCode.OK) {
            throw JlrRequestException(
                "JLR device authorization failed: status=${response.status.value} error=${response.errorCode()}",
            )
        }
        return response.decodeBody<JlrDeviceAuthorizationResponse>("device authorization")
    }

    private suspend fun pollForApproval(
        authorization: JlrDeviceAuthorizationResponse,
        verifier: String,
    ): JlrTokenResponse {
        val deadline = clock.now() + minOf(authorization.expiresIn.seconds, DEVICE_LOGIN_MAX_WAIT)
        var interval = authorization.interval.seconds
        while (true) {
            pause(interval)
            val response = endpoint.postToken(
                listOf(
                    "grant_type" to DEVICE_GRANT_TYPE,
                    "device_code" to authorization.deviceCode,
                    "code_verifier" to verifier,
                ),
            )
            val error = response.errorCode()
            when {
                response.status == HttpStatusCode.OK -> return response.tokens()
                error == "slow_down" -> interval += SLOW_DOWN_STEP_SECONDS.seconds
                error != "authorization_pending" -> throw pollFailure(error, response)
            }
            if (clock.now() >= deadline) throw JlrAuthException("The JLR device login was not approved in time")
        }
    }

    private data class CachedToken(val value: String, val expiresAt: Instant) {
        override fun toString(): String = "CachedToken(expiresAt=$expiresAt)"
    }
}

private const val SECONDS_PER_MINUTE = 60

private fun pkceVerifier(): String {
    val bytes = ByteArray(PKCE_VERIFIER_BYTES).also { SecureRandom().nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun pkceChallenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

private suspend fun HttpResponse.tokens(): JlrTokenResponse = decodeBody("token")

/**
 * Reads the body outside the `runCatching`, so a cancellation is not swallowed. A parse error does not become the cause
 * of the exception: its message contains a piece of the body, which holds tokens.
 */
private suspend inline fun <reified T> HttpResponse.decodeBody(what: String): T {
    val text = bodyAsText()
    return runCatching { jlrJson.decodeFromString<T>(text) }
        .getOrElse { throw JlrRequestException("JLR $what response could not be read") }
}

private fun refreshFailure(error: String?, response: HttpResponse): RuntimeException = when (error) {
    "invalid_grant" -> JlrAuthException("The JLR refresh token was rejected ($error), a new device login is needed")

    in ERRORS_THAT_NEED_NEW_LOGIN ->
        JlrAuthException("The JLR client was rejected ($error), check the client id and the key")

    else -> JlrRequestException("JLR token request failed: status=${response.status.value} error=$error")
}

/**
 * No OAuth error means the server failed (5xx, 429 after the retries). That is not the owner's decision, so it is a
 * [JlrRequestException] and no cooldown starts: the next run begins a new login.
 */
private fun pollFailure(error: String?, response: HttpResponse): RuntimeException = when (error) {
    null -> JlrRequestException("JLR device login polling failed: status=${response.status.value}")
    else -> JlrAuthException("The JLR device login failed: error=$error")
}
