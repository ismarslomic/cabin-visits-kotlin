package no.slomic.smarthytte.reservations

import kotlinx.datetime.LocalDate

/**
 * A guest of a reservation, with the guest's own stay when it differs from the reservation.
 *
 * [fromDate] is the guest's first day and [toDate] the last day, both inclusive and UTC dates, like
 * [Reservation.startDate] and [Reservation.endDate]. Null means the guest follows the reservation on that end, so the
 * two ends are independent (arriving late and leaving with the group sets only [fromDate]).
 */
data class ReservationGuest(val guestId: String, val fromDate: LocalDate? = null, val toDate: LocalDate? = null)
