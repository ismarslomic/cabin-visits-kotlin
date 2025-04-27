package no.slomic.smarthytte.sensors.checkinouts

import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.UpsertStatus

interface CheckInOutSensorRepository {
    suspend fun allCheckInOuts(): List<CheckInOutSensor>
    suspend fun addOrUpdate(checkInOutSensor: CheckInOutSensor): UpsertStatus
    suspend fun latestTime(): Instant?
    suspend fun addOrUpdate(latestTime: Instant)
}
