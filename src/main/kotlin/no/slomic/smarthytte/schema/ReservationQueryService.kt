package no.slomic.smarthytte.schema

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Query
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.until
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.schema.models.GuestVisitStats
import no.slomic.smarthytte.schema.models.MonthCount
import no.slomic.smarthytte.schema.models.MonthReservationStats
import no.slomic.smarthytte.schema.models.MonthStay
import no.slomic.smarthytte.schema.models.Reservation
import no.slomic.smarthytte.schema.models.YearReservationStats
import kotlin.math.round
import java.time.temporal.WeekFields

@Suppress("unused")
class ReservationQueryService(
    private val reservationRepository: ReservationRepository,
    private val guestRepository: GuestRepository,
) : Query {

    @GraphQLDescription("Get all reservations ordered latest reservations first")
    suspend fun allReservations(): List<Reservation> =
        reservationRepository.allReservations(sortByLatestReservation = true).map { it.toGql() }

    @GraphQLDescription("Get reservation by id")
    suspend fun reservationById(id: String): Reservation? = reservationRepository.reservationById(id)?.toGql()

    @GraphQLDescription(
        "Reservation statistics grouped by year (parent) with months (children). " +
            "Monthly metrics include: total visits, last 30 days window, same month last year, YTD average, " +
            "min/max/avg stay days. " +
            "Yearly metrics include: total visits, last 12 months window, average monthly visits (total/12), month " +
            "with most/fewest visits, month with longest stay.",
    )
    suspend fun reservationStats(
        @GraphQLDescription(
            "Optional list of years to include; if omitted, all years are returned",
        ) years: List<Int>? = null,
    ): List<YearReservationStats> {
        val reservations = reservationRepository.allReservations(sortByLatestReservation = true)
        val guestsById = guestRepository.allGuests().associateBy { it.id }

        // Group by calendar year using reservation start date
        val byYear: Map<Int, List<no.slomic.smarthytte.reservations.Reservation>> =
            reservations.groupBy { it.startDate.year }

        val targetYears: Set<Int> = years?.toSet() ?: byYear.keys

        return targetYears.sorted().map { year ->
            val yearReservations = byYear[year].orEmpty()

            // Build counts by month and compute monthly stats
            val countsByMonth = computeCountsByMonth(yearReservations)
            val monthStats = buildMonthStats(year, yearReservations, reservations, countsByMonth, guestsById)

            // Year-level metrics
            val totalVisitsYear = yearReservations.size
            val prev12MonthsCount = computeComparedToLast12Months(year, reservations)
            val comparedToLast12Months = totalVisitsYear - prev12MonthsCount
            val averageMonthlyVisits = (totalVisitsYear.toDouble() / MONTHS_IN_YEAR.toDouble()).round1()

            // Occupancy metrics (days/weeks/months)
            val jan1 = LocalDate(year, FIRST_MONTH, 1)
            val jan1Next = LocalDate(year + 1, FIRST_MONTH, 1)

            val occupiedDays: Set<LocalDate> = yearReservations
                .asSequence()
                .map { r ->
                    val start = maxOf(r.startDate, jan1)
                    val endExclusive = minOf(r.endDate, jan1Next)
                    if (start < endExclusive) start.datesUntil(endExclusive).toList() else emptyList()
                }
                .flatten()
                .toSet()

            val daysInYear = jan1.daysUntilSafe(jan1Next)
            val totalStayDays = occupiedDays.size
            val percentDaysOccupied = if (daysInYear > 0) (totalStayDays.toDouble() / daysInYear.toDouble() * PERCENT_FACTOR).round1() else 0.0

            // Compare stay-days to previous 12 months window
            val startPrev12Months = jan1.minus(DatePeriod(months = MONTHS_IN_YEAR))
            val occupiedPrev12MonthsDays = computeOccupiedDaysInWindow(reservations, startPrev12Months, jan1)
            val comparedStayDaysToLast12Months = totalStayDays - occupiedPrev12MonthsDays
            val averageMonthlyStayDays = (totalStayDays.toDouble() / MONTHS_IN_YEAR.toDouble()).round1()

            val allYearDays = jan1.datesUntil(jan1Next).toList()
            val totalWeeksInYear: Int = allYearDays.map { it.isoWeekId() }.toSet().size
            val occupiedWeeks: Int = occupiedDays.map { it.isoWeekId() }.toSet().size
            val percentWeeksOccupied = if (totalWeeksInYear > 0) (occupiedWeeks.toDouble() / totalWeeksInYear.toDouble() * PERCENT_FACTOR).round1() else 0.0

            val totalMonthsInYear = MONTHS_IN_YEAR
            val occupiedMonths: Int = occupiedDays.map { it.monthNumber }.toSet().size
            val percentMonthsOccupied = if (totalMonthsInYear > 0) (occupiedMonths.toDouble() / totalMonthsInYear.toDouble() * PERCENT_FACTOR).round1() else 0.0
            val monthMost = countsByMonth.maxByOrNull { it.value }?.let { (m, c) -> MonthCount(m, monthNameOf(m), c) }
            val monthFewest = countsByMonth.minByOrNull { it.value }?.let { (m, c) -> MonthCount(m, monthNameOf(m), c) }
            val monthWithLongestStay = findMonthWithLongestStay(yearReservations)

            // Per-guest stats for the year
            val guestYearStats: List<GuestVisitStats> = computeGuestStats(
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
            val newGuests = guestYearStats.filter { it.guestId !in prevYearGuests }.sortedWith(guestStatsComparator())
            val allGuestsSorted = guestYearStats.sortedWith(guestStatsComparator())
            val topGuestByDays = allGuestsSorted.maxByOrNull { it.totalStayDays }

            YearReservationStats(
                year = year,
                totalVisits = totalVisitsYear,
                comparedToLast12Months = comparedToLast12Months,
                averageMonthlyVisits = averageMonthlyVisits,
                totalStayDays = totalStayDays,
                comparedStayDaysToLast12Months = comparedStayDaysToLast12Months,
                averageMonthlyStayDays = averageMonthlyStayDays,
                percentDaysOccupied = percentDaysOccupied,
                percentWeeksOccupied = percentWeeksOccupied,
                percentMonthsOccupied = percentMonthsOccupied,
                monthMostVisits = monthMost,
                monthFewestVisits = monthFewest,
                monthWithLongestStay = monthWithLongestStay,
                months = monthStats,
                topGuestByDays = topGuestByDays,
                newGuests = newGuests,
                guests = allGuestsSorted,
            )
        }
    }
}

// --- Local helpers ---
private const val MONTHS_IN_YEAR: Int = 12
private const val FIRST_MONTH: Int = 1
private const val DAYS_IN_MONTHLY_COMPARE_WINDOW: Int = 30
private const val DAY_OFFSET_PREVIOUS: Int = 1
private const val ONE_DECIMAL_FACTOR: Double = 10.0
private const val PERCENT_FACTOR: Double = 100.0

private val MONTH_NAMES: List<String> = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private fun monthNameOf(monthNumber: Int): String = MONTH_NAMES[monthNumber - 1]

private fun computeCountsByMonth(
    yearReservations: List<no.slomic.smarthytte.reservations.Reservation>,
): Map<Int, Int> = (FIRST_MONTH..MONTHS_IN_YEAR).associateWith { m ->
    yearReservations.count { it.startDate.monthNumber == m }
}

private fun buildMonthStats(
    year: Int,
    yearReservations: List<no.slomic.smarthytte.reservations.Reservation>,
    allReservations: List<no.slomic.smarthytte.reservations.Reservation>,
    countsByMonth: Map<Int, Int>,
    guestsById: Map<String, no.slomic.smarthytte.guests.Guest>,
): List<MonthReservationStats> = (FIRST_MONTH..MONTHS_IN_YEAR).map { month ->
    val monthName = monthNameOf(month)
    val monthlyReservations = yearReservations.filter { it.startDate.monthNumber == month }

    val totalVisits = monthlyReservations.size

    val stayDays = monthlyReservations.map { r -> r.startDate.daysUntilSafe(r.endDate) }
    val minStay = stayDays.minOrNull()
    val maxStay = stayDays.maxOrNull()
    val avgStay = if (stayDays.isNotEmpty()) stayDays.averageRounded1() else null

    val firstOfMonth = LocalDate(year, month, 1)
    val lastDayPrevMonth = firstOfMonth.minus(DatePeriod(days = DAY_OFFSET_PREVIOUS))
    val startLast30 = firstOfMonth.minus(DatePeriod(days = DAYS_IN_MONTHLY_COMPARE_WINDOW))

    val firstOfNextMonth = if (month == MONTHS_IN_YEAR) LocalDate(year + 1, FIRST_MONTH, 1) else LocalDate(year, month + 1, 1)
    val allDaysInMonth = firstOfMonth.datesUntil(firstOfNextMonth).toList()
    val daysInMonth = allDaysInMonth.size
    val totalWeeksInMonth = allDaysInMonth.map { it.isoWeekId() }.toSet().size

    val occupiedDaysInMonth: Set<LocalDate> = allReservations
        .asSequence()
        .map { r ->
            val start = maxOf(r.startDate, firstOfMonth)
            val endExclusive = minOf(r.endDate, firstOfNextMonth)
            if (start < endExclusive) start.datesUntil(endExclusive).toList() else emptyList()
        }
        .flatten()
        .toSet()
    val percentDaysOccupied = if (daysInMonth > 0) (occupiedDaysInMonth.size.toDouble() / daysInMonth.toDouble() * PERCENT_FACTOR).round1() else 0.0
    val occupiedWeeksInMonth = occupiedDaysInMonth.map { it.isoWeekId() }.toSet().size
    val percentWeeksOccupied = if (totalWeeksInMonth > 0) (occupiedWeeksInMonth.toDouble() / totalWeeksInMonth.toDouble() * PERCENT_FACTOR).round1() else 0.0

    val last30DaysCount = allReservations.count { r ->
        val d = r.startDate
        d in startLast30..lastDayPrevMonth
    }
    val comparedToLast30Days = totalVisits - last30DaysCount

    val sameMonthLastYearCount = allReservations.count { r ->
        r.startDate.year == (year - 1) && r.startDate.monthNumber == month
    }
    val comparedToSameMonthLastYear = totalVisits - sameMonthLastYearCount

    val monthsSoFar = month
    val ytdTotal = (FIRST_MONTH..month).sumOf { m -> countsByMonth[m] ?: 0 }
    val ytdAverage = if (monthsSoFar > 0) ytdTotal.toDouble() / monthsSoFar else 0.0
    val comparedToYtdAvg = (totalVisits.toDouble() - ytdAverage).round1()

    val guestMonthStats: List<GuestVisitStats> = computeGuestStats(
        periodStart = firstOfMonth,
        periodEndExclusive = firstOfNextMonth,
        reservations = monthlyReservations,
        guestsById = guestsById,
        ageYear = year,
    ).sortedWith(guestStatsComparator())

    MonthReservationStats(
        monthNumber = month,
        monthName = monthName,
        totalVisits = totalVisits,
        comparedToLast30Days = comparedToLast30Days,
        comparedToSameMonthLastYear = comparedToSameMonthLastYear,
        comparedToYearToDateAverage = comparedToYtdAvg,
        minStayDays = minStay,
        maxStayDays = maxStay,
        avgStayDays = avgStay,
        percentDaysOccupied = percentDaysOccupied,
        percentWeeksOccupied = percentWeeksOccupied,
        guests = guestMonthStats,
    )
}

private fun computeComparedToLast12Months(
    year: Int,
    allReservations: List<no.slomic.smarthytte.reservations.Reservation>,
): Int {
    val firstOfYear = LocalDate(year, FIRST_MONTH, 1)
    val startPrev12Months = firstOfYear.minus(DatePeriod(months = MONTHS_IN_YEAR))
    val endPrev12Months = firstOfYear.minus(DatePeriod(days = DAY_OFFSET_PREVIOUS))
    return allReservations.count { r ->
        val d = r.startDate
        d in startPrev12Months..endPrev12Months
    }
}

private fun findMonthWithLongestStay(
    yearReservations: List<no.slomic.smarthytte.reservations.Reservation>,
): MonthStay? = yearReservations
    .maxByOrNull { it.startDate.daysUntilSafe(it.endDate) }
    ?.let { r ->
        val m = r.startDate.monthNumber
        MonthStay(m, monthNameOf(m), r.startDate.daysUntilSafe(r.endDate))
    }

// Computes number of unique occupied days within [startInclusive, endExclusive)
private fun computeOccupiedDaysInWindow(
    reservations: List<no.slomic.smarthytte.reservations.Reservation>,
    startInclusive: LocalDate,
    endExclusive: LocalDate,
): Int {
    if (startInclusive >= endExclusive) return 0
    return reservations
        .asSequence()
        .map { r ->
            val start = maxOf(r.startDate, startInclusive)
            val endEx = minOf(r.endDate, endExclusive)
            if (start < endEx) start.datesUntil(endEx).toList() else emptyList()
        }
        .flatten()
        .toSet()
        .size
}
private fun List<Int>.averageRounded1(): Double = if (isEmpty()) 0.0 else (sum().toDouble() / size).round1()

private fun Double.round1(): Double = round(this * ONE_DECIMAL_FACTOR) / ONE_DECIMAL_FACTOR

private fun LocalDate.daysUntilSafe(end: LocalDate): Int =
    this.until(end, DateTimeUnit.DAY).let { if (it < 0) 0 else it }

private fun LocalDate.datesUntil(endExclusive: LocalDate): Sequence<LocalDate> = sequence {
    var d = this@datesUntil
    while (d < endExclusive) {
        yield(d)
        d = d.plus(DatePeriod(days = DAY_OFFSET_PREVIOUS))
    }
}

private fun LocalDate.isoWeekId(): Pair<Int, Int> {
    val jd = java.time.LocalDate.of(this.year, this.monthNumber, this.dayOfMonth)
    val wf = WeekFields.ISO
    val weekBasedYear = jd.get(wf.weekBasedYear())
    val weekOfYear = jd.get(wf.weekOfWeekBasedYear())
    return weekBasedYear to weekOfYear
}

// --- Guest stats helpers ---
private fun computeGuestStats(
    periodStart: LocalDate,
    periodEndExclusive: LocalDate,
    reservations: List<no.slomic.smarthytte.reservations.Reservation>,
    guestsById: Map<String, no.slomic.smarthytte.guests.Guest>,
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

private fun guestStatsComparator(): Comparator<GuestVisitStats> = compareByDescending<GuestVisitStats> { it.totalStayDays }
    .thenByDescending { it.totalVisits }
    .thenBy { it.lastName }
    .thenBy { it.firstName }
