package no.slomic.smarthytte.vehicletrip

import no.slomic.smarthytte.common.UpsertStatus

interface VehicleTripRepository {
    suspend fun allVehicleTrips(): List<VehicleTrip>
    suspend fun addOrUpdate(vehicleTrip: VehicleTrip): UpsertStatus
    suspend fun setNotionId(notionId: String, vehicleTripId: String): UpsertStatus
}
