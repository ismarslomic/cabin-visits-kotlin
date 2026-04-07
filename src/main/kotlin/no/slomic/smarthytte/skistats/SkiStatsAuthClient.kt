package no.slomic.smarthytte.skistats

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import io.ktor.util.appendAll
import io.ktor.util.logging.KtorSimpleLogger
import no.slomic.smarthytte.plugins.HttpClientProvider
import no.slomic.smarthytte.properties.CoreSkiStatsProperties
import no.slomic.smarthytte.properties.ProfileSkiStatsProperties
import kotlin.time.Clock

/**
 * Custom (non-standard) HTTP header names used by SkiStats.
 *
 * Kept as constants similar to [HttpHeaders] to avoid typos and duplication.
 */
object SkiStatsHttpHeaders {
    const val APP_INSTANCE_ID: String = "x-app-instanceid"
    const val APP_PLATFORM: String = "x-app-platform"
    const val API_KEY: String = "x-api-key"
    const val APP_VERSION: String = "x-app-version"
    const val BUILD_TYPE: String = "x-build-type"
}

/**
 * Handles OAuth token acquisition and refresh against the SkiStats `/oauth/token` endpoint.
 *
 * Uses [HttpClientProvider.client] (shared baseline) for HTTP calls and sets
 * `Authorization: Basic ...` manually per request — no Bearer plugin needed here.
 */
class SkiStatsAuthClient(
    private val httpClient: HttpClient,
    private val coreProps: CoreSkiStatsProperties,
    private val profileProps: ProfileSkiStatsProperties,
) {
    private val logger = KtorSimpleLogger(SkiStatsAuthClient::class.java.name)
    val httpHeaders = commonHttpHeaders(coreProps) + mapOf(
        HttpHeaders.Authorization to profileProps.clientSecret,
    )

    suspend fun passwordGrant(): OAuthTokenResponse = httpClient.post(coreProps.authUrl) {
        headers { appendAll(this@SkiStatsAuthClient.httpHeaders) }
        contentType(ContentType.Application.FormUrlEncoded)
        setBody(
            Parameters.build {
                append("username", profileProps.username)
                append("password", profileProps.password)
                append("grant_type", "password")
                append("agent_id", profileProps.agentId)
            }.formUrlEncode(),
        )
    }.body()

    suspend fun refreshGrant(refreshToken: String): OAuthTokenResponse? {
        val response: HttpResponse = httpClient.post(coreProps.authUrl) {
            headers { appendAll(this@SkiStatsAuthClient.httpHeaders) }
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                Parameters.build {
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                    append("agent_id", profileProps.agentId)
                }.formUrlEncode(),
            )
        }
        if (!response.status.isSuccess()) {
            logger.warn(
                "Refresh token grant failed with status={}, body={}",
                response.status,
                response.bodyAsText(),
            )
            return null
        }
        return response.body()
    }
}

/**
 * Creates a new [HttpClient] with Bearer auth that automatically loads and refreshes tokens
 * for the given [profileId] via the shared [HttpClientProvider] baseline configuration.
 *
 * The caller is responsible for closing the returned client when no longer needed.
 */
fun createSkiStatsApiClient(
    coreProps: CoreSkiStatsProperties,
    skiStatsRepository: SkiTokenRepository,
    profileId: String,
    authClient: SkiStatsAuthClient,
): HttpClient = HttpClientProvider.createClient {
    defaultRequest {
        headers { appendAll(commonHttpHeaders(coreProps)) }
    }

    install(Auth) {
        bearer {
            loadTokens {
                skiStatsRepository.tokensByProfile(profileId)?.let {
                    BearerTokens(it.accessToken, it.refreshToken)
                }
            }
            refreshTokens {
                val old: BearerTokens = oldTokens ?: return@refreshTokens null
                val oldRefresh: String = old.refreshToken ?: return@refreshTokens null

                val refreshed: OAuthTokenResponse = authClient.refreshGrant(oldRefresh)
                    ?: authClient.passwordGrant()

                // Handle refresh token rotation: keep old if the server doesn't return a new one
                val newRefresh = refreshed.refreshToken

                val expiresAtEpochSeconds =
                    refreshed.expiresAtUtc?.epochSeconds
                        ?: refreshed.expiresIn?.let { Clock.System.now().epochSeconds + it }

                val newStored = SkiStatsTokens(
                    accessToken = refreshed.accessToken,
                    refreshToken = newRefresh,
                    expiresAtEpochSeconds = expiresAtEpochSeconds,
                )

                skiStatsRepository.addOrUpdateTokens(profileId, newStored)

                BearerTokens(newStored.accessToken, newStored.refreshToken)
            }
            sendWithoutRequest { request ->
                request.url.toString().startsWith(coreProps.baseUrl)
            }
        }
    }
}

private fun commonHttpHeaders(coreProps: CoreSkiStatsProperties) = mapOf(
    HttpHeaders.AcceptLanguage to "no",
    HttpHeaders.CacheControl to "no-cache",
    HttpHeaders.Cookie to coreProps.cookie,
    HttpHeaders.Accept to ContentType.Application.Json.toString(),
    HttpHeaders.UserAgent to coreProps.userAgent,
    SkiStatsHttpHeaders.APP_INSTANCE_ID to coreProps.appInstanceId,
    SkiStatsHttpHeaders.APP_PLATFORM to coreProps.appPlatform,
    SkiStatsHttpHeaders.API_KEY to coreProps.apiKey,
    SkiStatsHttpHeaders.APP_VERSION to coreProps.appVersion,
    SkiStatsHttpHeaders.BUILD_TYPE to "release",
)
