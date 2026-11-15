package no.slomic.smarthytte.vehicletrips

import kotlin.time.Duration
import kotlin.time.Instant

/** What the background sync does in one pass. */
enum class BackgroundSyncMode {
    /** Fetch vehicle trips and check-in/out sensor data, then update the check-in/out status. */
    ALL,

    /** Only fetch vehicle trips: outside the reservation window, as the history may be a rolling window. */
    TRIPS_ONLY,

    NONE,
}

/**
 * Inside the reservation window everything runs on every pass. Outside it only the vehicle trips run, and only when a
 * fetch is due. At night nothing runs.
 */
fun backgroundSyncMode(isDaytime: Boolean, isWithinReservationWindow: Boolean, isTripFetchDue: Boolean) = when {
    !isDaytime -> BackgroundSyncMode.NONE
    isWithinReservationWindow -> BackgroundSyncMode.ALL
    isTripFetchDue -> BackgroundSyncMode.TRIPS_ONLY
    else -> BackgroundSyncMode.NONE
}

/** A fetch is due when none has succeeded yet, or the last success is at least [interval] old. */
fun isFetchDue(lastSuccess: Instant?, now: Instant, interval: Duration): Boolean =
    lastSuccess == null || now - lastSuccess >= interval
