package no.slomic.smarthytte.checkin

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.toUtcDate

data class CheckIn(val id: String, val timestamp: Instant, val status: CheckInStatus) {
    val date: LocalDate
        get() = timestamp.toUtcDate()

    val isCheckedOut: Boolean
        get() = status == CheckInStatus.CHECKED_OUT

    val isCheckedIn: Boolean
        get() = status == CheckInStatus.CHECKED_IN
}

enum class CheckInStatus {
    CHECKED_IN,
    CHECKED_OUT,
}
