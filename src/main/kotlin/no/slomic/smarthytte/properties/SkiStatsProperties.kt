package no.slomic.smarthytte.properties

data class SkiStatsPropertiesHolder(val skiStats: SkiStatsProperties)

data class SkiStatsProperties(val core: CoreSkiStatsProperties, val profiles: List<ProfileSkiStatsProperties>)

data class CoreSkiStatsProperties(
    val baseUrl: String,
    val authPath: String,
    val seasonStatsPath: String,
    val appInstanceId: String,
    val appPlatform: String,
    val apiKey: String,
    val appVersion: String,
    val cookie: String,
    val userAgent: String,
) {
    val authUrl: String get() = "${baseUrl}$authPath"
    val seasonStatsUrl: String get() = "${baseUrl}$seasonStatsPath"
}

data class ProfileSkiStatsProperties(
    val id: String,
    val username: String,
    val password: String,
    val agentId: String,
    val clientId: String,
    val clientSecret: String,
)
