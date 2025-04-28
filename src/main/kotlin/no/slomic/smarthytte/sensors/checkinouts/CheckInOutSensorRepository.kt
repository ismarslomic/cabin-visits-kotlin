package no.slomic.smarthytte.sensors.checkinouts

import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.PersistenceResult

interface CheckInOutSensorRepository {
    suspend fun allCheckInOuts(): List<CheckInOutSensor>
    suspend fun addOrUpdate(checkInOutSensor: CheckInOutSensor): PersistenceResult
    suspend fun latestTime(): Instant?
    suspend fun addOrUpdate(latestTime: Instant)
}
