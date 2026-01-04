package no.slomic.smarthytte.schema.reservations.stats

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Query
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import no.slomic.smarthytte.common.MONTHS_IN_YEAR
import no.slomic.smarthytte.common.averageRounded1OrNull
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.countByMonth
import no.slomic.smarthytte.reservations.countOccupiedDaysInWindow
import no.slomic.smarthytte.reservations.diffVisitsCurrentYearWithLast12Months
import no.slomic.smarthytte.reservations.findMonthWithLongestStay
import no.slomic.smarthytte.vehicletrips.CabinVehicleTripList
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository

class ReservationStatsQueryService(
    private val reservationRepository: ReservationRepository,
    private val guestRepository: GuestRepository,
    private val vehicleTripRepository: VehicleTripRepository,
) : Query {
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
        val allReservations = reservationRepository.allReservations(sortByLatestReservation = true)
        val guestsById = guestRepository.allGuests().associateBy { it.id }
        val cabinTrips = CabinVehicleTripList(vehicleTripRepository.allVehicleTrips()).cabinTrips
        val byYear = allReservations.groupBy { it.startDate.year }

        fun computeMonthStats(
            year: Int,
            month: Month,
            yearRes: List<Reservation>,
            counts: Map<Month, Int>,
        ): MonthReservationStats {
            val monthlyRes = yearRes.filter { it.startDate.month == month }
            val dates = MonthDates(year, month)
            val occupancy = computeMonthOccupancy(allReservations, dates)
            val comparison = calculateMonthlyVisitDeltas(allReservations, counts, dates, monthlyRes.size)
            val stays = monthlyRes.map { it.startDate.daysUntilSafe(it.endDate) }

            return MonthReservationStats(
                monthNumber = month.value,
                monthName = monthNameOf(month),
                totalVisits = monthlyRes.size,
                comparedToLast30Days = comparison.comparedToLast30Days,
                comparedToSameMonthLastYear = comparison.comparedToSameMonthLastYear,
                comparedToYearToDateAverage = comparison.comparedToYearToDateAverage,
                minStayDays = stays.minOrNull(),
                maxStayDays = stays.maxOrNull(),
                avgStayDays = stays.averageRounded1OrNull(),
                percentDaysOccupied = occupancy.percentDaysOccupied,
                percentWeeksOccupied = occupancy.percentWeeksOccupied,
                guests = calculateMonthlyGuestStats(year, guestsById, dates, monthlyRes),
                drivingTime = calculateMonthDrivingTimeStats(year, month, cabinTrips),
                drivingMoments = calculateMonthDrivingMomentStats(year, month, cabinTrips),
            )
        }

        fun assembleYearStats(
            year: Int,
            yearRes: List<Reservation>,
            months: List<MonthReservationStats>,
        ): YearReservationStats {
            val jan1 = firstDayOfYear(year)
            val occupancy = computeYearOccupancy(year, yearRes)
            val guestStats = computeYearGuestStats(year, yearRes, guestsById, byYear)
            val counts = yearRes.countByMonth()

            return YearReservationStats(
                year = year,
                totalVisits = yearRes.size,
                comparedToLast12Months = allReservations.diffVisitsCurrentYearWithLast12Months(year, yearRes.size),
                averageMonthlyVisits = (yearRes.size.toDouble() / MONTHS_IN_YEAR).round1(),
                totalStayDays = occupancy.totalStayDays,
                comparedStayDaysToLast12Months = occupancy.totalStayDays - allReservations.countOccupiedDaysInWindow(
                    jan1.minus(DatePeriod(months = MONTHS_IN_YEAR)),
                    jan1,
                ),
                averageMonthlyStayDays = (occupancy.totalStayDays.toDouble() / MONTHS_IN_YEAR).round1(),
                percentDaysOccupied = occupancy.percentDaysOccupied,
                percentWeeksOccupied = occupancy.percentWeeksOccupied,
                percentMonthsOccupied = occupancy.percentMonthsOccupied,
                monthMostVisits = counts.maxByOrNull { it.value }
                    ?.let { (m, c) -> MonthCount(m.value, monthNameOf(m), c) },
                monthFewestVisits = counts.minByOrNull { it.value }
                    ?.let { (m, c) -> MonthCount(m.value, monthNameOf(m), c) },
                monthWithLongestStay = yearRes.findMonthWithLongestStay()
                    ?.let { (m, d) -> MonthStay(m.value, monthNameOf(m), d) },
                months = months,
                topGuestByDays = guestStats.topGuestByDays,
                newGuests = guestStats.newGuests,
                guests = guestStats.allGuestsSorted,
                drivingTime = calculateYearDrivingTimeStats(year, cabinTrips),
                drivingMoments = calculateYearDrivingMomentStats(year, cabinTrips),
            )
        }

        return (years?.toSet() ?: byYear.keys).sorted().map { year ->
            val yearReservations = byYear[year].orEmpty()
            val countsByMonth = yearReservations.countByMonth()
            val monthStats = Month.entries.map { computeMonthStats(year, it, yearReservations, countsByMonth) }
            assembleYearStats(year, yearReservations, monthStats)
        }
    }
}
