package no.slomic.smarthytte.schema.reservations.stats

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.common.MONTHS_IN_YEAR
import no.slomic.smarthytte.common.averageOrNullInt
import no.slomic.smarthytte.common.averageRounded1OrNull
import no.slomic.smarthytte.common.datesUntil
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.firstDateOfNextMonth
import no.slomic.smarthytte.common.firstDateOfThisMonth
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.common.formatClock
import no.slomic.smarthytte.common.formatMinutes
import no.slomic.smarthytte.common.isoWeekId
import no.slomic.smarthytte.common.minutesOfDay
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.osloTimeZone
import no.slomic.smarthytte.common.previousMonth
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.countOccupiedDaysInWindow
import no.slomic.smarthytte.vehicletrips.CabinVehicleTrip
import no.slomic.smarthytte.vehicletrips.totalDurationMinutes

data class MonthStatsContext(
    val year: Int,
    val yearReservations: List<Reservation>,
    val allReservations: List<Reservation>,
    val countsByMonth: Map<Month, Int>,
    val guestsById: Map<String, Guest>,
    val cabinTrips: List<CabinVehicleTrip>,
)

data class YearStatsContext(
    val year: Int,
    val yearReservations: List<Reservation>,
    val allReservations: List<Reservation>,
    val countsByMonth: Map<Month, Int>,
    val guestsById: Map<String, Guest>,
    val monthStats: List<MonthReservationStats>,
    val cabinTrips: List<CabinVehicleTrip>,
    val byYear: Map<Int, List<Reservation>>,
)

data class MonthDates(
    val year: Int,
    val month: Month,
    val firstOfMonth: LocalDate = firstDateOfThisMonth(year, month),
    val firstOfNextMonth: LocalDate = firstDateOfNextMonth(year, month),
)

data class YearOccupancy(
    val totalStayDays: Int,
    val percentDaysOccupied: Double,
    val percentWeeksOccupied: Double,
    val percentMonthsOccupied: Double,
)

internal object ReservationStatsEngine {
    private const val PERCENT_FACTOR: Double = 100.0

    fun buildYearStats(context: YearStatsContext): YearReservationStats {
        val year = context.year
        val jan1 = firstDayOfYear(year)
        val jan1Next = firstDayOfYearAfter(year)

        val occupancy = computeYearOccupancy(context, jan1, jan1Next)
        val guestStats = computeYearGuestStats(context, jan1, jan1Next)

        return YearReservationStats(
            year = year,
            totalVisits = context.yearReservations.size,
            comparedToLast12Months = ReservationStatsCalculationUtils.diffVisitsCurrentYearWithLast12Months(
                currentYear = year,
                totalVisitsCurrentYear = context.yearReservations.size,
                allReservations = context.allReservations,
            ),
            averageMonthlyVisits = (context.yearReservations.size.toDouble() / MONTHS_IN_YEAR.toDouble()).round1(),
            totalStayDays = occupancy.totalStayDays,
            comparedStayDaysToLast12Months =
            occupancy.totalStayDays - context.allReservations.countOccupiedDaysInWindow(
                jan1.minus(DatePeriod(months = MONTHS_IN_YEAR)),
                jan1,
            ),
            averageMonthlyStayDays = (occupancy.totalStayDays.toDouble() / MONTHS_IN_YEAR.toDouble()).round1(),
            percentDaysOccupied = occupancy.percentDaysOccupied,
            percentWeeksOccupied = occupancy.percentWeeksOccupied,
            percentMonthsOccupied = occupancy.percentMonthsOccupied,
            monthMostVisits = context.countsByMonth.maxByOrNull { it.value }
                ?.let { (m, c) -> MonthCount(monthNumber = m.value, monthName = monthNameOf(m), count = c) },
            monthFewestVisits = context.countsByMonth.minByOrNull { it.value }
                ?.let { (m, c) -> MonthCount(monthNumber = m.value, monthName = monthNameOf(m), count = c) },
            monthWithLongestStay = ReservationStatsCalculationUtils.findMonthWithLongestStay(
                context.yearReservations,
            ),
            months = context.monthStats,
            topGuestByDays = guestStats.topGuestByDays,
            newGuests = guestStats.newGuests,
            guests = guestStats.allGuestsSorted,
            drivingTime = computeYearDrivingStats(year, context.cabinTrips),
            drivingMoments = computeYearDrivingMomentStats(year, context.cabinTrips),
        )
    }

    private fun computeYearOccupancy(context: YearStatsContext, jan1: LocalDate, jan1Next: LocalDate): YearOccupancy {
        val occupiedDays: Set<LocalDate> = context.yearReservations
            .asSequence()
            .map { r ->
                val start = maxOf(r.startDate, jan1)
                val endExclusive = minOf(r.endDate, jan1Next)
                if (start < endExclusive) {
                    start.datesUntil(endExclusive).toList()
                } else {
                    emptyList()
                }
            }
            .flatten()
            .toSet()

        val daysInYear = jan1.daysUntilSafe(jan1Next)
        val totalStayDays = occupiedDays.size
        val percentDaysOccupied = if (daysInYear > 0) {
            (totalStayDays.toDouble() / daysInYear.toDouble() * PERCENT_FACTOR).round1()
        } else {
            0.0
        }

        val allYearDays = jan1.datesUntil(jan1Next).toList()
        val totalWeeksInYear: Int = allYearDays
            .map { it.isoWeekId() }
            .toSet().size
        val occupiedWeeks: Int = occupiedDays
            .map { it.isoWeekId() }
            .toSet().size
        val percentWeeksOccupied = if (totalWeeksInYear > 0) {
            (occupiedWeeks.toDouble() / totalWeeksInYear.toDouble() * PERCENT_FACTOR).round1()
        } else {
            0.0
        }

        val occupiedMonths: Int = occupiedDays.map { it.monthNumber }.toSet().size
        val percentMonthsOccupied = (occupiedMonths.toDouble() / MONTHS_IN_YEAR.toDouble() * PERCENT_FACTOR).round1()

        return YearOccupancy(totalStayDays, percentDaysOccupied, percentWeeksOccupied, percentMonthsOccupied)
    }

    private data class YearGuestStats(
        val topGuestByDays: GuestVisitStats?,
        val newGuests: List<GuestVisitStats>,
        val allGuestsSorted: List<GuestVisitStats>,
    )

    private fun computeYearGuestStats(context: YearStatsContext, jan1: LocalDate, jan1Next: LocalDate): YearGuestStats {
        val guestYearStats: List<GuestVisitStats> = ReservationStatsCalculationUtils.computeGuestStats(
            periodStart = jan1,
            periodEndExclusive = jan1Next,
            reservations = context.yearReservations,
            guestsById = context.guestsById,
            ageYear = context.year,
        )
        val prevYearGuests: Set<String> = context.byYear[context.year - 1]
            ?.flatMap { it.guestIds }
            ?.toSet()
            ?: emptySet()

        val newGuests = guestYearStats.filter { it.guestId !in prevYearGuests }.sortedWith(GuestVisitStats.COMPARATOR)
        val allGuestsSorted = guestYearStats.sortedWith(GuestVisitStats.COMPARATOR)
        val topGuestByDays = allGuestsSorted.maxByOrNull { it.totalStayDays }

        return YearGuestStats(topGuestByDays, newGuests, allGuestsSorted)
    }

    private fun computeYearDrivingStats(year: Int, cabinTrips: List<CabinVehicleTrip>): DrivingTimeStatsYear {
        val stats = DrivingYearDurations(cabinTrips, year)

        return DrivingTimeStatsYear(
            year = year,
            avgToCabinMinutes = stats.avgTo,
            avgToCabin = formatMinutes(stats.avgTo),
            minToCabinMinutes = stats.minTo,
            minToCabin = formatMinutes(stats.minTo),
            maxToCabinMinutes = stats.maxTo,
            maxToCabin = formatMinutes(stats.maxTo),
            avgFromCabinMinutes = stats.avgFrom,
            avgFromCabin = formatMinutes(stats.avgFrom),
            minFromCabinMinutes = stats.minFrom,
            minFromCabin = formatMinutes(stats.minFrom),
            maxFromCabinMinutes = stats.maxFrom,
            maxFromCabin = formatMinutes(stats.maxFrom),
        )
    }

    private data class DrivingYearDurations(val cabinTrips: List<CabinVehicleTrip>, val year: Int) {
        val toCabinDurations = cabinTrips
            .filter { it.toCabinEndDate?.year == year }
            .mapNotNull { it.toCabinTrips.totalDurationMinutes() }
        val fromCabinDurations = cabinTrips
            .filter { it.fromCabinStartDate?.year == year }
            .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }

        val avgTo = toCabinDurations.averageOrNullInt()
        val minTo = toCabinDurations.minOrNull()
        val maxTo = toCabinDurations.maxOrNull()
        val avgFrom = fromCabinDurations.averageOrNullInt()
        val minFrom = fromCabinDurations.minOrNull()
        val maxFrom = fromCabinDurations.maxOrNull()
    }

    private fun computeYearDrivingMomentStats(year: Int, cabinTrips: List<CabinVehicleTrip>): DrivingMomentStatsYear {
        val stats = DrivingYearMoments(cabinTrips, year)

        return DrivingMomentStatsYear(
            year = year,
            avgDepartureHomeMinutes = stats.depHome.averageOrNullInt(),
            avgDepartureHome = formatClock(stats.depHome.averageOrNullInt()),
            avgArrivalCabinMinutes = stats.arrCabin.averageOrNullInt(),
            avgArrivalCabin = formatClock(stats.arrCabin.averageOrNullInt()),
            avgDepartureCabinMinutes = stats.depCabin.averageOrNullInt(),
            avgDepartureCabin = formatClock(stats.depCabin.averageOrNullInt()),
            avgArrivalHomeMinutes = stats.arrHome.averageOrNullInt(),
            avgArrivalHome = formatClock(stats.arrHome.averageOrNullInt()),
        )
    }

    private data class DrivingYearMoments(val cabinTrips: List<CabinVehicleTrip>, val year: Int) {
        val depHome: List<Int> = cabinTrips.mapNotNull { trip ->
            trip.toCabinStartTimestamp?.toLocalDateTime(osloTimeZone)?.let {
                if (it.date.year == year) it.time.minutesOfDay() else null
            }
        }
        val arrCabin: List<Int> = cabinTrips.mapNotNull { trip ->
            trip.toCabinEndTimestamp?.toLocalDateTime(osloTimeZone)?.let {
                if (it.date.year == year) it.time.minutesOfDay() else null
            }
        }
        val depCabin: List<Int> = cabinTrips.mapNotNull { trip ->
            trip.fromCabinStartTimestamp?.toLocalDateTime(osloTimeZone)?.let {
                if (it.date.year == year) it.time.minutesOfDay() else null
            }
        }
        val arrHome: List<Int> = cabinTrips.mapNotNull { trip ->
            trip.fromCabinEndTimestamp?.toLocalDateTime(osloTimeZone)?.let {
                if (it.date.year == year) it.time.minutesOfDay() else null
            }
        }
    }

    fun buildMonthStats(context: MonthStatsContext): List<MonthReservationStats> = Month.entries.map { month ->
        val monthlyReservations = context.yearReservations.filter { it.startDate.month == month }
        val dates = MonthDates(context.year, month)
        val occupancy = computeMonthOccupancy(context.allReservations, dates)
        val comparison = ReservationStatsCalculationUtils.computeMonthComparison(
            context = context,
            month = month,
            dates = dates,
            totalVisits = monthlyReservations.size,
        )

        MonthReservationStats(
            monthNumber = month.value,
            monthName = monthNameOf(month),
            totalVisits = monthlyReservations.size,
            comparedToLast30Days = comparison.comparedToLast30Days,
            comparedToSameMonthLastYear = comparison.comparedToSameMonthLastYear,
            comparedToYearToDateAverage = comparison.comparedToYtdAvg,
            minStayDays = monthlyReservations.map { it.startDate.daysUntilSafe(it.endDate) }.minOrNull(),
            maxStayDays = monthlyReservations.map { it.startDate.daysUntilSafe(it.endDate) }.maxOrNull(),
            avgStayDays = monthlyReservations.map { it.startDate.daysUntilSafe(it.endDate) }
                .averageRounded1OrNull(),
            percentDaysOccupied = occupancy.percentDaysOccupied,
            percentWeeksOccupied = occupancy.percentWeeksOccupied,
            guests = ReservationStatsCalculationUtils.computeMonthGuestStats(context, dates, monthlyReservations),
            drivingTime = computeMonthDrivingStats(context.year, month, context.cabinTrips),
            drivingMoments = computeMonthDrivingMomentStats(context.year, month, context.cabinTrips),
        )
    }

    private data class MonthOccupancy(val percentDaysOccupied: Double, val percentWeeksOccupied: Double)

    private fun computeMonthOccupancy(allReservations: List<Reservation>, dates: MonthDates): MonthOccupancy {
        val allDaysInMonth = dates.firstOfMonth.datesUntil(dates.firstOfNextMonth).toList()
        val daysInMonth = allDaysInMonth.size
        val totalWeeksInMonth = allDaysInMonth.map { it.isoWeekId() }.toSet().size

        val occupiedDaysInMonth: Set<LocalDate> = allReservations
            .asSequence()
            .map { r ->
                val start = maxOf(r.startDate, dates.firstOfMonth)
                val endExclusive = minOf(r.endDate, dates.firstOfNextMonth)
                if (start < endExclusive) start.datesUntil(endExclusive).toList() else emptyList()
            }
            .flatten()
            .toSet()

        val percentDaysOccupied = if (daysInMonth > 0) {
            (occupiedDaysInMonth.size.toDouble() / daysInMonth.toDouble() * PERCENT_FACTOR).round1()
        } else {
            0.0
        }
        val occupiedWeeksInMonth = occupiedDaysInMonth.map { it.isoWeekId() }.toSet().size
        val percentWeeksOccupied = if (totalWeeksInMonth > 0) {
            (occupiedWeeksInMonth.toDouble() / totalWeeksInMonth.toDouble() * PERCENT_FACTOR).round1()
        } else {
            0.0
        }
        return MonthOccupancy(percentDaysOccupied, percentWeeksOccupied)
    }

    private fun computeMonthDrivingStats(
        year: Int,
        month: Month,
        cabinTrips: List<CabinVehicleTrip>,
    ): DrivingTimeStatsMonth {
        val (prevYear, prevMonth) = previousMonth(currentYear = year, currentMonth = month)
        val stats = DrivingMonthDurations(cabinTrips, year, month, prevYear, prevMonth)

        return DrivingTimeStatsMonth(
            monthNumber = month.value,
            monthName = monthNameOf(month),
            year = year,
            avgToCabinMinutes = stats.avgToThis,
            avgToCabin = formatMinutes(stats.avgToThis),
            minToCabinMinutes = stats.toCabinThis.minOrNull(),
            minToCabin = formatMinutes(stats.toCabinThis.minOrNull()),
            maxToCabinMinutes = stats.toCabinThis.maxOrNull(),
            maxToCabin = formatMinutes(stats.toCabinThis.maxOrNull()),
            avgFromCabinMinutes = stats.avgFromThis,
            avgFromCabin = formatMinutes(stats.avgFromThis),
            minFromCabinMinutes = stats.fromCabinThis.minOrNull(),
            minFromCabin = formatMinutes(stats.fromCabinThis.minOrNull()),
            maxFromCabinMinutes = stats.fromCabinThis.maxOrNull(),
            maxFromCabin = formatMinutes(stats.fromCabinThis.maxOrNull()),
            diffAvgToCabinMinutesVsPrevMonth = stats.diffTo,
            diffAvgToCabinVsPrevMonth = formatMinutes(stats.diffTo, showSign = true),
            diffAvgFromCabinMinutesVsPrevMonth = stats.diffFrom,
            diffAvgFromCabinVsPrevMonth = formatMinutes(stats.diffFrom, showSign = true),
        )
    }

    private data class DrivingMonthDurations(
        val cabinTrips: List<CabinVehicleTrip>,
        val year: Int,
        val month: Month,
        val prevYear: Int,
        val prevMonth: Month,
    ) {
        val toCabinThis = cabinTrips
            .filter { it.toCabinEndDate?.year == year && it.toCabinEndDate?.month == month }
            .mapNotNull { it.toCabinTrips.totalDurationMinutes() }
        val fromCabinThis = cabinTrips
            .filter { it.fromCabinStartDate?.year == year && it.fromCabinStartDate?.month == month }
            .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }
        val toCabinPrev = cabinTrips
            .filter { it.toCabinEndDate?.year == prevYear && it.toCabinEndDate?.month == prevMonth }
            .mapNotNull { it.toCabinTrips.totalDurationMinutes() }
        val fromCabinPrev = cabinTrips
            .filter { it.fromCabinStartDate?.year == prevYear && it.fromCabinStartDate?.month == prevMonth }
            .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }

        val avgToThis = toCabinThis.averageOrNullInt()
        val avgFromThis = fromCabinThis.averageOrNullInt()
        val diffTo = avgToThis?.let { at -> toCabinPrev.averageOrNullInt()?.let { at - it } }
        val diffFrom = avgFromThis?.let { af -> fromCabinPrev.averageOrNullInt()?.let { af - it } }
    }

    private fun computeMonthDrivingMomentStats(
        year: Int,
        month: Month,
        cabinTrips: List<CabinVehicleTrip>,
    ): DrivingMomentStatsMonth {
        val stats = DrivingMonthMoments(cabinTrips, year, month)

        return DrivingMomentStatsMonth(
            monthNumber = month.value,
            monthName = monthNameOf(month),
            year = year,
            avgDepartureHomeMinutes = stats.depHome.averageOrNullInt(),
            avgDepartureHome = formatClock(stats.depHome.averageOrNullInt()),
            avgArrivalCabinMinutes = stats.arrCabin.averageOrNullInt(),
            avgArrivalCabin = formatClock(stats.arrCabin.averageOrNullInt()),
            avgDepartureCabinMinutes = stats.depCabin.averageOrNullInt(),
            avgDepartureCabin = formatClock(stats.depCabin.averageOrNullInt()),
            avgArrivalHomeMinutes = stats.arrHome.averageOrNullInt(),
            avgArrivalHome = formatClock(stats.arrHome.averageOrNullInt()),
        )
    }

    private data class DrivingMonthMoments(val cabinTrips: List<CabinVehicleTrip>, val year: Int, val month: Month) {
        val depHome: List<Int> = cabinTrips.mapNotNull { trip ->
            trip.toCabinStartTimestamp?.toLocalDateTime(osloTimeZone)?.let {
                if (it.date.year == year && it.date.month == month) it.time.minutesOfDay() else null
            }
        }
        val arrCabin: List<Int> = cabinTrips.mapNotNull { trip ->
            trip.toCabinEndTimestamp?.toLocalDateTime(osloTimeZone)?.let {
                if (it.date.year == year && it.date.month == month) it.time.minutesOfDay() else null
            }
        }
        val depCabin: List<Int> = cabinTrips.mapNotNull { trip ->
            trip.fromCabinStartTimestamp?.toLocalDateTime(osloTimeZone)?.let {
                if (it.date.year == year && it.date.month == month) it.time.minutesOfDay() else null
            }
        }
        val arrHome: List<Int> = cabinTrips.mapNotNull { trip ->
            trip.fromCabinEndTimestamp?.toLocalDateTime(osloTimeZone)?.let {
                if (it.date.year == year && it.date.month == month) it.time.minutesOfDay() else null
            }
        }
    }
}
