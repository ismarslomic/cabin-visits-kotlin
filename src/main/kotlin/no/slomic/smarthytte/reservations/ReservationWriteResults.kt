package no.slomic.smarthytte.reservations

/** The outcome of [ReservationRepository.createReservation]. Nothing was written unless it is [Created]. */
sealed interface ReservationCreateOutcome {
    data class Created(val reservation: Reservation) : ReservationCreateOutcome

    /** The times overlap another reservation. */
    data object Overlap : ReservationCreateOutcome

    /** These guest ids do not exist. */
    data class UnknownGuests(val guestIds: List<String>) : ReservationCreateOutcome
}

/** The outcome of [ReservationRepository.updateReservation]. Nothing was written unless it is [Updated]. */
sealed interface ReservationUpdateOutcome {
    /** The reservation was changed. [timesChanged] means a calendar update was queued and derived data was cleared. */
    data class Updated(val reservation: Reservation, val timesChanged: Boolean) : ReservationUpdateOutcome

    /** The times and guests are the ones that are stored: the version is unchanged. */
    data class Unchanged(val reservation: Reservation) : ReservationUpdateOutcome

    data object NotFound : ReservationUpdateOutcome

    /** The stored version is not the one the client read. */
    data object VersionMismatch : ReservationUpdateOutcome

    /** The new times overlap another reservation. */
    data object Overlap : ReservationUpdateOutcome

    /** These guest ids do not exist. */
    data class UnknownGuests(val guestIds: List<String>) : ReservationUpdateOutcome
}

/** The outcome of [ReservationRepository.deleteReservation]. Nothing was deleted unless it is [Deleted]. */
sealed interface ReservationDeleteOutcome {
    data object Deleted : ReservationDeleteOutcome

    data object NotFound : ReservationDeleteOutcome

    /** The stored version is not the one the client read. */
    data object VersionMismatch : ReservationDeleteOutcome
}
