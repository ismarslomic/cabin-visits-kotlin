package no.slomic.smarthytte.properties

data class SkiStatsPropertiesHolder(val skiStats: SkiStatsProperties)

data class SkiStatsProperties(
    val baseUrl: String,
    val authPath: String,
    val seasonStatsPath: String,
    val appInstanceId: String,
    val appPlatform: String,
    val apiKey: String,
    val appVersion: String,
    val cookie: String,
    val userAgent: String,
    val profileIsmar: ProfileSkiStatsProperties,
)

data class ProfileSkiStatsProperties(
    val username: String,
    val password: String,
    val agentId: String,
    val clientSecret: String,
)
