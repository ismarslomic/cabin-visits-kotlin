package no.slomic.smarthytte.vehicletrips

import no.slomic.smarthytte.common.PersistenceResult

interface VehicleTripRepository {
    suspend fun allVehicleTrips(): List<VehicleTrip>
    suspend fun addOrUpdate(vehicleTrip: VehicleTrip): PersistenceResult
    suspend fun setNotionId(notionId: String, vehicleTripId: String): PersistenceResult
}
