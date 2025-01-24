package no.slomic.smarthytte.cabinvisit

import kotlinx.datetime.Instant
import no.slomic.smarthytte.reservations.Reservation
import kotlin.time.Duration

data class CabinVisit(
    val reservation: Reservation,
    val checkIn: VisitEvent?,
    val checkOut: VisitEvent?,
    val duration: Duration?,
)

data class VisitEvent(val timestamp: Instant, val sourceName: EventSource, val sourceId: String)

enum class EventSource {
    CHECK_IN_SENSOR,
    VEHICLE_TRIP,
    CALENDAR_EVENT,
}
