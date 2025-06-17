package no.slomic.smarthytte.plugins

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
