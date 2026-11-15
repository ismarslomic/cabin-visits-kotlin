package no.slomic.smarthytte.properties

data class VehicleTripPropertiesHolder(val vehicleTrip: VehicleTripProperties)

data class VehicleTripProperties(val filePath: String, val syncEnabled: Boolean, val syncFromDate: String)
