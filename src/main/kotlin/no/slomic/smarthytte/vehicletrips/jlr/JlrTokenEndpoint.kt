package no.slomic.smarthytte.vehicletrips.jlr

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.plugins.NoRetryOnException
import no.slomic.smarthytte.plugins.SignRequest

internal const val AUTH_BASE_URL = "https://auth.jlr-vcdp.com/realms/public/protocol/openid-connect"
internal const val TOKEN_URL = "$AUTH_BASE_URL/token"
internal const val DEVICE_URL = "$AUTH_BASE_URL/auth/device"
private const val CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
internal const val DPOP_HEADER = "DPoP"
internal const val DPOP_NONCE_HEADER = "DPoP-Nonce"

internal val jlrJson = Json { ignoreUnknownKeys = true }

/**
 * Posts forms to the auth server. Every request carries a client assertion (audience: the token URL, also for the
 * device endpoint) and, for the token endpoint, a DPoP proof. Both are single use, so they are made again for every
 * attempt, also a retry by the HTTP client (see [SignRequest]). Retries once when the server asks for a DPoP nonce.
 */
internal class JlrTokenEndpoint(
    private val httpClient: HttpClient,
    private val signer: JlrJwtSigner,
    private val clientId: String,
) {
    @Volatile
    var dpopNonce: String? = null

    /** Device authorization request: a client assertion, no DPoP proof. */
    suspend fun postDevice(fields: List<Pair<String, String>>): HttpResponse =
        postForm(DEVICE_URL, withDpop = false, fields = fields)

    /**
     * Token request with a DPoP proof, retried once when the server answers `use_dpop_nonce`. Pass
     * `retryOnException = false` for a refresh: after a timeout the server may have rotated the token already, and the
     * HTTP client's retry would send the spent one.
     */
    suspend fun postToken(fields: List<Pair<String, String>>, retryOnException: Boolean = true): HttpResponse {
        val first = postForm(TOKEN_URL, withDpop = true, fields, retryOnException)
        val nonce = first.headers[DPOP_NONCE_HEADER]
        val needsNonce = nonce != null && first.errorCode() == "use_dpop_nonce"
        return if (needsNonce) {
            dpopNonce = nonce
            postForm(TOKEN_URL, withDpop = true, fields, retryOnException)
        } else {
            first
        }
    }

    private suspend fun postForm(
        url: String,
        withDpop: Boolean,
        fields: List<Pair<String, String>>,
        retryOnException: Boolean = true,
    ): HttpResponse = httpClient.post(url) {
        if (!retryOnException) attributes.put(NoRetryOnException, Unit)
        val sign: (HttpRequestBuilder) -> Unit = { request ->
            val form: Parameters = parameters {
                append("client_id", clientId)
                append("client_assertion_type", CLIENT_ASSERTION_TYPE)
                append("client_assertion", signer.clientAssertion(clientId, audience = TOKEN_URL))
                fields.forEach { (name, value) -> append(name, value) }
            }
            request.setBody(FormDataContent(form))
            // Replace, not append: on a retry the old proof is still on the request
            if (withDpop) request.headers[DPOP_HEADER] = signer.dpopProof("POST", url, nonce = dpopNonce)
        }
        attributes.put(SignRequest, sign)
        sign(this)
    }
}

/** The OAuth `error` of an answer, null when the body has none or is not JSON. A cancellation is not swallowed. */
internal suspend fun HttpResponse.errorCode(): String? {
    val text = bodyAsText()
    return runCatching { jlrJson.decodeFromString<JlrErrorResponse>(text).error }.getOrNull()
}

/**
 * Reads the body outside the `runCatching`, so a cancellation is not swallowed. A parse error does not become the cause
 * of the exception: its message contains a piece of the body, which holds tokens.
 */
internal suspend inline fun <reified T> HttpResponse.decodeBody(what: String): T {
    val text = bodyAsText()
    return runCatching { jlrJson.decodeFromString<T>(text) }
        .getOrElse { throw JlrRequestException("JLR $what response could not be read") }
}
