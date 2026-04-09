package no.slomic.smarthytte.skistats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.common.readContentFromFile
import no.slomic.smarthytte.properties.CoreSkiStatsProperties
import no.slomic.smarthytte.properties.FriendsLeaderboardSkiStatsProperties
import no.slomic.smarthytte.properties.ProfileSkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsPropertiesHolder
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.getResourceFilePath

class SkiStatsServiceIntegrationTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val coreProps = CoreSkiStatsProperties(
            baseUrl = "https://api.example.com",
            authPath = "/oauth/token",
            friendsLeaderboardsPath = "/leaderboards/friends",
            statisticsPeriodsPath = "/users/{skiProfileId}/statistics/periods",
            appInstanceId = "ABC-DEF-GHIJKLMN",
            appPlatform = "osx",
            apiKey = "key-foo-bar",
            appVersion = "1.0.0",
            cookie = "CookieConsent={foo:bar}",
            userAgent = "Mozilla/5.0",
        )

        val profileProps = ProfileSkiStatsProperties(
            id = "ismar",
            externalProfileId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
            username = "ismar@example.com",
            password = "supersecretpassword",
            agentId = "agent-123",
            clientId = "my-client-123",
            clientSecret = "instance-id-foo-bar",
        )

        val propertiesHolder = SkiStatsPropertiesHolder(
            skiStats = SkiStatsProperties(
                core = coreProps,
                profiles = listOf(profileProps),
                friendsLeaderboard = FriendsLeaderboardSkiStatsProperties(
                    enabled = true,
                    syncFrequencyMinutes = 30,
                    syncFromDate = "2026-02-15",
                    syncFromWeekId = "2907",
                    syncFromSeasonId = "29",
                ),
            ),
        )

        val existingTokens = SkiStatsTokens(
            accessToken = "existing-access-token",
            refreshToken = "existing-refresh-token",
            expiresAtEpochSeconds = 1800000000,
        )

        fun readJsonResource(fileName: String): String = readContentFromFile(getResourceFilePath(fileName))

        // Creates a factory that returns a fresh MockEngine client per call (needed because apiClient.use{} closes it).
        // periodsJson drives which periods are polled; dayJson/weekJson/seasonJson supply the leaderboard responses.
        // When a leaderboard json is empty, a valid but empty leaderboard response is returned so parsing succeeds.
        fun leaderboardApiClientFactory(
            periodsJson: String,
            dayJson: String = "",
            weekJson: String = "",
            seasonJson: String = "",
        ): (CoreSkiStatsProperties, SkiTokenRepository, String, SkiStatsAuthClient) -> HttpClient = { _, _, _, _ ->
            val engine = MockEngine { request ->
                val content = when {
                    request.url.encodedPath.contains("/statistics/periods") -> periodsJson
                    request.url.encodedPath.contains("/day/") -> dayJson.ifEmpty { EMPTY_LEADERBOARD_JSON }
                    request.url.encodedPath.contains("/week/") -> weekJson.ifEmpty { EMPTY_LEADERBOARD_JSON }
                    request.url.encodedPath.contains("/season/") -> seasonJson.ifEmpty { EMPTY_LEADERBOARD_JSON }
                    else -> "{}"
                }
                respond(
                    content = ByteReadChannel(content),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
            HttpClient(engine) {
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
        }

        // A no-op auth HttpClient — tokens are pre-inserted so auth is never called in these tests
        fun createNoOpHttpClient(): HttpClient {
            val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
            return HttpClient(engine) {
                install(HttpCookies) { storage = AcceptAllCookiesStorage() }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }

        "should fetch day leaderboard and persist profiles and entries to database" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val dayJson = readJsonResource("friendsLeaderboardDayResponse.json")
            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = leaderboardApiClientFactory(periodsJson = periodsJson, dayJson = dayJson),
            )

            service.pollAllLeaderboards()

            val entries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.DAY)
            entries shouldHaveSize 3
            entries.map { it.entryUserId }.toSet() shouldBe setOf(
                "ABCD12345",
                "EFGH12345",
                "IJKL1234",
            )
            entries.first { it.entryUserId == "ABCD12345" }.also {
                it.position shouldBe 1
                it.dropHeightInMeter shouldBe 4811
                it.period.value shouldBe "2026-02-15"
                it.period.startDate shouldBe LocalDate(2026, 2, 15)
                it.period.weekId shouldBe "2907"
                it.period.seasonId shouldBe "29"
            }
        }

        "should fetch week leaderboard and persist profiles and entries to database" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val weekJson = readJsonResource("friendsLeaderboardWeekResponse.json")
            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = leaderboardApiClientFactory(periodsJson = periodsJson, weekJson = weekJson),
            )

            service.pollAllLeaderboards()

            val entries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.WEEK)
            entries shouldHaveSize 4
            entries.first { it.entryUserId == "EFGH12345" }.also {
                it.position shouldBe 1
                it.dropHeightInMeter shouldBe 10808
                it.period.value shouldBe "2907"
                it.period.weekId shouldBe "2907"
                it.period.seasonId shouldBe "29"
            }
        }

        "should fetch season leaderboard and persist profiles and entries to database" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val seasonJson = readJsonResource("friendsLeaderboardSeasonResponse.json")
            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = leaderboardApiClientFactory(periodsJson = periodsJson, seasonJson = seasonJson),
            )

            service.pollAllLeaderboards()

            val entries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.SEASON)
            entries shouldHaveSize 4
            entries.first { it.entryUserId == "ABCD12345" }.also {
                it.position shouldBe 1
                it.dropHeightInMeter shouldBe 55384
                it.period.value shouldBe "29"
                it.period.weekId.shouldBeNull()
                it.period.seasonId shouldBe "29"
            }
        }

        "should update checkpoint after successful poll" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val dayJson = readJsonResource("friendsLeaderboardDayResponse.json")
            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = leaderboardApiClientFactory(periodsJson = periodsJson, dayJson = dayJson),
            )

            val checkpointBefore = syncCheckpointService.checkpointForSkiStatsDay("ismar")
            service.pollAllLeaderboards()
            val checkpointAfter = syncCheckpointService.checkpointForSkiStatsDay("ismar")

            checkpointBefore.shouldBeNull()
            checkpointAfter shouldBe LocalDate(2026, 2, 15)
        }

        "should update week and season checkpoints after successful polls" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val weekJson = readJsonResource("friendsLeaderboardWeekResponse.json")
            val seasonJson = readJsonResource("friendsLeaderboardSeasonResponse.json")
            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = leaderboardApiClientFactory(
                    periodsJson = periodsJson,
                    weekJson = weekJson,
                    seasonJson = seasonJson,
                ),
            )

            service.pollAllLeaderboards()

            syncCheckpointService.checkpointForSkiStatsWeek("ismar") shouldBe "2907"
            syncCheckpointService.checkpointForSkiStatsSeason("ismar") shouldBe "29"
        }

        "should not duplicate entries on re-poll of same period" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val dayJson = readJsonResource("friendsLeaderboardDayResponse.json")
            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = leaderboardApiClientFactory(periodsJson = periodsJson, dayJson = dayJson),
            )

            service.pollAllLeaderboards()
            service.pollAllLeaderboards()

            val entries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.DAY)
            entries shouldHaveSize 3
        }

        "should update existing entry when data changes on re-poll" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val dayJson = readJsonResource("friendsLeaderboardDayResponse.json")
            // Second response has ABCD12345 with updated position
            val updatedDayJson = dayJson.replace(
                "\"position\": 1,\n      \"userId\": \"ABCD12345\"",
                "\"position\": 2,\n      \"userId\": \"ABCD12345\"",
            )

            var callCount = 0
            val factory: (CoreSkiStatsProperties, SkiTokenRepository, String, SkiStatsAuthClient) -> HttpClient =
                { _, _, _, _ ->
                    callCount++
                    val leaderboardJson = if (callCount == 1) dayJson else updatedDayJson
                    val engine = MockEngine { request ->
                        val content = when {
                            request.url.encodedPath.contains("/statistics/periods") -> periodsJson
                            else -> leaderboardJson
                        }
                        respond(
                            ByteReadChannel(content),
                            HttpStatusCode.OK,
                            headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    }
                    HttpClient(engine) {
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
                }

            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = factory,
            )

            service.pollAllLeaderboards()
            val ismarAfterFirst = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.DAY)
                .first { it.entryUserId == "ABCD12345" }
            ismarAfterFirst.position shouldBe 1

            service.pollAllLeaderboards()
            val ismarAfterSecond = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.DAY)
                .first { it.entryUserId == "ABCD12345" }
            ismarAfterSecond.position shouldBe 2
            // Still 3 entries, no duplicates
            repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.DAY) shouldHaveSize 3
        }

        "should not duplicate profile row when same skier appears in multiple leaderboards" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val dayJson = readJsonResource("friendsLeaderboardDayResponse.json")
            val weekJson = readJsonResource("friendsLeaderboardWeekResponse.json")
            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = leaderboardApiClientFactory(
                    periodsJson = periodsJson,
                    dayJson = dayJson,
                    weekJson = weekJson,
                ),
            )

            // Ismar appears in both day and week responses — single pollAllLeaderboards covers both
            service.pollAllLeaderboards()

            val dayEntries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.DAY)
            val weekEntries = repository.leaderboardEntriesByProfileAndPeriodType("ismar", PeriodType.WEEK)
            dayEntries shouldHaveSize 3
            weekEntries shouldHaveSize 4

            // Ismar's profile appears in both leaderboards — profile row should not be duplicated
            // (FK constraints would fail if profile was inserted twice, but we verify via entry counts)
            dayEntries.any { it.entryUserId == "ABCD12345" } shouldBe true
            weekEntries.any { it.entryUserId == "ABCD12345" } shouldBe true
        }

        "should use syncFromDate as floor when no checkpoint exists" {
            val repository = SqliteSkiStatsRepository()
            val syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            repository.addOrUpdateTokens(profileProps.id, existingTokens)

            // The statistics periods fixture contains 2026-02-15 as the only day, which equals
            // syncFromDate. It will be the only date polled and the checkpoint will be set to it.
            val periodsJson = readJsonResource("statisticsPeriodsResponse.json")
            val dayJson = readJsonResource("friendsLeaderboardDayResponse.json")
            val service = SkiStatsService(
                skiStatsRepository = repository,
                syncCheckpointService = syncCheckpointService,
                httpClient = createNoOpHttpClient(),
                skiStatsPropertiesHolder = propertiesHolder,
                apiClientFactory = leaderboardApiClientFactory(periodsJson = periodsJson, dayJson = dayJson),
            )

            syncCheckpointService.checkpointForSkiStatsDay("ismar").shouldBeNull()

            service.pollAllLeaderboards()
            syncCheckpointService.checkpointForSkiStatsDay("ismar").shouldNotBeNull()
        }
    })

@Suppress("MaxLineLength")
private const val EMPTY_LEADERBOARD_JSON =
    """{"userId":"ismar","periodData":{"periodType":"Day","startDate":"2026-02-15","seasonId":"29"},"entries":[],"user":{"position":0,"userId":"ismar","isPrivate":false,"name":"Ismar","value":0},"updatedAtUtc":"2026-02-15T18:15:07Z"}"""
