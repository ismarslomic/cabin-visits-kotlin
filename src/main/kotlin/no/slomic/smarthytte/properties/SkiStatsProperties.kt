package no.slomic.smarthytte.properties

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

data class SkiStatsPropertiesHolder(val skiStats: SkiStatsProperties)

data class SkiStatsProperties(
    val core: CoreSkiStatsProperties,
    val profiles: List<ProfileSkiStatsProperties>,
    val friendsLeaderboard: FriendsLeaderboardSkiStatsProperties,
)

data class FriendsLeaderboardSkiStatsProperties(
    val enabled: Boolean,
    val syncFrequencyMinutes: Int,
    val syncFromDate: String,
    val syncFromWeekId: String,
    val syncFromSeasonId: String,
)

data class CoreSkiStatsProperties(
    val baseUrl: String,
    val authPath: String,
    val friendsLeaderboardsPath: String,
    val statisticsPeriodsPath: String,
    val appInstanceId: String,
    val appPlatform: String,
    val apiKey: String,
    val appVersion: String,
    val cookie: String,
    val userAgent: String,
) {
    val authUrl: String get() = "${baseUrl}$authPath"

    /**
     * Builds the URL for fetching statistics periods for a specific user.
     *
     * The [statisticsPeriodsPath] must contain the placeholder `{skiProfileId}`, which will be replaced
     * at runtime with the actual SkiStats user ID (UUID) received from the auth or leaderboard API responses.
     *
     * Example value for `statisticsPeriodsPath`: `/users/{skiProfileId}/statistics/periods`
     * Example result: `https://api.example.com/users/ABCD1234/statistics/periods`
     *
     * @param skiProfileId The SkiStats user ID (UUID) to substitute for `{skiProfileId}` in the path.
     * @return The complete URL as a string for fetching the user's statistics periods.
     */
    fun statisticsPeriodsUrl(skiProfileId: String): String =
        "$baseUrl${statisticsPeriodsPath.replace("{skiProfileId}", skiProfileId)}"

    /**
     * Builds and returns the URL for accessing friends' leaderboards based on the specified period type and value.
     *
     * @param periodType The type of period for the leaderboard, such as daily, weekly, or monthly.
     *                    This parameter determines the time span of the leaderboard data.
     * @param value A specific value associated with the periodType, such as a date or identifier.
     *              This parameter provides additional context for the leaderboard request.
     * @return The complete URL as a string for accessing the friends' leaderboard.
     */
    fun friendsLeaderboardsUrl(periodType: String, value: String): String = URLBuilder(baseUrl)
        .appendPathSegments(friendsLeaderboardsPath, periodType.lowercase(), value)
        .buildString()
}

data class ProfileSkiStatsProperties(
    val id: String,
    val externalProfileId: String,
    val username: String,
    val password: String,
    val agentId: String,
    val clientId: String,
    val clientSecret: String,
)
