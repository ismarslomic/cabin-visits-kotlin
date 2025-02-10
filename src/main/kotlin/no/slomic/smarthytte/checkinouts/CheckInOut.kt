package no.slomic.smarthytte.checkinouts

import kotlinx.datetime.Instant

sealed class CheckInOut {
    abstract val time: Instant
    abstract val sourceName: CheckInOutSource
    abstract val sourceId: String
}

data class CheckIn(
    override val time: Instant,
    override val sourceName: CheckInOutSource,
    override val sourceId: String,
) : CheckInOut()

data class CheckOut(
    override val time: Instant,
    override val sourceName: CheckInOutSource,
    override val sourceId: String,
) : CheckInOut()

enum class CheckInOutSource {
    CHECK_IN_SENSOR,
    VEHICLE_TRIP,
    CALENDAR_EVENT,
}
