package no.slomic.smarthytte.checkin

import kotlinx.datetime.Instant

data class CheckIn(val timestamp: Instant, val status: CheckInStatus)

enum class CheckInStatus {
    CHECKED_IN,
    CHECKED_OUT,
}
