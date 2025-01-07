package no.slomic.smarthytte.calendar

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

fun Application.startPeriodicGoogleCalendarSync(googleCalendarService: GoogleCalendarService) {
    // Launch the task in a background coroutine
    val taskJob = launch {
        while (isActive) {
            performPeriodicGoogleCalendarRequest(googleCalendarService)
            delay(10.seconds)
        }
    }

    // Ensure the client is closed when the application stops
    monitor.subscribe(ApplicationStopping) {
        log.info("Canceling the startPeriodicGoogleCalendarSync task since Application is stopping.")
        taskJob.cancel() // Stop the task
    }
}

private fun Application.performPeriodicGoogleCalendarRequest(googleCalendarService: GoogleCalendarService) {
    // Launch custom code in a coroutine
    launch {
        log.info("Subscribing to Google Calendar events...")
        googleCalendarService.synchronizeCalendarEvents()
        log.info("Successfully subscribed")
    }
}
