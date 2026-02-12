package no.slomic.smarthytte.skistats

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.properties.ProfileSkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import kotlin.time.Clock

class SkiStatsService(
    private val skiStatsRepository: SkiStatsRepository,
    private val httpClient: HttpClient,
    skiStatsPropertiesHolder: SkiStatsPropertiesHolder = loadProperties(),
) {
    private val logger: Logger = KtorSimpleLogger(SkiStatsService::class.java.name)
    private val properties: SkiStatsProperties = skiStatsPropertiesHolder.skiStats

    suspend fun pollSeasonStats(profile: ProfileSkiStatsProperties) {
        val profileId = profile.id
        logger.info("SkiStats: polling season stats for profile={}", profileId)

        val authClient = createAuthClient(profile)
        ensureLoggedIn(profile, authClient)

        val apiClient = createSkiStatsApiClient(
            coreProps = properties.core,
            skiStatsRepository = skiStatsRepository,
            profileId = profileId,
            authClient = authClient,
        )

        try {
            val seasonJson: String = fetchSeasonJson(apiClient)
            logger.info(seasonJson)
            logger.info("SkiStats: season snapshot persisted for profile={}", profileId)
        } finally {
            // API-klienten er en egen instans (med Auth-plugin) og må stenges
            apiClient.close()
        }
    }

    private suspend fun fetchSeasonJson(apiClient: HttpClient): String =
        apiClient.get(properties.core.seasonStatsUrl) {}.body()

    private fun createAuthClient(profile: ProfileSkiStatsProperties): SkiStatsAuthClient = SkiStatsAuthClient(
        httpClient = httpClient,
        coreProps = properties.core,
        profileProps = profile,
    )

    private suspend fun ensureLoggedIn(profile: ProfileSkiStatsProperties, authClient: SkiStatsAuthClient) {
        val profileId = profile.id
        val existing = skiStatsRepository.tokensByProfile(profileId)
        if (existing != null) return

        logger.info("SkiStats: no tokens found for profile={}, performing password grant", profileId)

        val response = authClient.passwordGrant()

        val expiresAtEpochSeconds =
            response.expiresAtUtc?.epochSeconds
                ?: response.expiresIn?.let { Clock.System.now().epochSeconds + it }

        val stored = SkiStatsTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtEpochSeconds = expiresAtEpochSeconds,
        )

        skiStatsRepository.addOrUpdateTokens(profileId, stored)

        logger.info("SkiStats: initial tokens persisted for profile={}", profileId)
    }
}
