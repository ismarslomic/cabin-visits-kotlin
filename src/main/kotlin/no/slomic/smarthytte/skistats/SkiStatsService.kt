package no.slomic.smarthytte.skistats

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.properties.CoreSkiStatsProperties
import no.slomic.smarthytte.properties.ProfileSkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import kotlin.time.Clock
import kotlin.time.Clock.System

class SkiStatsService(
    private val skiStatsRepository: SkiStatsRepository,
    private val httpClient: HttpClient,
    skiStatsPropertiesHolder: SkiStatsPropertiesHolder = loadProperties(),
    private val clock: Clock = System,
    private val apiClientFactory: (
        CoreSkiStatsProperties,
        SkiStatsRepository,
        String,
        SkiStatsAuthClient,
    ) -> HttpClient = ::createSkiStatsApiClient,
) {
    private val logger: Logger = KtorSimpleLogger(SkiStatsService::class.java.name)
    private val properties: SkiStatsProperties = skiStatsPropertiesHolder.skiStats

    suspend fun pollFriendsLeaderboard(periodType: PeriodType, value: String, profile: ProfileSkiStatsProperties) {
        val profileId = profile.id
        logger.info("Polling friends leaderboard for period={} and profile={}", periodType, profileId)

        val authClient = createSkiStatsAuthClient(profile)
        ensureLoggedIn(profile, authClient)

        val apiClient = apiClientFactory(
            properties.core,
            skiStatsRepository,
            profileId,
            authClient,
        )

        apiClient.use { apiClient ->
            val seasonJson: String = fetchFriendsLeaderboardJson(periodType, value, apiClient)
            logger.info(seasonJson)
            logger.info("Friends leaderboard persisted period={} and profile={}", periodType, profileId)
        }
    }

    private suspend fun fetchFriendsLeaderboardJson(
        periodType: PeriodType,
        value: String,
        apiClient: HttpClient,
    ): String = apiClient.get(properties.core.friendsLeaderboardsUrl(periodType.name, value)) {}.body()

    internal fun createSkiStatsAuthClient(profile: ProfileSkiStatsProperties): SkiStatsAuthClient = SkiStatsAuthClient(
        httpClient = httpClient,
        coreProps = properties.core,
        profileProps = profile,
    )

    /**
     * Ensures the current user's profile is logged in by checking for existing tokens
     * and getting new ones if necessary via the password grant flow.
     *
     * @param profile Represents the user's profile containing the necessary credentials and identifiers
     *                required for authentication.
     * @param authClient An instance of SkiStatsAuthClient responsible for handling the OAuth
     *                   token acquisition process using the given profile details.
     */
    internal suspend fun ensureLoggedIn(profile: ProfileSkiStatsProperties, authClient: SkiStatsAuthClient) {
        val profileId = profile.id
        val existing = skiStatsRepository.tokensByProfile(profileId)
        if (existing != null) return

        logger.info("No tokens found for profile={}, performing password grant", profileId)

        val response = authClient.passwordGrant()

        val expiresAtEpochSeconds =
            response.expiresAtUtc?.epochSeconds
                ?: response.expiresIn?.let { clock.now().epochSeconds + it }

        val stored = SkiStatsTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtEpochSeconds = expiresAtEpochSeconds,
        )

        skiStatsRepository.addOrUpdateTokens(profileId, stored)

        logger.info("Initial tokens persisted for profile={}", profileId)
    }
}

enum class PeriodType {
    DAY,
    WEEK,
    SEASON,
}
