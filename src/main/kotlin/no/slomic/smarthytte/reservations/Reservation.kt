package no.slomic.smarthytte.reservations

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.toUtcDate
import no.slomic.smarthytte.common.utcDateNow
import no.slomic.smarthytte.vehicletrips.CabinVehicleTrip

data class Reservation(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val guestIds: List<String>,
    val cabinVehicleTrip: CabinVehicleTrip? = null,
    val summary: String? = null,
    val description: String? = null,
    val sourceCreatedTime: Instant? = null,
    val sourceUpdatedTime: Instant? = null,
    val notionId: String? = null,
    var checkIn: CheckIn? = null,
    var checkOut: CheckOut? = null,
) {
    val hasStarted: Boolean
        get() = startDate <= utcDateNow()

    val hasEnded: Boolean
        get() = endDate <= utcDateNow()

    val startDate: LocalDate
        get() = startTime.toUtcDate()

    val endDate: LocalDate
        get() = endTime.toUtcDate()
}
