package no.slomic.smarthytte.sensors.checkinouts

import no.slomic.smarthytte.common.PersistenceResult

interface CheckInOutSensorRepository {
    suspend fun allCheckInOuts(): List<CheckInOutSensor>

    /** Adds or updates the sensor reading. The [actor] goes in `created_by` (or `updated_by` for a change). */
    suspend fun addOrUpdate(checkInOutSensor: CheckInOutSensor, actor: String): PersistenceResult
}
