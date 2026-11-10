package no.slomic.smarthytte.reservations

import kotlinx.serialization.Serializable

/**
 * Where a reservation or a guest's visit is in time, see [Reservation.visitStatus]. Used by the reservation API and by
 * the guest profile statistics.
 */
@Serializable
enum class VisitStatus { COMPLETED, ONGOING, UPCOMING }
