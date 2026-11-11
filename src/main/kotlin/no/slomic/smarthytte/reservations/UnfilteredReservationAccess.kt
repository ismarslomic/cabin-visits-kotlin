package no.slomic.smarthytte.reservations

/**
 * Marks reservation reads that do not filter by the authenticated user. Only background tasks that act for the system
 * may opt in (`@OptIn(UnfilteredReservationAccess::class)`); routes and anything that serves a user must not.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This read ignores who the caller is (no access control). Use it only from system tasks, " +
        "never for a user.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class UnfilteredReservationAccess
