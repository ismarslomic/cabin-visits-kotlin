package no.slomic.smarthytte.skistats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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

@Suppress("MaxLineLength")
private const val STATISTICS_PERIODS_JSON =
    """{"userId":"john","seasons":[{"id":"29","name":"Season 29","type":"alpine","weeks":[{"id":"2907","year":2026,"weekNumber":7,"days":[{"date":"2026-02-15","destinationIds":[]}]}]}],"updatedAtUtc":"2026-02-15T18:15:07Z"}"""

@Suppress("MaxLineLength")
private const val EMPTY_LEADERBOARD_JSON =
    """{"userId":"john","periodData":{"periodType":"Day","startDate":"2026-02-15","seasonId":"29"},"entries":[],"user":{"position":0,"userId":"john","isPrivate":false,"name":"John","value":0},"updatedAtUtc":"2026-02-15T18:15:07Z"}"""

class SkiStatsServicePollLeaderboardsTest :
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

        fun buildMockApiClient(mockEngine: MockEngine): HttpClient = HttpClient(mockEngine) {
            install(HttpCookies) { storage = AcceptAllCookiesStorage() }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
        }

        "should fetch DAY leaderboard when tokens exist" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val existingTokens = SkiStatsTokens(
                accessToken = "existing-access-token",
                refreshToken = "existing-refresh-token",
                expiresAtEpochSeconds = 1800000000,
            )

            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns existingTokens
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsDay(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsWeek(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsSeason(any()) } returns null

            val mockEngine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("/statistics/periods") -> respond(
                        content = ByteReadChannel(STATISTICS_PERIODS_JSON),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )

                    else -> respond(
                        content = ByteReadChannel(EMPTY_LEADERBOARD_JSON),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }

            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = createMockHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = { _, _, _, _ -> buildMockApiClient(mockEngine) },
            )

            // When
            service.pollAllLeaderboards()

            // Then
            coVerify(exactly = 1) { mockRepository.tokensByProfile(profileProps.id) }
            coVerify(exactly = 0) { mockRepository.addOrUpdateTokens(any(), any()) }
        }

        "should fetch WEEK leaderboard when tokens exist" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val existingTokens = SkiStatsTokens(
                accessToken = "existing-access-token",
                refreshToken = "existing-refresh-token",
                expiresAtEpochSeconds = 1800000000,
            )

            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns existingTokens
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsDay(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsWeek(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsSeason(any()) } returns null

            val mockEngine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("/statistics/periods") -> respond(
                        content = ByteReadChannel(STATISTICS_PERIODS_JSON),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )

                    else -> respond(
                        content = ByteReadChannel(EMPTY_LEADERBOARD_JSON),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }

            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = createMockHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = { _, _, _, _ -> buildMockApiClient(mockEngine) },
            )

            // When
            service.pollAllLeaderboards()

            // Then
            coVerify(exactly = 1) { mockRepository.tokensByProfile(profileProps.id) }
            coVerify(exactly = 0) { mockRepository.addOrUpdateTokens(any(), any()) }
        }

        "should fetch SEASON leaderboard when tokens exist" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val existingTokens = SkiStatsTokens(
                accessToken = "existing-access-token",
                refreshToken = "existing-refresh-token",
                expiresAtEpochSeconds = 1800000000,
            )

            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns existingTokens
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsDay(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsWeek(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsSeason(any()) } returns null

            val mockEngine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("/statistics/periods") -> respond(
                        content = ByteReadChannel(STATISTICS_PERIODS_JSON),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )

                    else -> respond(
                        content = ByteReadChannel(EMPTY_LEADERBOARD_JSON),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }

            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = createMockHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = { _, _, _, _ -> buildMockApiClient(mockEngine) },
            )

            // When
            service.pollAllLeaderboards()

            // Then
            coVerify(exactly = 1) { mockRepository.tokensByProfile(profileProps.id) }
            coVerify(exactly = 0) { mockRepository.addOrUpdateTokens(any(), any()) }
        }

        "should poll week leaderboards in chronological order when week IDs are not monotonic across year boundary" {
            // Given - season 29 has id 2952 (year 2025, w52) and id 2903 (year 2026, w3).
            // Numerically 2903 < 2952, so the old int-sort gave the wrong order [2903, 2952].
            // The correct chronological order is [2952, 2903].
            @Suppress("MaxLineLength")
            val crossYearPeriodsJson =
                """{"userId":"john","seasons":[{"id":"29","name":"2025/2026","type":"alpine","weeks":[{"id":"2952","year":2025,"weekNumber":52,"days":[{"date":"2025-12-22","destinationIds":[]}]},{"id":"2903","year":2026,"weekNumber":3,"days":[{"date":"2026-01-17","destinationIds":[]}]}]}],"updatedAtUtc":"2026-02-15T18:15:07Z"}"""

            // syncFromWeekId "2949" is not present in the response → fromWeek == null → all weeks included
            val customPropertiesHolder = SkiStatsPropertiesHolder(
                skiStats = SkiStatsProperties(
                    core = coreProps,
                    profiles = listOf(profileProps),
                    friendsLeaderboard = FriendsLeaderboardSkiStatsProperties(
                        enabled = true,
                        syncFrequencyMinutes = 30,
                        syncFromDate = "2025-12-22",
                        syncFromWeekId = "2949",
                        syncFromSeasonId = "29",
                    ),
                ),
            )

            val mockRepository = mockk<SkiStatsRepository>()
            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns SkiStatsTokens(
                accessToken = "t",
                refreshToken = "r",
                expiresAtEpochSeconds = 1800000000,
            )
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsWeek(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsDay(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsSeason(any()) } returns null

            val polledWeekIds = mutableListOf<String>()
            val mockEngine = MockEngine { request ->
                val content = when {
                    request.url.encodedPath.contains("/statistics/periods") -> crossYearPeriodsJson

                    request.url.encodedPath.contains("/week/") -> {
                        polledWeekIds.add(request.url.encodedPath.substringAfterLast("/"))
                        EMPTY_LEADERBOARD_JSON
                    }

                    else -> EMPTY_LEADERBOARD_JSON
                }
                respond(
                    content = ByteReadChannel(content),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = createMockHttpClient(),
                skiStatsPropertiesHolder = customPropertiesHolder,
                apiClientFactory = { _, _, _, _ -> buildMockApiClient(mockEngine) },
            )

            // When
            service.pollAllLeaderboards()

            // Then - week 2952 (2025/w52) must be polled before week 2903 (2026/w3)
            polledWeekIds shouldBe listOf("2952", "2903")
        }

        "should include early-numbered week of following year when checkpoint is at late-numbered week" {
            // Given - checkpoint "2952" (year 2025, w52). Periods also has "2903" (year 2026, w3).
            // With the old int-sort: 2903 < 2952 → week 3 of 2026 was incorrectly excluded.
            // With the fix: (2026, 3) > (2025, 52) → week 3 of 2026 is correctly included.
            @Suppress("MaxLineLength")
            val crossYearPeriodsJson =
                """{"userId":"john","seasons":[{"id":"29","name":"2025/2026","type":"alpine","weeks":[{"id":"2952","year":2025,"weekNumber":52,"days":[{"date":"2025-12-22","destinationIds":[]}]},{"id":"2903","year":2026,"weekNumber":3,"days":[{"date":"2026-01-17","destinationIds":[]}]}]}],"updatedAtUtc":"2026-02-15T18:15:07Z"}"""

            val mockRepository = mockk<SkiStatsRepository>()
            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns SkiStatsTokens(
                accessToken = "t",
                refreshToken = "r",
                expiresAtEpochSeconds = 1800000000,
            )
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsWeek(any()) } returns "2952"
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsDay(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsSeason(any()) } returns null

            val polledWeekIds = mutableListOf<String>()
            val mockEngine = MockEngine { request ->
                val content = when {
                    request.url.encodedPath.contains("/statistics/periods") -> crossYearPeriodsJson

                    request.url.encodedPath.contains("/week/") -> {
                        polledWeekIds.add(request.url.encodedPath.substringAfterLast("/"))
                        EMPTY_LEADERBOARD_JSON
                    }

                    else -> EMPTY_LEADERBOARD_JSON
                }
                respond(
                    content = ByteReadChannel(content),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = createMockHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = { _, _, _, _ -> buildMockApiClient(mockEngine) },
            )

            // When
            service.pollAllLeaderboards()

            // Then - both 2952 (re-fetch checkpoint) and 2903 (following year) are polled
            polledWeekIds shouldBe listOf("2952", "2903")
        }

        "should perform login and fetch leaderboards when no tokens exist" {
            // Given
            val mockRepository = mockk<SkiStatsRepository>()
            val fixedInstant = Instant.fromEpochSeconds(1000000000)
            val mockClock = object : Clock {
                override fun now() = fixedInstant
            }

            coEvery { mockRepository.tokensByProfile(profileProps.id) } returns null
            coEvery { mockRepository.addOrUpdateTokens(any(), any()) } returns PersistenceResult.ADDED
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsDay(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsWeek(any()) } returns null
            coEvery { mockSyncCheckpointService.checkpointForSkiStatsSeason(any()) } returns null

            var requestCount = 0
            val mockEngine = MockEngine { request ->
                requestCount++
                when {
                    request.url.encodedPath.endsWith("/oauth/token") -> respond(
                        content = ByteReadChannel(
                            """
                            {
                              "user_id": "user-123",
                              "client_id": "my-client-123",
                              "access_token": "new-access-token",
                              "token_type": "bearer",
                              "expires_in": 1799,
                              "refresh_token": "new-refresh-token"
                            }
                            """.trimIndent(),
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )

                    request.url.encodedPath.contains("/statistics/periods") -> respond(
                        content = ByteReadChannel(STATISTICS_PERIODS_JSON),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )

                    else -> respond(
                        content = ByteReadChannel(EMPTY_LEADERBOARD_JSON),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
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

            val service = SkiStatsService(
                skiStatsRepository = mockRepository,
                syncCheckpointService = mockSyncCheckpointService,
                httpClient = httpClient,
                skiStatsPropertiesHolder = propertiesHolder,
                clock = mockClock,
                apiClientFactory = { _, _, _, _ -> buildMockApiClient(mockEngine) },
            )

            // When
            service.pollAllLeaderboards()

            // Then
            coVerify(exactly = 1) { mockRepository.tokensByProfile(profileProps.id) }
            coVerify(exactly = 1) {
                mockRepository.addOrUpdateTokens(
                    profileProps.id,
                    SkiStatsTokens(
                        accessToken = "new-access-token",
                        refreshToken = "new-refresh-token",
                        expiresAtEpochSeconds = 1000001799,
                    ),
                )
            }
            requestCount shouldBe 5 // auth + statistics/periods + day + week + season
        }
    })
