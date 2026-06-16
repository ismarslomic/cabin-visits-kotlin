package no.slomic.smarthytte.skistats

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.PersistenceResults
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
            val periods = fetchStatisticsPeriods(profile.externalProfileId, it)
            pollDayLeaderboards(profileId, periods, it)
            pollWeekLeaderboards(profileId, periods, it)
            pollSeasonLeaderboards(profileId, periods, it)
        }
    }

    /**
     * Polls the friends leaderboard for every day in [periods] on or after the floor date.
     *
     * **Initial poll** (no checkpoint exists): uses `syncFromDate` from properties as the floor date,
     * so all days in [periods] from that date onwards are fetched.
     *
     * **Incremental poll** (checkpoint exists): uses the last successfully persisted day from
     * [SyncCheckpointService.checkpointForSkiStatsDay] as the floor, so only days that have not yet
     * been synced are fetched. This also re-fetches the checkpoint day itself, which handles late-arriving
     * leaderboard updates for the most recently polled day.
     *
     * @param profileId The internal profile identifier used to look up the checkpoint.
     * @param periods All seasons/weeks/days where the user has ski statistics, as returned by the
     *                statistics periods API. Only days at or after the floor date are polled.
     * @param apiClient The authenticated HTTP client used to fetch leaderboard data.
     */
    private suspend fun pollDayLeaderboards(
        profileId: String,
        periods: StatisticsPeriodResponse,
        apiClient: HttpClient,
    ) {
        val checkpoint: LocalDate? = syncCheckpointService.checkpointForSkiStatsDay(profileId)
        val fromDate: LocalDate = checkpoint ?: LocalDate.parse(properties.friendsLeaderboard.syncFromDate)

        if (checkpoint == null) {
            logger.info("Performing full fetch of day leaderboards for profile={} from {}", profileId, fromDate)
        } else {
            logger.info("Performing incremental fetch of day leaderboards for profile={} from {}", profileId, fromDate)
        }

        val seasonWeekDays = periods.seasons
            .flatMap { season ->
                season.weeks.flatMap { week ->
                    week.days.map { Triple(season, week, LocalDate.parse(it.date)) }
                }
            }
            .filter { (_, _, date) -> date >= fromDate }
            .sortedBy { (_, _, date) -> date }

        for ((season, week, date) in seasonWeekDays) {
            fetchFriendsLeaderboard(
                periodType = PeriodType.DAY,
                value = date.toString(),
                profileId = profileId,
                apiClient = apiClient,
                seasonName = season.name,
                year = week.year,
                weekNumber = week.weekNumber,
            )
        }
    }

    /**
     * Polls the friends leaderboard for every week in [periods] on or after the floor week.
     *
     * **Initial poll** (no checkpoint exists): uses `syncFromWeekId` from properties as the floor,
     * so all weeks in [periods] from that week onwards are fetched.
     *
     * **Incremental poll** (checkpoint exists): uses the last successfully persisted week ID from
     * [SyncCheckpointService.checkpointForSkiStatsWeek] as the floor. The checkpoint week itself is
     * also re-fetched, which handles late-arriving leaderboard updates for the most recently polled week.
     *
     * Weeks are sorted and compared by `(year, weekNumber)` rather than by their numeric ID, because
     * week IDs are not globally monotonic: a high-numbered week in year N (e.g. id `2952` = week 52 of
     * 2025) has a larger numeric ID than an early week in year N+1 (e.g. id `2903` = week 3 of 2026),
     * even though it is chronologically earlier.
     *
     * If the floor week ID is not found in [periods] (e.g. the API no longer returns that week), all
     * weeks in [periods] are included as a safe fallback.
     *
     * @param profileId The internal profile identifier used to look up the checkpoint.
     * @param periods All seasons/weeks where the user has ski statistics, as returned by the
     *                statistics periods API. Only weeks at or after the floor week are polled.
     * @param apiClient The authenticated HTTP client used to fetch leaderboard data.
     */
    private suspend fun pollWeekLeaderboards(
        profileId: String,
        periods: StatisticsPeriodResponse,
        apiClient: HttpClient,
    ) {
        val checkpoint: String? = syncCheckpointService.checkpointForSkiStatsWeek(profileId)
        val fromWeekId: String = checkpoint ?: properties.friendsLeaderboard.syncFromWeekId

        if (checkpoint == null) {
            logger.info(
                "Performing full fetch of week leaderboards for profile={} from weekId={}",
                profileId,
                fromWeekId,
            )
        } else {
            logger.info(
                "Performing incremental fetch of week leaderboards for profile={} from weekId={}",
                profileId,
                fromWeekId,
            )
        }

        val allSeasonWeeks = periods.seasons.flatMap { season -> season.weeks.map { Pair(season, it) } }
        val fromWeek = allSeasonWeeks.map { it.second }.find { it.id == fromWeekId }

        val seasonWeeks = allSeasonWeeks
            .filter { (_, week) ->
                fromWeek == null ||
                    week.year > fromWeek.year ||
                    (week.year == fromWeek.year && week.weekNumber >= fromWeek.weekNumber)
            }
            .sortedWith(compareBy({ it.second.year }, { it.second.weekNumber }))

        for ((season, week) in seasonWeeks) {
            fetchFriendsLeaderboard(
                periodType = PeriodType.WEEK,
                value = week.id,
                profileId = profileId,
                apiClient = apiClient,
                seasonName = season.name,
                year = week.year,
                weekNumber = week.weekNumber,
            )
        }
    }

    /**
     * Polls the friends leaderboard for every season in [periods] on or after the floor season ID.
     *
     * **Initial poll** (no checkpoint exists): uses `syncFromSeasonId` from properties as the floor,
     * so all seasons in [periods] from that season ID onwards are fetched.
     *
     * **Incremental poll** (checkpoint exists): uses the last successfully persisted season ID from
     * [SyncCheckpointService.checkpointForSkiStatsSeason] as the floor. The checkpoint season itself is
     * also re-fetched, which handles late-arriving leaderboard updates for the most recently polled season.
     * Season IDs are compared as integers (e.g. `29`).
     *
     * @param profileId The internal profile identifier used to look up the checkpoint.
     * @param periods All seasons where the user has ski statistics, as returned by the statistics
     *                periods API. Only seasons at or after the floor season ID are polled.
     * @param apiClient The authenticated HTTP client used to fetch leaderboard data.
     */
    private suspend fun pollSeasonLeaderboards(
        profileId: String,
        periods: StatisticsPeriodResponse,
        apiClient: HttpClient,
    ) {
        val checkpoint: String? = syncCheckpointService.checkpointForSkiStatsSeason(profileId)
        val fromSeasonId: Int = (checkpoint ?: properties.friendsLeaderboard.syncFromSeasonId).toInt()

        if (checkpoint == null) {
            logger.info(
                "Performing full fetch of season leaderboards for profile={} from seasonId={}",
                profileId,
                fromSeasonId,
            )
        } else {
            logger.info(
                "Performing incremental fetch of season leaderboards for profile={} from seasonId={}",
                profileId,
                fromSeasonId,
            )
        }

        val seasons = periods.seasons
            .filter { it.id.toInt() >= fromSeasonId }
            .sortedBy { it.id.toInt() }

        for (season in seasons) {
            fetchFriendsLeaderboard(PeriodType.SEASON, season.id, profileId, apiClient, season.name, null, null)
        }
    }

    @Suppress("LongParameterList")
    private suspend fun fetchFriendsLeaderboard(
        periodType: PeriodType,
        value: String,
        profileId: String,
        apiClient: HttpClient,
        seasonName: String,
        year: Int?,
        weekNumber: Int?,
    ) {
        val response: FriendsLeaderboardResponse = fetchLeaderboard(periodType, value, apiClient)
        val persistenceResults = PersistenceResults()

        val period = SkiLeaderboardPeriod(
            type = periodType,
            value = value,
            startDate = response.periodData.startDate,
            weekId = response.periodData.weekId,
            seasonId = response.periodData.seasonId,
            seasonName = seasonName,
            year = year,
            weekNumber = weekNumber,
        )

        for (entry in response.entries) {
            val skiProfile = SkiProfile(
                id = entry.userId,
                name = entry.name,
                profileImageUrl = entry.profileImageUrl,
                isPrivate = entry.isPrivate,
            )
            skiStatsRepository.addOrUpdateProfile(skiProfile)

            val leaderboardEntry = SkiLeaderboardEntry(
                id = leaderboardEntryId(profileId, period, entry.userId),
                profileId = profileId,
                period = period,
                leaderboardUpdatedAtUtc = response.updatedAtUtc,
                entryUserId = entry.userId,
                position = entry.position,
                dropHeightInMeter = entry.dropHeightInMeter,
            )
            persistenceResults.add(skiStatsRepository.addOrUpdateLeaderboardEntry(leaderboardEntry))
        }

        updateCheckpoint(period, profileId)
        logger.info(
            "Fetching leaderboard for period=$periodType value=$value profile=$profileId complete. " +
                "Total entries in response: ${response.entries.size}, added: ${persistenceResults.addedCount}, " +
                "updated: ${persistenceResults.updatedCount}, no actions: ${persistenceResults.noActionCount}",
        )
    }

    private suspend fun updateCheckpoint(period: SkiLeaderboardPeriod, profileId: String) {
        when (period.type) {
            PeriodType.DAY -> syncCheckpointService.addOrUpdateCheckpointForSkiStatsDay(
                profileId,
                LocalDate.parse(period.value),
            )

            PeriodType.WEEK -> syncCheckpointService.addOrUpdateCheckpointForSkiStatsWeek(profileId, period.value)

            PeriodType.SEASON -> syncCheckpointService.addOrUpdateCheckpointForSkiStatsSeason(profileId, period.value)
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

    private suspend fun fetchStatisticsPeriods(skiProfileId: String, apiClient: HttpClient): StatisticsPeriodResponse {
        logger.info("Started fetching statistics periods for profile=$skiProfileId from external source.")

        val url = properties.core.statisticsPeriodsUrl(skiProfileId)
        val statisticsPeriods: StatisticsPeriodResponse = apiClient.get(url) {}.body()

        logger.info(
            "Fetching statistics periods from external source complete. " +
                "Total seasons in response: ${statisticsPeriods.seasons.size}",
        )

        return statisticsPeriods
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

fun leaderboardEntryId(profileId: String, period: SkiLeaderboardPeriod, entryUserId: String) =
    "${profileId}_${period.type.name}_${period.value}_$entryUserId"
