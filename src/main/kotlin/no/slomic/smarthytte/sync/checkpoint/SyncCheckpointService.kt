package no.slomic.smarthytte.sync.checkpoint

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.skistats.PeriodType
import kotlin.time.Instant

// Thin delegation wrappers, one per checkpoint type — splitting adds no value
@Suppress("TooManyFunctions")
class SyncCheckpointService(val repository: SyncCheckpointRepository) {
    companion object {
        private const val CHECK_IN_OUT_SENSOR_ID = "check_in_out_sensor"
        private const val GOOGLE_CALENDAR_EVENTS_ID = "google_calendar_events"
        private const val VEHICLE_TRIPS_ID = "vehicle_trips"
    }

    private fun skiStatsCheckpointId(periodType: PeriodType, profileId: String) =
        "ski_stats_${periodType.name.lowercase()}_$profileId"

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

    suspend fun deleteCheckpointForGoogleCalendarEvents() = repository.deleteCheckpoint(id = GOOGLE_CALENDAR_EVENTS_ID)

    suspend fun checkpointForSkiStatsDay(profileId: String): LocalDate? {
        val checkpoint: String? = repository.checkpointById(skiStatsCheckpointId(PeriodType.DAY, profileId))
        return checkpoint?.let { LocalDate.parse(it) }
    }

    suspend fun checkpointForSkiStatsWeek(profileId: String): String? =
        repository.checkpointById(skiStatsCheckpointId(PeriodType.WEEK, profileId))

    suspend fun checkpointForSkiStatsSeason(profileId: String): String? =
        repository.checkpointById(skiStatsCheckpointId(PeriodType.SEASON, profileId))

    suspend fun addOrUpdateCheckpointForSkiStatsDay(profileId: String, value: LocalDate) {
        repository.addOrUpdateCheckpoint(skiStatsCheckpointId(PeriodType.DAY, profileId), value.toString())
    }

    suspend fun addOrUpdateCheckpointForSkiStatsWeek(profileId: String, value: String) {
        repository.addOrUpdateCheckpoint(skiStatsCheckpointId(PeriodType.WEEK, profileId), value)
    }

    suspend fun addOrUpdateCheckpointForSkiStatsSeason(profileId: String, value: String) {
        repository.addOrUpdateCheckpoint(skiStatsCheckpointId(PeriodType.SEASON, profileId), value)
    }
}
