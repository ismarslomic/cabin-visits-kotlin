package no.slomic.smarthytte.reservations

import kotlin.time.Instant

// Most tests only care about who the guests are, not their own stays: these take guest ids, so a guest follows the
// reservation

suspend fun ReservationRepository.createReservation(
    startTime: Instant,
    endTime: Instant,
    guestIds: List<String>,
    actor: String,
): ReservationCreateOutcome = createReservation(startTime, endTime, guestIds.map(::ReservationGuest), actor)

suspend fun ReservationRepository.updateReservation(
    id: String,
    expectedVersion: Short,
    startTime: Instant,
    endTime: Instant,
    guestIds: List<String>,
    actor: String,
): ReservationUpdateOutcome =
    updateReservation(id, expectedVersion, startTime, endTime, guestIds.map(::ReservationGuest), actor)
