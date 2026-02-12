package no.slomic.smarthytte.skistats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.properties.CoreSkiStatsProperties
import no.slomic.smarthytte.properties.ProfileSkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsProperties
import java.util.*

class SkiStatsAuthClientTest :
    StringSpec({
        val coreProps = CoreSkiStatsProperties(
            baseUrl = "https://api.example.com",
            authPath = "/oauth/token",
            seasonStatsPath = "/season",
            appInstanceId = "ABC-DEF-GHIJKLMN",
            appPlatform = "osx",
            apiKey = "key-foo-bar",
            appVersion = "1.0.0",
            cookie = "CookieConsent={foo:bar}",
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X)",
        )
        val profileProp = ProfileSkiStatsProperties(
            id = "john",
            username = "johndoe",
            password = "supersecretpassword",
            agentId = "agent-123",
            clientId = "my-client-123",
            clientSecret = "instance-id-foo-bar",
        )

        val props = SkiStatsProperties(
            core = coreProps,
            profiles = listOf(profileProp),
        )
        val basicAuthHeaderValue =
            "Basic ${
                Base64.getEncoder()
                    .encodeToString("${profileProp.clientId}:${profileProp.clientSecret}".toByteArray())
            }"

        "passwordGrant should make POST request with correct headers and body parameters" {
            var capturedRequest: HttpRequestData? = null

            val mockEngine = MockEngine { request ->
                capturedRequest = request
                respondWithAuthSuccess(this)
            }

            val httpClient = HttpClient(mockEngine) {
                install(HttpCookies) {
                    storage = AcceptAllCookiesStorage()
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                        },
                    )
                }
            }

            val authClient = SkiStatsAuthClient(
                httpClient = httpClient,
                coreProps = props.core,
                profileProps = profileProp,
            )

            // When
            val response = authClient.passwordGrant()

            // Then: verify request was made with correct parameters
            capturedRequest.shouldNotBeNull()
            capturedRequest.apply {
                "${url.protocol.name}://${url.host}" shouldBe coreProps.baseUrl
                url.encodedPath shouldBe coreProps.authPath
                method shouldBe HttpMethod.Post

                // Verify Authorization header
                assertRequestHasHeader(headers, coreProps, basicAuthHeaderValue)
                headers[HttpHeaders.Authorization] shouldBe basicAuthHeaderValue

                // Verify Content-Type header (check the body's content type instead)
                body.contentType shouldBe ContentType.Application.FormUrlEncoded

                // Verify body parameters
                val bodyContent = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                val params = Parameters.build {
                    bodyContent.split("&").forEach { part ->
                        val (key, value) = part.split("=")
                        append(key, value)
                    }
                }
                params["username"] shouldBe profileProp.username
                params["password"] shouldBe profileProp.password
                params["agent_id"] shouldBe profileProp.agentId
                params["grant_type"] shouldBe "password"
            }

            // Verify response
            response.accessToken shouldBe "mock-access-token"
            response.refreshToken shouldBe "mock-refresh-token"
        }

        "refreshGrant should make POST request with correct headers and body parameters" {
            var capturedRequest: HttpRequestData? = null

            val mockEngine = MockEngine { request ->
                capturedRequest = request
                respondWithAuthSuccess(this)
            }

            val httpClient = HttpClient(mockEngine) {
                install(HttpCookies) {
                    storage = AcceptAllCookiesStorage()
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                        },
                    )
                }
            }

            val authClient = SkiStatsAuthClient(
                httpClient = httpClient,
                coreProps = props.core,
                profileProps = profileProp,
            )

            // When
            val response = authClient.refreshGrant(refreshToken = "existing-refresh-token")

            // Then: verify request was made with correct parameters
            capturedRequest.shouldNotBeNull()
            capturedRequest.apply {
                "${url.protocol.name}://${url.host}" shouldBe coreProps.baseUrl
                url.encodedPath shouldBe coreProps.authPath
                method shouldBe HttpMethod.Post

                // Verify Authorization header
                headers[HttpHeaders.Authorization] shouldBe basicAuthHeaderValue

                // Verify Content-Type header (check the body's content type instead)
                body.contentType shouldBe ContentType.Application.FormUrlEncoded

                // Verify body parameters
                val bodyContent = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                val params = Parameters.build {
                    bodyContent.split("&").forEach { part ->
                        val (key, value) = part.split("=")
                        append(key, value)
                    }
                }
                params["refresh_token"] shouldBe "existing-refresh-token"
                params["agent_id"] shouldBe profileProp.agentId
                params["grant_type"] shouldBe "refresh_token"
            }

            // Verify response
            response.accessToken shouldBe "mock-access-token"
            response.refreshToken shouldBe "mock-refresh-token"
        }
    })

private fun assertRequestHasHeader(
    headers: Headers,
    coreProps: CoreSkiStatsProperties,
    expectedAuthorization: String,
) {
    headers.apply {
        get(HttpHeaders.Accept) shouldBe "application/json"
        get(HttpHeaders.AcceptLanguage) shouldBe "no"
        get(HttpHeaders.CacheControl) shouldBe "no-cache"
        get(HttpHeaders.UserAgent) shouldBe coreProps.userAgent
        get(HttpHeaders.Cookie) shouldBe coreProps.cookie
        get(HttpHeaders.Authorization) shouldBe expectedAuthorization
        get("x-app-instanceid") shouldBe coreProps.appInstanceId
        get("x-app-platform") shouldBe coreProps.appPlatform
        get("x-api-key") shouldBe coreProps.apiKey
        get("x-app-version") shouldBe coreProps.appVersion
        get("x-build-type") shouldBe "release"
    }
}

private fun respondWithAuthSuccess(scope: MockRequestHandleScope) = scope.respond(
    content = ByteReadChannel(
        """
        {
          "user_id": "user-123",
          "client_id": "my-client-123",
          "environment": "Production",
          "agent_id": "agent-123",
          "issuedAtUtc": "2026-01-26T22:12:31Z",
          "expiresAtUtc": "2026-01-26T22:42:31Z",
          "access_token": "mock-access-token",
          "token_type": "bearer",
          "expires_in": 1799,
          "scope": "offline_access",
          "refresh_token": "mock-refresh-token"
        }
        """.trimIndent(),
    ),
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
