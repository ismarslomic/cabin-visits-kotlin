package no.slomic.smarthytte.vehicletrips

/** Stored by name in `vehicle_trip.data_quality`, so a value cannot be renamed without a data migration. */
enum class DataQuality {
    COMPLETE,

    /** The place lookup failed: city or address is null. Retried at the start of the next trip fetch. */
    PLACE_MISSING,
}

/** A trip is complete when it has a start and an end city. An address is for display only, so it may be missing. */
fun TripPlaces.dataQuality(): DataQuality =
    if (startCity != null && endCity != null) DataQuality.COMPLETE else DataQuality.PLACE_MISSING
