package no.slomic.smarthytte.sensors.checkinouts

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.toUtcDate
import kotlin.time.Instant

data class CheckInOutSensor(val id: String, val time: Instant, val status: CheckInStatus) {
    val date: LocalDate
        get() = time.toUtcDate()

    val isCheckedOut: Boolean
        get() = status == CheckInStatus.CHECKED_OUT

    val isCheckedIn: Boolean
        get() = status == CheckInStatus.CHECKED_IN
}

enum class CheckInStatus {
    CHECKED_IN,
    CHECKED_OUT,
}
