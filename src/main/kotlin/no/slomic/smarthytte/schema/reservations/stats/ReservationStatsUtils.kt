package no.slomic.smarthytte.schema.reservations.stats

internal object ReservationStatsUtils {
    const val DAYS_IN_MONTHLY_COMPARE_WINDOW: Int = 30
    const val DAY_OFFSET_PREVIOUS: Int = 1
    const val PERCENT_FACTOR: Double = 100.0

    /**
     * Creates a comparator for comparing instances of `GuestVisitStats`.
     *
     * The comparison is performed in the following order of priority:
     * - Descending order by the total number of stay days (`totalStayDays`).
     * - Descending order by the total number of visits (`totalVisits`).
     * - Ascending order by the guest's last name (`lastName`).
     * - Ascending order by the guest's first name (`firstName`).
     *
     * @return A comparator that applies the specified ordering rules for `GuestVisitStats` objects.
     */
    fun guestStatsComparator(): Comparator<GuestVisitStats> = compareByDescending<GuestVisitStats> { it.totalStayDays }
        .thenByDescending { it.totalVisits }
        .thenBy { it.lastName }
        .thenBy { it.firstName }
}
