@file:OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)
@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.routing

private const val MONTHS_PER_YEAR = 12

fun Application.configureOpenApi() {
    val baseDoc = OpenApiDoc.Builder().apply {
        info = OpenApiInfo(
            title = "Cabin Visits API",
            version = "1.0.0",
            description = "REST API for cabin visit statistics: occupancy, driving, guests and EV metrics.",
        )
        tag("Stats", "Cabin visit statistics: live status, yearly and monthly summaries, and guest rankings.")
        tag("Guests", "Guest resources, such as avatar images.")
    }.build()

    routing {
        get("/openapi.json") {
            val spec = OpenApiDocSource.Routing().read(this@configureOpenApi, baseDoc)
            call.respondText(spec.content, ContentType.Application.Json)
        }.hide()
    }
}

fun Application.configureStatsRoutes(statsService: StatsService) {
    routing {
        get("/api/stats") { call.respond(statsService.getLiveStats()) }
            .describeLiveStats()

        get("/api/stats/current-year") { call.respond(statsService.getCurrentYearStats()) }
            .describeCurrentYearStats()

        get("/api/stats/years") { call.respond(statsService.getAvailableYears()) }
            .describeAvailableYears()

        get("/api/stats/years/{year}") {
            val year = call.parameters["year"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid year")
            call.respond(statsService.getYearStats(year))
        }.describeYearStats()

        get("/api/stats/years/{year}/months/{month}") {
            val year = call.parameters["year"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid year")
            val month = call.parameters["month"]?.toIntOrNull()?.takeIf { it in 1..MONTHS_PER_YEAR }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid month, must be 1-12")
            call.respond(statsService.getMonthStats(year, month))
        }.describeMonthStats()

        get("/api/stats/guests") { call.respond(statsService.getGuestStats()) }
            .describeGuestStats()
    }
}

private fun Route.describeLiveStats() = describe {
    operationId = "getLiveStats"
    tag("Stats")
    summary = "Live occupancy status"
    description = "Returns whether the cabin is currently occupied, details about the ongoing and next " +
        "reservation (dates, guest statistics, remaining/upcoming nights), and all-time totals (visits, nights, " +
        "unique guests) excluding future bookings. Suitable for a smart mirror or real-time dashboard."
    responses {
        HttpStatusCode.OK { description = "Live occupancy status." }
        default { description = "Unexpected server error." }
    }
}

private fun Route.describeCurrentYearStats() = describe {
    operationId = "getCurrentYearStats"
    tag("Stats")
    summary = "Current year summary"
    description = "Returns a quick summary for the current calendar year: number of visits, total nights, " +
        "and total driving distance to/from the cabin. Suitable for a summary card or widget."
    responses {
        HttpStatusCode.OK { description = "Current year summary." }
        default { description = "Unexpected server error." }
    }
}

private fun Route.describeAvailableYears() = describe {
    operationId = "getAvailableYears"
    tag("Stats")
    summary = "Available years"
    description = "Returns a sorted list of years for which reservations exist. " +
        "Use this to populate a year picker in a dashboard."
    responses {
        HttpStatusCode.OK { description = "Sorted list of available years." }
        default { description = "Unexpected server error." }
    }
}

private fun Route.describeYearStats() = describe {
    operationId = "getYearStats"
    tag("Stats")
    summary = "Full year statistics"
    parameters {
        path("year") {
            description = "The calendar year, for example 2025."
            schema = JsonSchema(type = JsonType.INTEGER)
        }
    }
    description = "Returns full statistics for a given year, including visit counts, nights, occupancy " +
        "percentages (days/weeks/months), guest overview, EV statistics (distance, energy consumption, " +
        "regeneration), driving time stats (avg/min/max to and from cabin), typical departure and arrival " +
        "times, and a monthly breakdown for all 12 months.\n\n" +
        "A guest is counted as **new** in a given year if that year is the first year in which they " +
        "appear in any reservation."
    responses {
        HttpStatusCode.OK { description = "Full statistics for the requested year." }
        HttpStatusCode.BadRequest { description = "Invalid year parameter." }
    }
}

private fun Route.describeMonthStats() = describe {
    operationId = "getMonthStats"
    tag("Stats")
    summary = "Monthly statistics"
    parameters {
        path("year") {
            description = "The calendar year, for example 2025."
            schema = JsonSchema(type = JsonType.INTEGER)
        }
        path("month") {
            description = "The month number, 1 (January) to 12 (December)."
            schema = JsonSchema(type = JsonType.INTEGER, minimum = 1.0, maximum = MONTHS_PER_YEAR.toDouble())
        }
    }
    description = "Returns statistics for a specific month: visit count with comparisons against the " +
        "previous 30 days, the same month last year, and the year-to-date monthly average; nights " +
        "min/avg/max; occupancy percentages; guest list; and driving time and moment stats including " +
        "diff vs. the previous month."
    responses {
        HttpStatusCode.OK { description = "Statistics for the requested month." }
        HttpStatusCode.BadRequest { description = "Invalid year or month parameter (month must be 1–12)." }
    }
}

private fun Route.describeGuestStats() = describe {
    operationId = "getGuestStats"
    tag("Stats")
    summary = "Guest statistics"
    description = "Returns guest statistics across all years: top 10 guests by number of visits and by " +
        "total nights, gender distribution, and age group breakdown (0–12, 13–17, 18–30, 31–50, 51+). " +
        "Suitable for a hall-of-fame view or demographic overview."
    responses {
        HttpStatusCode.OK { description = "Guest statistics across all years." }
        default { description = "Unexpected server error." }
    }
}
