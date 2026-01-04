package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.stayDaysByGuest
import no.slomic.smarthytte.reservations.visitsByGuest

/**
 * Calculates monthly guest visit statistics based on a given context, set of dates, and a list of reservations.
 *
 * The method aggregates guest-specific statistics such as total visits and total stay days within the provided
 * time period, leveraging the provided reservations and context data. The resulting statistics are sorted
 * in descending order by total stay days, followed by total visits, and then by the guest's last name and first name.
 *
 * @param context the context containing year-specific and global reservation data, guest mappings, and other statistics
 * @param dates the time period details, including the start date (inclusive) and the end date (exclusive) of the month
 * @param monthlyReservations the list of reservations relevant to the specific month for which statistics are
 * calculated
 * @return a sorted list of per-guest visit statistics, including details such as guest name, age, total visits,
 * and total stay days within the specified month
 */
fun calculateMonthlyGuestStats(
    year: Int,
    guestsById: Map<String, Guest>,
    dates: MonthDates,
    monthlyReservations: List<Reservation>,
): List<GuestVisitStats> = aggregateGuestVisitStats(
    periodStart = dates.firstOfMonth,
    periodEndExclusive = dates.firstOfNextMonth,
    reservations = monthlyReservations,
    guestsById = guestsById,
    ageYear = year,
).sortedWith(GuestVisitStats.COMPARATOR)

data class YearGuestStats(
    val topGuestByDays: GuestVisitStats?,
    val newGuests: List<GuestVisitStats>,
    val allGuestsSorted: List<GuestVisitStats>,
)

fun computeYearGuestStats(
    year: Int,
    yearReservations: List<Reservation>,
    guestsById: Map<String, Guest>,
    byYear: Map<Int, List<Reservation>>,
): YearGuestStats {
    val jan1 = firstDayOfYear(year)
    val jan1Next = firstDayOfYearAfter(year)

    val guestYearStats: List<GuestVisitStats> = aggregateGuestVisitStats(
        periodStart = jan1,
        periodEndExclusive = jan1Next,
        reservations = yearReservations,
        guestsById = guestsById,
        ageYear = year,
    )
    val prevYearGuests: Set<String> = byYear[year - 1]
        ?.flatMap { it.guestIds }
        ?.toSet()
        ?: emptySet()

    val newGuests = guestYearStats.filter { it.guestId !in prevYearGuests }.sortedWith(GuestVisitStats.COMPARATOR)
    val allGuestsSorted = guestYearStats.sortedWith(GuestVisitStats.COMPARATOR)
    val topGuestByDays = allGuestsSorted.maxByOrNull { it.totalStayDays }

    return YearGuestStats(topGuestByDays, newGuests, allGuestsSorted)
}

/**
 * Internal helper to aggregate raw reservation data into guest visit statistics for a specified time period based
 * on reservations and guest data.
 *
 * The method computes the number of visits and the total stay days for each guest during the specified period.
 * Guests are identified using their IDs from the reservations and mapped to their corresponding data from the
 * `guestsById` map. If a guest ID in the reservations is not found in the `guestsById` map, that guest is
 * excluded from the results.
 *
 * @param periodStart the start date of the period (inclusive) for which statistics are aggregated
 * @param periodEndExclusive the end date of the period (exclusive) for which statistics are aggregated
 * @param reservations the list of reservations used to calculate visit and stay day statistics
 * @param guestsById a map of guest IDs to their respective `Guest` objects
 * @param ageYear the calendar year used to calculate the age of each guest
 * @return a list of aggregated statistics for each guest, containing their ID, name, age, total visits, and total
 * stay days
 */
fun aggregateGuestVisitStats(
    periodStart: LocalDate,
    periodEndExclusive: LocalDate,
    reservations: List<Reservation>,
    guestsById: Map<String, Guest>,
    ageYear: Int,
): List<GuestVisitStats> {
    if (reservations.isEmpty()) return emptyList()

    val visitsByGuest = reservations.visitsByGuest()
    val stayDaysByGuest = reservations.stayDaysByGuest(periodStart, periodEndExclusive)

    return (visitsByGuest.keys + stayDaysByGuest.keys)
        .toSet()
        .mapNotNull { guestId ->
            val guest = guestsById[guestId] ?: return@mapNotNull null
            GuestVisitStats(
                guestId = guestId,
                firstName = guest.firstName,
                lastName = guest.lastName,
                age = (ageYear - guest.birthYear.toInt()).coerceAtLeast(0),
                totalVisits = visitsByGuest[guestId] ?: 0,
                totalStayDays = stayDaysByGuest[guestId] ?: 0,
            )
        }
}
