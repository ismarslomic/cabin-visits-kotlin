package no.slomic.smarthytte.tasks

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.slomic.smarthytte.services.GoogleCalendarService
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

fun Application.performPeriodicGoogleCalendarRequest(googleCalendarService: GoogleCalendarService) {
    // Launch custom code in a coroutine
    launch {
        log.info("Subscribing to Google Calendar events...")
        googleCalendarService.synchronizeCalendarEvents(fromDate = "2024-12-29T00:00:00.000Z")
        log.info("Successfully subscribed")
    }
}
