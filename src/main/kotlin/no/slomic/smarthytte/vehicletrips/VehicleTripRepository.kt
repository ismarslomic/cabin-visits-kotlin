package no.slomic.smarthytte.vehicletrips

import no.slomic.smarthytte.common.PersistenceResult

interface VehicleTripRepository {
    /**
     * Retrieves all vehicle trips stored in the repository.
     *
     * @return A list of vehicle trips, sorted by the start time (ASC), each containing details such as start and
     * end locations, distances, duration, energy consumption, and other trip-specific data.
     */
    suspend fun allVehicleTrips(): List<VehicleTrip>

    /**
     * Adds a new vehicle trip to the repository or updates an existing one
     * based on its unique identifier.
     *
     * @param vehicleTrip The vehicle trip data to add or update in the repository.
     * @param actor Goes in `created_by` for a new trip, or `updated_by` for a changed one. No default: the import
     * passes [no.slomic.smarthytte.common.SYSTEM_ACTOR] explicitly.
     * @return A [PersistenceResult] indicating the result of the operation.
     * Possible values are [PersistenceResult.ADDED], [PersistenceResult.UPDATED],
     * [PersistenceResult.NO_ACTION], or [PersistenceResult.DELETED].
     */
    suspend fun addOrUpdate(vehicleTrip: VehicleTrip, actor: String): PersistenceResult
}
