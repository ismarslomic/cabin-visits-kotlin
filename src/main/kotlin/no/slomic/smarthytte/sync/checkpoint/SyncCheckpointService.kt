package no.slomic.smarthytte.sync.checkpoint

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class SyncCheckpointService(val repository: SyncCheckpointRepository) {
    companion object {
        private const val CHECK_IN_OUT_SENSOR_ID = "check_in_out_sensor"
        private const val GOOGLE_CALENDAR_EVENTS_ID = "google_calendar_events"
        private const val VEHICLE_TRIPS_ID = "vehicle_trips"
    }

    // Read
    suspend fun checkpointForCheckInOutSensor(): Instant? {
        val checkpoint: String? = repository.checkpointById(id = CHECK_IN_OUT_SENSOR_ID)
        return checkpoint?.let { Instant.parse(it) }
    }

    suspend fun checkpointForGoogleCalendarEvents(): String? = repository.checkpointById(id = GOOGLE_CALENDAR_EVENTS_ID)

    suspend fun checkpointForVehicleTrips(): LocalDate? {
        val checkpoint: String? = repository.checkpointById(id = VEHICLE_TRIPS_ID)
        return checkpoint?.let { LocalDate.parse(it) }
    }

    // Add or update
    suspend fun addOrUpdateCheckpointForCheckInOutSensor(value: Instant) {
        repository.addOrUpdateCheckpoint(id = CHECK_IN_OUT_SENSOR_ID, value = value.toString())
    }

    suspend fun addOrUpdateCheckpointForGoogleCalendarEvents(value: String) {
        repository.addOrUpdateCheckpoint(id = GOOGLE_CALENDAR_EVENTS_ID, value = value)
    }

    suspend fun addOrUpdateCheckpointForVehicleTrips(value: LocalDate) {
        repository.addOrUpdateCheckpoint(id = VEHICLE_TRIPS_ID, value = value.toString())
    }

    // Delete
    suspend fun deleteCheckpointForGoogleCalendarEvents() = repository.deleteCheckpoint(id = GOOGLE_CALENDAR_EVENTS_ID)
}
