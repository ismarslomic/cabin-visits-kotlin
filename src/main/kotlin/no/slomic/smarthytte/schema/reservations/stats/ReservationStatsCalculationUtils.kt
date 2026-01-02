package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation

internal object ReservationStatsCalculationUtils {
    private const val DAYS_IN_MONTHLY_COMPARE_WINDOW: Int = 30
    private const val DAY_OFFSET_PREVIOUS: Int = 1

    fun computeGuestStats(
        periodStart: LocalDate,
        periodEndExclusive: LocalDate,
        reservations: List<Reservation>,
        guestsById: Map<String, Guest>,
        ageYear: Int,
    ): List<GuestVisitStats> {
        if (reservations.isEmpty()) return emptyList()

        val visitsByGuest: Map<String, Int> = reservations
            .flatMap { r -> r.guestIds.map { it } }
            .groupingBy { it }
            .eachCount()

        val stayDaysByGuest: MutableMap<String, Int> = mutableMapOf()
        reservations.forEach { r ->
            val overlapStart = maxOf(r.startDate, periodStart)
            val overlapEndExclusive = minOf(r.endDate, periodEndExclusive)
            val days = if (overlapStart < overlapEndExclusive) overlapStart.daysUntilSafe(overlapEndExclusive) else 0
            r.guestIds.forEach { gid ->
                stayDaysByGuest[gid] = (stayDaysByGuest[gid] ?: 0) + days
            }
        }

        return (visitsByGuest.keys + stayDaysByGuest.keys)
            .toSet()
            .mapNotNull { gid ->
                val g = guestsById[gid] ?: return@mapNotNull null
                GuestVisitStats(
                    guestId = gid,
                    firstName = g.firstName,
                    lastName = g.lastName,
                    age = (ageYear - g.birthYear.toInt()).coerceAtLeast(0),
                    totalVisits = visitsByGuest[gid] ?: 0,
                    totalStayDays = stayDaysByGuest[gid] ?: 0,
                )
            }
    }

    fun computeMonthGuestStats(
        context: MonthStatsContext,
        dates: MonthDates,
        monthlyReservations: List<Reservation>,
    ): List<GuestVisitStats> = computeGuestStats(
        periodStart = dates.firstOfMonth,
        periodEndExclusive = dates.firstOfNextMonth,
        reservations = monthlyReservations,
        guestsById = context.guestsById,
        ageYear = context.year,
    ).sortedWith(GuestVisitStats.COMPARATOR)

    fun computeMonthComparison(
        context: MonthStatsContext,
        month: Month,
        dates: MonthDates,
        totalVisits: Int,
    ): MonthComparison {
        val lastDayPrevMonth = dates.firstOfMonth.minus(DatePeriod(days = DAY_OFFSET_PREVIOUS))
        val startLast30 = dates.firstOfMonth.minus(DatePeriod(days = DAYS_IN_MONTHLY_COMPARE_WINDOW))

        val last30DaysCount = context.allReservations.count { r ->
            r.startDate in startLast30..lastDayPrevMonth
        }
        val sameMonthLastYearCount = context.allReservations.count { r ->
            r.startDate.year == (context.year - 1) && r.startDate.month == month
        }

        val ytdTotal = Month.entries
            .filter { it <= month }
            .sumOf { m -> context.countsByMonth[m] ?: 0 }
        val ytdAverage = ytdTotal.toDouble() / month.value

        return MonthComparison(
            comparedToLast30Days = totalVisits - last30DaysCount,
            comparedToSameMonthLastYear = totalVisits - sameMonthLastYearCount,
            comparedToYtdAvg = (totalVisits.toDouble() - ytdAverage).round1(),
        )
    }
}

data class MonthComparison(
    val comparedToLast30Days: Int,
    val comparedToSameMonthLastYear: Int,
    val comparedToYtdAvg: Double,
)
