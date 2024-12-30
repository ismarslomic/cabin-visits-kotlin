package no.slomic.smarthytte

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import no.slomic.smarthytte.plugins.configureMonitoring
import no.slomic.smarthytte.plugins.configureRouting
import no.slomic.smarthytte.properties.KtorPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import no.slomic.smarthytte.services.createGoogleCalendarService
import no.slomic.smarthytte.tasks.startPeriodicGoogleCalendarSync

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
    val googleCalendarService = createGoogleCalendarService()
    configureMonitoring()
    configureRouting()
    startPeriodicGoogleCalendarSync(googleCalendarService)
}
