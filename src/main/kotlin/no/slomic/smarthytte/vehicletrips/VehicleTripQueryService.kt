package no.slomic.smarthytte.vehicletrips

/** Reads of vehicle trips for other packages, which call this instead of the [VehicleTripRepository]. */
class VehicleTripQueryService(private val vehicleTripRepository: VehicleTripRepository) {
    /** All trips, sorted by start time. */
    suspend fun allVehicleTrips(): List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
}
