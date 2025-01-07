package no.slomic.smarthytte

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import no.slomic.smarthytte.calendar.CalendarEventRepository
import no.slomic.smarthytte.calendar.SqliteCalendarEventRepository
import no.slomic.smarthytte.calendar.createGoogleCalendarService
import no.slomic.smarthytte.calendar.startPeriodicGoogleCalendarSync
import no.slomic.smarthytte.guest.GuestRepository
import no.slomic.smarthytte.guest.SqliteGuestRepository
import no.slomic.smarthytte.guest.insertGuestsFromFile
import no.slomic.smarthytte.plugins.configureDatabases
import no.slomic.smarthytte.plugins.configureMonitoring
import no.slomic.smarthytte.plugins.configureRouting
import no.slomic.smarthytte.properties.KtorPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

fun main() {
    val ktorProperties = loadProperties<KtorPropertiesHolder>().ktor

    with(ktorProperties.deployment) {
        embeddedServer(
            CIO,
            port = port,
            host = host,
            module = Application::module,
        ).start(wait = true)
    }
}

fun Application.module() {
    val calendarRepository: CalendarEventRepository = SqliteCalendarEventRepository()
    val guestRepository: GuestRepository = SqliteGuestRepository()
    val googleCalendarService = createGoogleCalendarService(calendarRepository = calendarRepository)

    configureMonitoring()
    configureRouting()
    configureDatabases()
    insertGuestsFromFile(guestRepository)
    startPeriodicGoogleCalendarSync(googleCalendarService = googleCalendarService)
}
