package no.slomic.smarthytte.vehicletrip

interface VehicleTripRepository {
    suspend fun addOrUpdate(vehicleTrip: VehicleTrip)
}
