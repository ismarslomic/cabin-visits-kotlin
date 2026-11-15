package no.slomic.smarthytte.vehicletrips

/** Stored by name in `vehicle_trip.data_quality`, so a value cannot be renamed without a data migration. */
enum class DataQuality {
    COMPLETE,

    /** The place lookup failed: city or address is null. Retried at the start of the next trip fetch. */
    PLACE_MISSING,
}
