package no.slomic.smarthytte.properties

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

data class SkiStatsPropertiesHolder(val skiStats: SkiStatsProperties)

data class SkiStatsProperties(val core: CoreSkiStatsProperties, val profiles: List<ProfileSkiStatsProperties>)

data class CoreSkiStatsProperties(
    val baseUrl: String,
    val authPath: String,
    val friendsLeaderboardsPath: String,
    val appInstanceId: String,
    val appPlatform: String,
    val apiKey: String,
    val appVersion: String,
    val cookie: String,
    val userAgent: String,
) {
    val authUrl: String get() = "${baseUrl}$authPath"

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
    val username: String,
    val password: String,
    val agentId: String,
    val clientId: String,
    val clientSecret: String,
)
