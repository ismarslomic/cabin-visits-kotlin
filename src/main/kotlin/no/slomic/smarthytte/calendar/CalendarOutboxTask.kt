package no.slomic.smarthytte.calendar

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.slomic.smarthytte.common.runCatchingLogged
import no.slomic.smarthytte.properties.CalendarOutboxPropertiesHolder
import no.slomic.smarthytte.properties.GoogleCalendarPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.reservations.ReservationSystemService
import kotlin.time.Duration.Companion.seconds

/**
 * Starts the task that pushes the calendar outbox to Google Calendar. It runs around the clock and never stops the
 * app: the Google client is created inside the first push, so a missing or invalid credentials file only makes the
 * pushes fail and retry while reservation writes go on and the outbox fills up.
 */
fun Application.startCalendarOutboxDrain(reservationService: ReservationSystemService, registry: MeterRegistry) {
    val properties = loadProperties<CalendarOutboxPropertiesHolder>().calendarOutbox
    val gateway = GoogleCalendarGateway(
        clientProvider = { createCalendarClient(loadProperties<GoogleCalendarPropertiesHolder>().googleCalendar) },
        title = properties.eventTitle,
    )
    val metrics = CalendarOutboxMetrics().also { it.bind(registry) }
    val drain = CalendarOutboxDrain(
        CalendarOutbox(),
        CalendarEventPusher(gateway, reservationService),
        properties,
        metrics,
    )
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    monitor.subscribe(ApplicationStopped) { scope.cancel() }
    scope.launch {
        while (isActive) {
            runCatchingLogged("Calendar outbox drain") { drain.drainOnce() }
            delay(properties.drainIntervalSeconds.seconds)
        }
    }
}
