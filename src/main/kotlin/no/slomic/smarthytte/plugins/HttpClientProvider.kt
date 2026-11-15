package no.slomic.smarthytte.plugins

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/** How many times a failed request is sent again, so at most 4 attempts in total. */
private const val MAX_RETRIES = 3

/** The wait before a retry grows by this factor with each retry, unless `Retry-After` says more. */
private const val RETRY_DELAY_BASE = 2.0

/** The wait before a retry never gets longer than this, so a request does not hang for minutes. */
private const val RETRY_MAX_DELAY_MILLIS = 30_000L

/** A random extra wait of up to this much is added to each retry, so clients do not all retry at the same moment. */
private const val RETRY_JITTER_MILLIS = 1_000L

/**
 * A request that carries something single use (a DPoP proof, a client assertion) puts a function here that signs the
 * request. It is called for the first attempt and again before every retry, so a retry never repeats a `jti`. Set
 * headers instead of appending: on a retry the old value is still on the request.
 */
val SignRequest = AttributeKey<(HttpRequestBuilder) -> Unit>("SignRequest")

/**
 * Marks a request that must not be retried after a network failure or timeout, because the server may have handled it
 * already (a refresh token grant rotates the token, a retry would send the spent one). Server errors are still retried.
 */
val NoRetryOnException = AttributeKey<Unit>("NoRetryOnException")

/**
 * Retries server errors (5xx), 429 and network failures at most [MAX_RETRIES] times with exponential back-off, which
 * honours the `Retry-After` header. Before each retry the request is signed again, see [SignRequest].
 */
fun HttpClientConfig<*>.installRetry(
    maxDelayMillis: Long = RETRY_MAX_DELAY_MILLIS,
    randomizationMillis: Long = RETRY_JITTER_MILLIS,
) {
    install(HttpRequestRetry) {
        retryIf(MAX_RETRIES) { _, response ->
            response.status.value >= HttpStatusCode.InternalServerError.value ||
                response.status == HttpStatusCode.TooManyRequests
        }
        retryOnExceptionIf(MAX_RETRIES) { request, cause ->
            NoRetryOnException !in request.attributes && cause !is CancellationException
        }
        exponentialDelay(base = RETRY_DELAY_BASE, maxDelayMs = maxDelayMillis, randomizationMs = randomizationMillis)
        modifyRequest { request -> request.attributes.getOrNull(SignRequest)?.invoke(request) }
    }
}

object HttpClientProvider {
    // Drop idle connections after 5 sec
    private const val KEEP_ALIVE_TIME_MILLIS: Long = 5_000

    // Wait 10 sec to establish connection to the server
    private const val CONNECT_TIMEOUT_MILLIS: Long = 10_000

    // Timeout after 30 sec processing HTTP request (from sending to receiving response)
    private const val REQUEST_TIMEOUT_MILLIS: Long = 30_000

    val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }

            // Allow redirects on POST
            install(HttpRedirect) {
                checkHttpMethod = false // Allow redirects on POST
            }

            installRetry()

            // Allows parsing JSON responses
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }

            engine {
                endpoint {
                    keepAliveTime = KEEP_ALIVE_TIME_MILLIS
                    connectTimeout = CONNECT_TIMEOUT_MILLIS
                    requestTimeout = REQUEST_TIMEOUT_MILLIS
                }
            }
        }
    }
}
