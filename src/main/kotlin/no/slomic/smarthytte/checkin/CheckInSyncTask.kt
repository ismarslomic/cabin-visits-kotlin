package no.slomic.smarthytte.checkin

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

fun Application.startPeriodicCheckInSync(checkInService: CheckInService) {
    val checkInProperties: CheckInProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb.checkIn
    val syncFrequencyMinutes: Duration = checkInProperties.syncFrequencyMinutes.minutes

    // Launch the task in a background coroutine
    log.info("Launching startPeriodicCheckInSync task every $syncFrequencyMinutes")
    val taskJob = launch {
        while (isActive) {
            performPeriodicCheckInRequest(checkInService)
            delay(syncFrequencyMinutes)
        }
    }

    // Ensure the client is closed when the application stops
    monitor.subscribe(ApplicationStopping) {
        log.info("Canceling the startPeriodicCheckInSync task since Application is stopping.")
        taskJob.cancel() // Stop the task
    }
}

private fun Application.performPeriodicCheckInRequest(checkInService: CheckInService) {
    // Launch custom code in a coroutine
    launch {
        log.info("Starting syncing the Check Ins from InfluxDb.")
        checkInService.synchronizeCheckIns()
        log.info("Completed syncing the Check Ins from InfluxDb")
    }
}
