package no.slomic.smarthytte.schema.models

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import kotlinx.datetime.Instant

@GraphQLDescription("Cabin reservation in past or future")
data class Reservation(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val guestIds: List<String>,
    val summary: String? = null,
    val description: String? = null,
    val sourceCreatedTime: Instant? = null,
    val sourceUpdatedTime: Instant? = null,
    var checkIn: CheckIn? = null,
    var checkOut: CheckOut? = null,
)

data class CheckIn(val time: Instant, val sourceName: CheckInOutSource, val sourceId: String)

data class CheckOut(val time: Instant, val sourceName: CheckInOutSource, val sourceId: String)

@Suppress("unused")
enum class CheckInOutSource {
    CHECK_IN_SENSOR,
    VEHICLE_TRIP,
    CALENDAR_EVENT,
}
