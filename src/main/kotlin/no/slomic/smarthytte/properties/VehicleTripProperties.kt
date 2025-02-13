package no.slomic.smarthytte.properties

data class VehicleTripPropertiesHolder(val vehicleTrip: VehicleTripProperties)

data class VehicleTripProperties(
    val filePath: String,
    val loginUrl: String,
    val tripsUrl: String,
    val username: String,
    val password: String,
    val syncFromDate: String,
    val userAgent: String,
    val referrer: String,
    val locale: String,
)
