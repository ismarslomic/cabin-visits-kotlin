package no.slomic.smarthytte.vehicletrips

data class GeoPoint(val latitude: Double, val longitude: Double)

/** City and address of the start and end of a trip, null when unknown. */
data class TripPlaces(val startCity: String?, val startAddress: String?, val endCity: String?, val endAddress: String?)
