package no.slomic.smarthytte.vehicletrips

import no.slomic.smarthytte.common.PersistenceResult
import kotlin.time.Instant

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
     * On update only the archive columns are written: `data_quality`, `jlr_journey_id` and the coordinates are left
     * alone, because the archive file is upserted at every startup and knows nothing about them.
     * @return A [PersistenceResult] indicating the result of the operation.
     * Possible values are [PersistenceResult.ADDED], [PersistenceResult.UPDATED],
     * [PersistenceResult.NO_ACTION], or [PersistenceResult.DELETED].
     */
    suspend fun addOrUpdate(vehicleTrip: VehicleTrip, actor: String): PersistenceResult

    /** Trips whose `end_time` is in [from]..[to], both inclusive, sorted by end time. */
    suspend fun findByEndTimeBetween(from: Instant, to: Instant): List<VehicleTrip>

    suspend fun findByDataQuality(quality: DataQuality): List<VehicleTrip>

    /**
     * Links a stored trip to its JLR journey and stores the coordinates. Changes nothing else, no place lookup.
     *
     * @return [PersistenceResult.UPDATED], or [PersistenceResult.NO_ACTION] if the id is unknown or nothing changed
     */
    suspend fun setJlrData(
        id: String,
        jlrJourneyId: String,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        actor: String,
    ): PersistenceResult

    /**
     * Stores the resolved [places] and the new [quality] of a trip, used when a failed place lookup is retried.
     *
     * @return [PersistenceResult.UPDATED], or [PersistenceResult.NO_ACTION] if the id is unknown or nothing changed
     */
    suspend fun updatePlaces(id: String, places: TripPlaces, quality: DataQuality, actor: String): PersistenceResult
}
