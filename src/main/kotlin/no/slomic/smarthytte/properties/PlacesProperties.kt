package no.slomic.smarthytte.properties

data class PlacesPropertiesHolder(val places: PlacesProperties)

/** The two places that need no lookup: the home and the cabin. */
data class PlacesProperties(val home: PlaceProperties, val cabin: PlaceProperties)

/** [radiusMeters]: a point this close to ([latitude], [longitude]) is the place, whatever GPS drift says. */
data class PlaceProperties(val latitude: Double, val longitude: Double, val address: String, val radiusMeters: Double)
