package no.slomic.smarthytte.vehicletrips

const val HOME_CITY_NAME = "Oslo"
const val CABIN_CITY_NAME = "Ullsåk"

/**
 * Represents the vehicle trips to the cabin and back.
 */
data class CabinVehicleTrip(
    /**
     * The vehicle trip from Home to Cabin, including extra stops.
     */
    val toCabin: VehicleTrip,
    /**
     * The vehicle trip from Cabin to Home, including extra stops.
     * Null if the cabin visit is active and not yet completed.
     */
    val fromCabin: VehicleTrip?,
    /**
     * The index of the [fromCabin] trip if not null, otherwise the [toCabin] trip in the list of trips.
     */
    val indexInTempList: Int,
) {
    /**
     * Checks if this vehicle trip to cabin is still active or is completed.
     */
    val isActive: Boolean get() = fromCabin == null
}
