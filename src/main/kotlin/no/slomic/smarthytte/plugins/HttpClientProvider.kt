package no.slomic.smarthytte.plugins

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientProvider {
    // Drop idle connections after 5 sec
    private const val KEEP_ALIVE_TIME_MILLIS: Long = 5_000

    // Wait 10 sec to establish connection to the server
    private const val CONNECT_TIMEOUT_MILLIS: Long = 10_000

    // Timeout after 30 sec processing HTTP request (from sending to receiving response)
    private const val REQUEST_TIMEOUT_MILLIS: Long = 30_000

    val client: HttpClient by lazy {
        createClient()
    }

    /**
     * Creates a new [HttpClient] with the standard configuration (CIO engine, JSON, timeouts),
     * optionally customized via [additionalConfig].
     *
     * Use this when you need a client with extra plugins (e.g. Auth) on top of the shared baseline.
     * Each call returns a **new** client instance that must be closed by the caller.
     */
    fun createClient(additionalConfig: (HttpClientConfig<CIOEngineConfig>.() -> Unit)? = null): HttpClient =
        HttpClient(CIO) {
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }

            // Allow redirects on POST
            install(HttpRedirect) {
                checkHttpMethod = false // Allow redirects on POST
            }

            // Allows parsing JSON responses
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }

            engine {
                endpoint {
                    keepAliveTime = KEEP_ALIVE_TIME_MILLIS
                    connectTimeout = CONNECT_TIMEOUT_MILLIS
                    requestTimeout = REQUEST_TIMEOUT_MILLIS
                }
            }

            additionalConfig?.invoke(this)
        }
}
