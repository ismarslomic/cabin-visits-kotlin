package no.slomic.smarthytte.skistats

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.slomic.smarthytte.common.osloDateNow
import no.slomic.smarthytte.properties.CoreSkiStatsProperties
import no.slomic.smarthytte.properties.ProfileSkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsProperties
import no.slomic.smarthytte.properties.SkiStatsPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import kotlin.time.Clock
import kotlin.time.Clock.System

@Suppress("TooManyFunctions")
class SkiStatsService(
    private val skiStatsRepository: SkiStatsRepository,
    private val syncCheckpointService: SyncCheckpointService,
    private val httpClient: HttpClient,
    skiStatsPropertiesHolder: SkiStatsPropertiesHolder = loadProperties(),
    private val clock: Clock = System,
    private val apiClientFactory: (
        CoreSkiStatsProperties,
        SkiTokenRepository,
        String,
        SkiStatsAuthClient,
    ) -> HttpClient = ::createSkiStatsApiClient,
) {
    private val logger: Logger = KtorSimpleLogger(SkiStatsService::class.java.name)
    private val properties: SkiStatsProperties = skiStatsPropertiesHolder.skiStats

    suspend fun pollAllLeaderboards() {
        for (profile in properties.profiles) {
            pollLeaderboardsForProfile(profile)
        }
    }

    private suspend fun pollLeaderboardsForProfile(profile: ProfileSkiStatsProperties) {
        val profileId = profile.id
        logger.info("Polling all leaderboards for profile={}", profileId)

        val authClient = createSkiStatsAuthClient(profile)
        ensureLoggedIn(profile, authClient)

        val apiClient = apiClientFactory(properties.core, skiStatsRepository, profileId, authClient)

        apiClient.use {
            pollDayLeaderboards(profileId, it)
            pollWeekLeaderboards(profileId, it)
            pollSeasonLeaderboards(profileId, it)
        }
    }

    private suspend fun pollDayLeaderboards(profileId: String, apiClient: HttpClient) {
        val fromDate: LocalDate = syncCheckpointService.checkpointForSkiStatsDay(profileId)
            ?: LocalDate.parse(properties.friendsLeaderboard.syncFromDate)
        val today: LocalDate = osloDateNow()

        var date = fromDate
        while (date <= today) {
            pollFriendsLeaderboard(PeriodType.DAY, date.toString(), profileId, apiClient)
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }

    private suspend fun pollWeekLeaderboards(profileId: String, apiClient: HttpClient) {
        val fromWeekId: Int = (
            syncCheckpointService.checkpointForSkiStatsWeek(profileId)
                ?: properties.friendsLeaderboard.syncFromWeekId
            ).toInt()
        val currentWeekId: Int = isoWeekId(properties.friendsLeaderboard.syncFromSeasonId)

        var weekId = fromWeekId
        while (weekId <= currentWeekId) {
            pollFriendsLeaderboard(PeriodType.WEEK, weekId.toString(), profileId, apiClient)
            weekId++
        }
    }

    private suspend fun pollSeasonLeaderboards(profileId: String, apiClient: HttpClient) {
        val fromSeasonId: Int = (
            syncCheckpointService.checkpointForSkiStatsSeason(profileId)
                ?: properties.friendsLeaderboard.syncFromSeasonId
            ).toInt()
        val currentSeasonId: Int = properties.friendsLeaderboard.syncFromSeasonId.toInt()

        var seasonId = fromSeasonId
        while (seasonId <= currentSeasonId) {
            pollFriendsLeaderboard(PeriodType.SEASON, seasonId.toString(), profileId, apiClient)
            seasonId++
        }
    }

    internal suspend fun pollFriendsLeaderboard(
        periodType: PeriodType,
        value: String,
        profile: ProfileSkiStatsProperties,
    ) {
        val profileId = profile.id
        logger.info("Polling friends leaderboard for period={} and profile={}", periodType, profileId)

        val authClient = createSkiStatsAuthClient(profile)
        ensureLoggedIn(profile, authClient)

        val apiClient = apiClientFactory(properties.core, skiStatsRepository, profileId, authClient)

        apiClient.use {
            pollFriendsLeaderboard(periodType, value, profileId, it)
        }
    }

    private suspend fun pollFriendsLeaderboard(
        periodType: PeriodType,
        value: String,
        profileId: String,
        apiClient: HttpClient,
    ) {
        val response: FriendsLeaderboardResponse = fetchLeaderboard(periodType, value, apiClient)

        for (entry in response.entries) {
            val skiProfile = SkiProfile(
                id = entry.userId,
                name = entry.name,
                profileImageUrl = entry.profileImageUrl,
                isPrivate = entry.isPrivate,
            )
            skiStatsRepository.addOrUpdateProfile(skiProfile)

            val leaderboardEntry = SkiLeaderboardEntry(
                id = leaderboardEntryId(profileId, periodType, value, entry.userId),
                profileId = profileId,
                periodType = periodType,
                periodValue = value,
                startDate = response.periodData.startDate,
                weekId = response.periodData.weekId,
                seasonId = response.periodData.seasonId,
                leaderboardUpdatedAtUtc = response.updatedAtUtc,
                entryUserId = entry.userId,
                position = entry.position,
                dropHeightInMeter = entry.dropHeightInMeter,
            )
            skiStatsRepository.addOrUpdateLeaderboardEntry(leaderboardEntry)
        }

        updateCheckpoint(periodType, value, profileId)
        logger.info("Friends leaderboard persisted period={} value={} profile={}", periodType, value, profileId)
    }

    private suspend fun updateCheckpoint(periodType: PeriodType, value: String, profileId: String) {
        when (periodType) {
            PeriodType.DAY -> syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay(
                profileId,
                LocalDate.parse(value),
            )

            PeriodType.WEEK -> syncCheckpointService.addOrUpdateCheckpointForSkiStatsWeek(profileId, value)

            PeriodType.SEASON -> syncCheckpointService.addOrUpdateCheckpointForSkiStatsSeason(profileId, value)
        }
    }

    private suspend fun fetchLeaderboard(
        periodType: PeriodType,
        value: String,
        apiClient: HttpClient,
    ): FriendsLeaderboardResponse {
        val url = properties.core.friendsLeaderboardsUrl(periodType.name, value)
        return apiClient.get(url) {}.body()
    }

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

        logger.info("No auth tokens found in database for profile={}, performing password grant", profileId)

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

        logger.info("Initial auth tokens persisted in database for profile={}", profileId)
    }
}

enum class PeriodType {
    DAY,
    WEEK,
    SEASON,
}

fun leaderboardEntryId(profileId: String, periodType: PeriodType, periodValue: String, entryUserId: String) =
    "${profileId}_${periodType.name}_${periodValue}_$entryUserId"

/**
 * Computes the weekId for the current date using ISO week number and the given seasonId.
 *
 * WeekId format: {seasonId}{isoWeekNumber:02d}
 * Example: season "29", ISO week 7 → "2907"
 */
private fun isoWeekId(seasonId: String): Int {
    val now = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Oslo"))
    val isoWeek = now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    return "${seasonId}${isoWeek.toString().padStart(2, '0')}".toInt()
}
