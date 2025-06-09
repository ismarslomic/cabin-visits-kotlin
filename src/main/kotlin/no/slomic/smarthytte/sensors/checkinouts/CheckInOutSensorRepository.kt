package no.slomic.smarthytte.sensors.checkinouts

import no.slomic.smarthytte.common.PersistenceResult

interface CheckInOutSensorRepository {
    suspend fun allCheckInOuts(): List<CheckInOutSensor>
    suspend fun addOrUpdate(checkInOutSensor: CheckInOutSensor): PersistenceResult
}
