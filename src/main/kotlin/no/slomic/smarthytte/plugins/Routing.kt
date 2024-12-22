package no.slomic.smarthytte.plugins

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    // Starting point for a Ktor app:
    routing {
        get("/") {
            call.respondText("Hello GraalVM!")
            call.application.environment.log
                .info("Call made to /")
        }
    }
}
