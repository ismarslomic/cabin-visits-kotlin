package no.slomic.smarthytte.sensors.checkinouts

/** Reads of the check-in/out sensor readings for other packages, which call this instead of the repository. */
class CheckInOutSensorQueryService(private val checkInOutSensorRepository: CheckInOutSensorRepository) {
    suspend fun allCheckInOuts(): List<CheckInOutSensor> = checkInOutSensorRepository.allCheckInOuts()
}
