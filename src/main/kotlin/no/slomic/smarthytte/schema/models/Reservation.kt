package no.slomic.smarthytte.schema.models

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("Cabin reservation in past or future")
data class Reservation(
    val id: String,
    val startTime: String,
    val endTime: String,
    val guestIds: List<String>,
    val summary: String? = null,
    val description: String? = null,
    val sourceCreatedTime: String? = null,
    val sourceUpdatedTime: String? = null,
    var checkIn: CheckIn? = null,
    var checkOut: CheckOut? = null,
)

data class CheckIn(val time: String, val sourceName: CheckInOutSource, val sourceId: String)

data class CheckOut(val time: String, val sourceName: CheckInOutSource, val sourceId: String)

@Suppress("unused")
enum class CheckInOutSource {
    CHECK_IN_SENSOR,
    VEHICLE_TRIP,
    CALENDAR_EVENT,
}
