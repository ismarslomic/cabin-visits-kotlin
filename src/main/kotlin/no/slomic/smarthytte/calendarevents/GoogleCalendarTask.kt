package no.slomic.smarthytte.calendarevents

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.slomic.smarthytte.properties.GoogleCalendarProperties
import no.slomic.smarthytte.properties.GoogleCalendarPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Application.launchSyncGoogleCalendarTask(googleCalendarService: GoogleCalendarService) {
    val googleProperties: GoogleCalendarProperties = loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar
    val syncFrequencyMinutes: Duration = googleProperties.syncFrequencyMinutes.minutes

    // Launch the task in a background coroutine
    log.info("Launching startPeriodicGoogleCalendarSync task every $syncFrequencyMinutes")
    val taskJob = launch {
        while (isActive) {
            performSync(googleCalendarService)
            delay(syncFrequencyMinutes)
        }
    }

    // Ensure the client is closed when the application stops
    monitor.subscribe(ApplicationStopping) {
        log.info("Canceling the startPeriodicGoogleCalendarSync task since Application is stopping.")
        taskJob.cancel() // Stop the task
    }
}

private fun Application.performSync(googleCalendarService: GoogleCalendarService) {
    // Launch custom code in a coroutine
    launch {
        log.info("Starting syncing the Google Calendar events")
        googleCalendarService.synchronizeCalendarEvents()
        log.info("Completed syncing the Google Calendar events")
    }
}
