package no.slomic.smarthytte.reservations

import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.GuestRepository
import kotlin.time.Clock
import kotlin.time.Instant

/** A clock that always says 2026-04-11 10:00 UTC. */
val fixedClock = object : Clock {
    override fun now(): Instant = Instant.parse("2026-04-11T10:00:00Z")
}

/**
 * Four guests and these reservations: `r1` (guests a, b) spans the March/April boundary and is completed, `r2`
 * (guest b) is ongoing, `r3a` and `r3b` (guest c) start at the same time and are upcoming, and `r0` (guest a) is a
 * 0-night reservation inside one UTC day.
 */
suspend fun givenReservations(guests: GuestRepository, reservations: ReservationRepository) {
    guests.addOrUpdate(testGuest("a", role = AppRole.ADMIN))
    guests.addOrUpdate(testGuest("b", role = AppRole.GUEST_USER))
    guests.addOrUpdate(testGuest("c", role = AppRole.GUEST_USER))
    guests.addOrUpdate(testGuest("outsider", role = AppRole.GUEST_USER))

    listOf(
        reservation("r1", "2026-03-30T16:00:00Z", "2026-04-02T13:00:00Z", "a", "b"),
        reservation("r2", "2026-04-10T16:00:00Z", "2026-04-12T13:00:00Z", "b"),
        reservation("r0", "2026-05-04T10:00:00Z", "2026-05-04T12:00:00Z", "a"),
        reservation("r3b", "2026-06-01T16:00:00Z", "2026-06-03T13:00:00Z", "c"),
        reservation("r3a", "2026-06-01T16:00:00Z", "2026-06-03T13:00:00Z", "c"),
    ).forEach { reservations.addOrUpdate(it) }
}

private fun reservation(id: String, start: String, end: String, vararg guestIds: String) = Reservation(
    id = id,
    startTime = Instant.parse(start),
    endTime = Instant.parse(end),
    guestIds = guestIds.toList(),
)
