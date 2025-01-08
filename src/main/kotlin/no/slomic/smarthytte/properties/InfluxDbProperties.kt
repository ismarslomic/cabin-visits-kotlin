package no.slomic.smarthytte.properties

data class InfluxDbPropertiesHolder(
    val influxDb: InfluxDbProperties,
)

data class InfluxDbProperties(
    val url: String,
    val token: String,
    val org: String,
    val bucket: String,
    val checkIn: CheckInProperties,
)

data class CheckInProperties(
    val measurement: String,
    val rangeStart: String,
    val rangeStop: String? = null,
)
