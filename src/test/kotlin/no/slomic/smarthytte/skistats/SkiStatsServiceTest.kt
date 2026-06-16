package no.slomic.smarthytte.skistats

import io.kotest.core.spec.style.StringSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.properties.CoreSkiStatsProperties
import no.slomic.smarthytte.properties.FriendsLeaderboardSkiStatsProperties
import no.slomic.smarthytte.properties.ProfileSkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsPropertiesHolder
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import kotlin.time.Clock
import kotlin.time.Instant

class SkiStatsServiceTest :
    StringSpec({

        val coreProps = CoreSkiStatsProperties(
            baseUrl = "https://api.example.com",
            authPath = "/oauth/token",
            friendsLeaderboardsPath = "/friends/leaderboards",
            statisticsPeriodsPath = "/users/{skiProfileId}/statistics/periods",
            appInstanceId = "ABC-DEF-GHIJKLMN",
            appPlatform = "osx",
            apiKey = "key-foo-bar",
            appVersion = "1.0.0",
            cookie = "CookieConsent={foo:bar}",
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X)",
        )

        val profileProps = ProfileSkiStatsProperties(
            id = "john",
            externalProfileId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
            username = "johndoe",
            password = "supersecretpassword",
            agentId = "agent-123",
            clientId = "my-client-123",
            clientSecret = "instance-id-foo-bar",
        )

        val properties = SkiStatsProperties(
            core = coreProps,
            profiles = listOf(profileProps),
            friendsLeaderboard = FriendsLeaderboardSkiStatsProperties(
                enabled = true,
                syncFrequencyMinutes = 30,
                syncFromDate = "2026-02-15",
                syncFromWeekId = "2907",
                syncFromSeasonId = "29",
            ),
        )

        val propertiesHolder = SkiStatsPropertiesHolder(skiStats = properties)
        val mockSyncCheckpointService = mockk<SyncCheckpointService>(relaxed = true)

        fun createMockHttpClient(): HttpClient {
            val mockEngine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/oauth/token") -> {
                        respond(
                            content = ByteReadChannel(
                                """
                            {
                              "user_id": "user-123",
                              "client_id": "my-client-123",
                              "environment": "Production",
                              "agent_id": "agent-123",
                              "issuedAtUtc": "2026-01-26T22:12:31Z",
                              "expiresAtUtc": "2026-01-26T22:42:31Z",
                              "access_token": "new-access-token",
                              "token_type": "bearer",
                              "expires_in": 1799,
                              "scope": "offline_access",
                              "refresh_token": "new-refresh-token"
                            }
                                """.trimIndent(),
                            ),
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    }

                    else -> respond(
                        content = ByteReadChannel("{}"),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }

            return HttpClient(mockEngine) {
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
        }

        "ensureLoggedIn should not call passwordGrant when tokens exist" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val existingTokens = SkiStatsTokens(
                accessToken = "existing-access-token",
                refreshToken = "existing-refresh-token",
                expiresAtEpochSeconds = 1234567890,
            )

            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns existingTokens

            val httpClient = createMockHttpClient()
            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = httpClient,
                skiStatsPropertiesHolder = propertiesHolder,
            )

            val mockAuthClient = mockk<SkiStatsAuthClient>()

            // When
            service.ensureLoggedIn(profileProps, mockAuthClient)

            // Then
            coVerify(exactly = 1) { mockRepository.tokensByProfile(profileProps.id) }
            coVerify(exactly = 0) { mockRepository.addOrUpdateTokens(profileProps.id, any()) }
            coVerify(exactly = 0) { mockAuthClient.passwordGrant() }
        }

        "ensureLoggedIn should call passwordGrant and store tokens when no tokens exist" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val fixedInstant = Instant.fromEpochSeconds(1000000000)
            val mockClock = object : Clock {
                override fun now() = fixedInstant
            }

            val authResponse = OAuthTokenResponse(
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token",
                expiresIn = 1799,
                userId = "user-123",
                clientId = "my-client-123",
            )

            val mockAuthClient = mockk<SkiStatsAuthClient>()
            coEvery { mockAuthClient.passwordGrant() } returns authResponse
            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns null
            coEvery { mockRepository.addOrUpdateTokens(profileProps.id, any()) } returns PersistenceResult.ADDED

            val httpClient = createMockHttpClient()
            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = httpClient,
                skiStatsPropertiesHolder = propertiesHolder,
                clock = mockClock,
            )

            // When
            service.ensureLoggedIn(profileProps, mockAuthClient)

            // Then
            coVerify(exactly = 1) { mockRepository.tokensByProfile(profileProps.id) }
            coVerify(exactly = 1) { mockAuthClient.passwordGrant() }
            coVerify(exactly = 1) {
                mockRepository.addOrUpdateTokens(
                    profileProps.id,
                    SkiStatsTokens(
                        accessToken = "new-access-token",
                        refreshToken = "new-refresh-token",
                        expiresAtEpochSeconds = 1000001799, // fixedInstant + expiresIn
                    ),
                )
            }
        }

        "ensureLoggedIn should calculate expiresAtEpochSeconds from expiresIn when expiresAtUtc is null" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val fixedInstant = Instant.fromEpochSeconds(1000000000)
            val mockClock = object : Clock {
                override fun now() = fixedInstant
            }

            val authResponse = OAuthTokenResponse(
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token",
                expiresIn = 3600, // 1 hour
                expiresAtUtc = null, // Explicitly null
            )

            val mockAuthClient = mockk<SkiStatsAuthClient>()
            coEvery { mockAuthClient.passwordGrant() } returns authResponse
            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns null
            coEvery { mockRepository.addOrUpdateTokens(any(), any()) } returns PersistenceResult.ADDED

            val httpClient = createMockHttpClient()
            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = httpClient,
                skiStatsPropertiesHolder = propertiesHolder,
                clock = mockClock,
            )

            // When
            service.ensureLoggedIn(profileProps, mockAuthClient)

            // Then - verify expiresAtEpochSeconds was calculated from clock.now() + expiresIn
            coVerify(exactly = 1) {
                mockRepository.addOrUpdateTokens(
                    profileProps.id,
                    SkiStatsTokens(
                        accessToken = "new-access-token",
                        refreshToken = "new-refresh-token",
                        expiresAtEpochSeconds = 1000003600, // 1000000000 + 3600
                    ),
                )
            }
        }

        "ensureLoggedIn should use expiresAtUtc when present in response" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val mockClock = object : Clock {
                override fun now() = Instant.fromEpochSeconds(999999999)
            }

            val expiresAtUtc = Instant.fromEpochSeconds(1800000000)
            val authResponse = OAuthTokenResponse(
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token",
                expiresIn = 3600,
                expiresAtUtc = expiresAtUtc,
            )

            val mockAuthClient = mockk<SkiStatsAuthClient>()
            coEvery { mockAuthClient.passwordGrant() } returns authResponse
            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns null
            coEvery { mockRepository.addOrUpdateTokens(profileProps.id, any()) } returns PersistenceResult.ADDED

            val httpClient = createMockHttpClient()
            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = httpClient,
                skiStatsPropertiesHolder = propertiesHolder,
                clock = mockClock,
            )

            // When
            service.ensureLoggedIn(profileProps, mockAuthClient)

            // Then - verify expiresAtUtc takes precedence over clock.now() + expiresIn
            coVerify(exactly = 1) {
                mockRepository.addOrUpdateTokens(
                    profileProps.id,
                    SkiStatsTokens(
                        accessToken = "new-access-token",
                        refreshToken = "new-refresh-token",
                        expiresAtEpochSeconds = 1800000000, // From expiresAtUtc, not 999999999 + 3600
                    ),
                )
            }
        }

        "ensureLoggedIn should handle response with neither expiresAtUtc nor expiresIn" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val mockClock = object : Clock {
                override fun now() = Instant.fromEpochSeconds(1000000000)
            }

            val authResponse = OAuthTokenResponse(
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token",
                expiresIn = null,
                expiresAtUtc = null,
            )

            val mockAuthClient = mockk<SkiStatsAuthClient>()
            coEvery { mockAuthClient.passwordGrant() } returns authResponse
            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns null
            coEvery { mockRepository.addOrUpdateTokens(profileProps.id, any()) } returns PersistenceResult.ADDED

            val httpClient = createMockHttpClient()
            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = httpClient,
                skiStatsPropertiesHolder = propertiesHolder,
                clock = mockClock,
            )

            // When
            service.ensureLoggedIn(profileProps, mockAuthClient)

            // Then - expiresAtEpochSeconds should be null
            coVerify(exactly = 1) {
                mockRepository.addOrUpdateTokens(
                    profileProps.id,
                    SkiStatsTokens(
                        accessToken = "new-access-token",
                        refreshToken = "new-refresh-token",
                        expiresAtEpochSeconds = null,
                    ),
                )
            }
        }
    })
