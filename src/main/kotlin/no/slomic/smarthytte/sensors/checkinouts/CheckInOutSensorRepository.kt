package no.slomic.smarthytte.sensors.checkinouts

import kotlinx.datetime.Instant

interface CheckInOutSensorRepository {
    suspend fun allCheckInOuts(): List<CheckInOutSensor>
    suspend fun addOrUpdate(checkInOutSensor: CheckInOutSensor): CheckInOutSensor
    suspend fun latestTime(): Instant?
    suspend fun addOrUpdate(latestTime: Instant)
}
