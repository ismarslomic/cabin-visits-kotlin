package no.slomic.smarthytte.sensors.checkinouts

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.slomic.smarthytte.properties.CheckInProperties
import no.slomic.smarthytte.properties.InfluxDbPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Application.launchSyncCheckInOutSensorTask(checkInOutSensorService: CheckInOutSensorService) {
    val checkInProperties: CheckInProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb.checkIn
    val syncFrequencyMinutes: Duration = checkInProperties.syncFrequencyMinutes.minutes

    // Launch the task in a background coroutine
    log.info("Launching syncCheckInOutSensor task every $syncFrequencyMinutes")
    val taskJob = launch {
        while (isActive) {
            fetchCheckInOut(checkInOutSensorService)
            delay(syncFrequencyMinutes)
        }
    }

    // Ensure the client is closed when the application stops
    monitor.subscribe(ApplicationStopping) {
        log.info("Canceling the syncCheckInOutSensor task since Application is stopping.")
        taskJob.cancel() // Stop the task
    }
}

suspend fun Application.fetchCheckInOut(checkInOutSensorService: CheckInOutSensorService) {
    // Launch custom code in a coroutine
    log.info("Starting syncing the Check In/Out sensor from InfluxDb.")
    checkInOutSensorService.synchronizeCheckInOut()
    log.info("Completed syncing the Check In/Out sensor from InfluxDb")
}
