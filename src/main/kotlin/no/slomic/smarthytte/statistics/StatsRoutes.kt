package no.slomic.smarthytte.statistics

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val MONTHS_PER_YEAR = 12

fun Application.configureStatsRoutes(statsService: StatsService) {
    routing {
        // GET /api/stats
        // Returns live occupancy status: whether the cabin is currently occupied, details about the
        // ongoing and next reservation (dates, guest names, remaining/upcoming nights), and all-time
        // totals (visits, nights, unique guests). Suitable for a smart mirror or real-time dashboard.
        get("/api/stats") {
            call.respond(statsService.getLiveStats())
        }

        // GET /api/stats/current-year
        // Returns a quick summary for the current calendar year: number of visits, total nights,
        // and total driving distance to/from the cabin. Suitable for a summary card or widget.
        get("/api/stats/current-year") {
            call.respond(statsService.getCurrentYearStats())
        }

        // GET /api/stats/years
        // Returns a sorted list of years for which reservations exist.
        // Use this to populate a year picker in a dashboard.
        get("/api/stats/years") {
            call.respond(statsService.getAvailableYears())
        }

        // GET /api/stats/years/{year}
        // Returns full statistics for a given year, including visit counts, nights, occupancy
        // percentages (days/weeks/months), guest overview (new guests, top guest, all guests),
        // EV statistics (distance, energy consumption, regeneration), driving time stats
        // (avg/min/max to and from cabin), typical departure and arrival times, and a monthly
        // breakdown for all 12 months.
        // Path parameter: year - the calendar year (e.g. 2024)
        get("/api/stats/years/{year}") {
            val year = call.parameters["year"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid year")
            call.respond(statsService.getYearStats(year))
        }

        // GET /api/stats/years/{year}/months/{month}
        // Returns statistics for a specific month: visit count with comparisons against the previous
        // 30 days, the same month last year, and the year-to-date monthly average; nights
        // min/avg/max; occupancy percentages; guest list; and driving time and moment stats for
        // the month including diff vs. the previous month.
        // Path parameters:
        //   year  - the calendar year (e.g. 2024)
        //   month - month number 1–12
        get("/api/stats/years/{year}/months/{month}") {
            val year = call.parameters["year"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid year")
            val month = call.parameters["month"]?.toIntOrNull()?.takeIf { it in 1..MONTHS_PER_YEAR }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid month, must be 1-12")
            call.respond(statsService.getMonthStats(year, month))
        }

        // GET /api/stats/guests
        // Returns guest statistics across all years: top 10 guests by number of visits and by total
        // nights, gender distribution, and age group breakdown (0–12, 13–17, 18–30, 31–50, 51+).
        // Suitable for a hall-of-fame view or demographic overview.
        get("/api/stats/guests") {
            call.respond(statsService.getGuestStats())
        }
    }
}
