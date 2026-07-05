package no.slomic.smarthytte.statistics

import kotlinx.datetime.Month
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.common.firstDayOfYearBefore
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.osloDateNow
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.countByMonth
import no.slomic.smarthytte.reservations.countOccupiedNightsInWindow
import no.slomic.smarthytte.reservations.diffVisitsCurrentYearWithLast12Months
import no.slomic.smarthytte.reservations.findMonthWithLongestStay
import no.slomic.smarthytte.reservations.nightsByGuest
import no.slomic.smarthytte.reservations.visitsByGuest
import no.slomic.smarthytte.statistics.calculator.MonthDates
import no.slomic.smarthytte.statistics.calculator.calculateMonthDrivingMomentStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthDrivingTimeStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthlyDaysStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthlyGuestStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthlyNightsStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthlyVisitDeltas
import no.slomic.smarthytte.statistics.calculator.calculateYearDrivingMomentStats
import no.slomic.smarthytte.statistics.calculator.calculateYearDrivingTimeStats
import no.slomic.smarthytte.statistics.calculator.computeMonthOccupancy
import no.slomic.smarthytte.statistics.calculator.computeYearGuestStats
import no.slomic.smarthytte.statistics.calculator.computeYearOccupancy
import no.slomic.smarthytte.statistics.model.AgeGroup
import no.slomic.smarthytte.statistics.model.CurrentReservationInfo
import no.slomic.smarthytte.statistics.model.CurrentYearStats
import no.slomic.smarthytte.statistics.model.GenderDistribution
import no.slomic.smarthytte.statistics.model.GuestRanking
import no.slomic.smarthytte.statistics.model.GuestStats
import no.slomic.smarthytte.statistics.model.LiveStats
import no.slomic.smarthytte.statistics.model.MonthCount
import no.slomic.smarthytte.statistics.model.MonthStats
import no.slomic.smarthytte.statistics.model.MonthStay
import no.slomic.smarthytte.statistics.model.NextReservationInfo
import no.slomic.smarthytte.statistics.model.YearStats

private const val PERCENT_FACTOR = 100.0
private const val TOP_GUESTS_LIMIT = 10
private const val MONTHS_PER_YEAR = 12
private const val KWH_PER_100KM_FACTOR = 100
private const val AGE_CHILD_MAX = 12
private const val AGE_TEEN_MIN = 13
private const val AGE_TEEN_MAX = 17
private const val AGE_YOUNG_ADULT_MIN = 18
private const val AGE_YOUNG_ADULT_MAX = 30
private const val AGE_ADULT_MIN = 31
private const val AGE_ADULT_MAX = 50
private const val AGE_SENIOR_MIN = 51

class StatsService(
    private val reservationRepository: ReservationRepository,
    private val guestRepository: GuestRepository,
) {
    suspend fun getLiveStats(): LiveStats {
        val today = osloDateNow()
        val allReservations = reservationRepository.allReservations()
        val allGuests = guestRepository.allGuests()
        val guestsById = allGuests.associateBy { it.id }

        val current = allReservations.firstOrNull { it.hasStarted && !it.hasEnded }
        val next = allReservations.filter { !it.hasStarted }.minByOrNull { it.startDate }

        val currentInfo =
            current?.let { r ->
                val guestNames = r.guestIds.mapNotNull { guestsById[it]?.firstName }
                val remaining = today.daysUntilSafe(r.endDate)
                CurrentReservationInfo(r.startDate, r.endDate, guestNames, remaining)
            }

        val nextInfo =
            next?.let { r ->
                val guestNames = r.guestIds.mapNotNull { guestsById[it]?.firstName }
                val daysUntil = today.daysUntilSafe(r.startDate)
                NextReservationInfo(r.startDate, r.endDate, guestNames, daysUntil)
            }

        val allTimeNights = allReservations.sumOf { it.durationNights }

        return LiveStats(
            isOccupied = current != null,
            currentReservation = currentInfo,
            nextReservation = nextInfo,
            allTimeVisits = allReservations.size,
            allTimeNights = allTimeNights,
            allTimeUniqueGuests = allGuests.size,
        )
    }

    suspend fun getCurrentYearStats(): CurrentYearStats {
        val currentYear = osloDateNow().year
        val allReservations = reservationRepository.allReservations()
        val yearReservations = allReservations.filter { it.startDate.year == currentYear }
        val totalNights = yearReservations.sumOf { it.durationNights }
        val totalKm =
            yearReservations
                .flatMap { it.toCabinVehicleTrips + it.fromCabinVehicleTrips }
                .sumOf { it.distance }
                .takeIf { it > 0 }

        return CurrentYearStats(
            year = currentYear,
            visits = yearReservations.size,
            totalNights = totalNights,
            totalDistanceKm = totalKm?.round1(),
        )
    }

    suspend fun getAvailableYears(): List<Int> {
        val allReservations = reservationRepository.allReservations()
        return allReservations.map { it.startDate.year }.distinct().sorted()
    }

    suspend fun getYearStats(year: Int): YearStats {
        val allReservations = reservationRepository.allReservations()
        val allGuests = guestRepository.allGuests()
        val guestsById = allGuests.associateBy { it.id }
        val byYear = allReservations.groupBy { it.startDate.year }
        val yearReservations = byYear[year] ?: emptyList()

        val visitStats = computeYearVisitStats(year, allReservations, yearReservations)
        val guestStats = computeYearGuestStats(year, yearReservations, guestsById, byYear)
        val evStats = computeEvStats(yearReservations)

        val drivingTime =
            calculateYearDrivingTimeStats(year, allReservations)
                .takeIf { it.avgToCabinMinutes != null || it.avgFromCabinMinutes != null }
        val drivingMoments =
            calculateYearDrivingMomentStats(year, allReservations)
                .takeIf { it.avgDepartureHomeMinutes != null || it.avgDepartureCabinMinutes != null }

        val months =
            Month.entries.map { month ->
                buildMonthStats(year, month, allReservations, byYear, guestsById)
            }

        return YearStats(
            year = year,
            totalVisits = visitStats.totalVisits,
            visitsComparedToLast12Months = visitStats.comparedToLast12,
            averageMonthlyVisits = visitStats.avgMonthlyVisits,
            averageGroupSize = visitStats.avgGroupSize,
            averageNightsPerVisit = visitStats.avgNightsPerVisit,
            totalNights = visitStats.occupancy.totalNights,
            totalNightsComparedToLast12Months = visitStats.nightsComparedToLast12,
            averageMonthlyNights = visitStats.avgMonthlyNights,
            percentDaysOccupied = visitStats.occupancy.percentDaysOccupied,
            percentWeeksOccupied = visitStats.occupancy.percentWeeksOccupied,
            percentMonthsOccupied = visitStats.occupancy.percentMonthsOccupied,
            monthMostVisits = visitStats.monthMostVisits,
            monthFewestVisits = visitStats.monthFewestVisits,
            monthWithLongestStay = visitStats.longestStay,
            topGuestByDays = guestStats.topGuestByDays,
            newGuests = guestStats.newGuests,
            guests = guestStats.allGuestsSorted,
            totalDistanceKm = evStats.totalKm,
            totalEnergyConsumedKwh = evStats.totalKwh,
            avgEnergyConsumptionKwhPer100km = evStats.avgKwhPer100km,
            totalEnergyRegeneratedKwh = evStats.totalRegeneratedKwh,
            drivingTime = drivingTime,
            drivingMoments = drivingMoments,
            months = months,
        )
    }

    suspend fun getMonthStats(year: Int, month: Int): MonthStats {
        val kotlinMonth = Month.entries[month - 1]
        val allReservations = reservationRepository.allReservations()
        val allGuests = guestRepository.allGuests()
        val guestsById = allGuests.associateBy { it.id }
        val byYear = allReservations.groupBy { it.startDate.year }

        return buildMonthStats(year, kotlinMonth, allReservations, byYear, guestsById)
    }

    suspend fun getGuestStats(): GuestStats {
        val allGuests = guestRepository.allGuests()
        val allReservations = reservationRepository.allReservations()
        val guestsById = allGuests.associateBy { it.id }

        val firstYear = allReservations.minOfOrNull { it.startDate.year } ?: osloDateNow().year
        val jan1 = firstDayOfYear(firstYear)
        val jan1Next = firstDayOfYearAfter(osloDateNow().year)

        val visitsByGuest = allReservations.visitsByGuest()
        val nightsByGuest = allReservations.nightsByGuest(jan1, jan1Next)

        val rankings =
            (visitsByGuest.keys + nightsByGuest.keys).toSet().mapNotNull { guestId ->
                val guest = guestsById[guestId] ?: return@mapNotNull null
                GuestRanking(
                    guestId = guestId,
                    firstName = guest.firstName,
                    lastName = guest.lastName,
                    totalVisits = visitsByGuest[guestId] ?: 0,
                    totalNights = nightsByGuest[guestId] ?: 0,
                )
            }

        val topByVisits = rankings.sortedByDescending { it.totalVisits }.take(TOP_GUESTS_LIMIT)
        val topByDays = rankings.sortedByDescending { it.totalNights }.take(TOP_GUESTS_LIMIT)

        val maleCount = allGuests.count { it.gender == Gender.MALE }
        val femaleCount = allGuests.count { it.gender == Gender.FEMALE }
        val total = allGuests.size.toDouble().coerceAtLeast(1.0)
        val genderDist =
            GenderDistribution(
                maleCount = maleCount,
                femaleCount = femaleCount,
                malePercent = (maleCount / total * PERCENT_FACTOR).round1(),
                femalePercent = (femaleCount / total * PERCENT_FACTOR).round1(),
            )

        val currentYear = osloDateNow().year
        val ageBuckets =
            listOf(
                "0-12" to (0..AGE_CHILD_MAX),
                "13-17" to (AGE_TEEN_MIN..AGE_TEEN_MAX),
                "18-30" to (AGE_YOUNG_ADULT_MIN..AGE_YOUNG_ADULT_MAX),
                "31-50" to (AGE_ADULT_MIN..AGE_ADULT_MAX),
                "51+" to (AGE_SENIOR_MIN..Int.MAX_VALUE),
            )
        val ageGroups =
            ageBuckets.map { (label, range) ->
                val count = allGuests.count { (currentYear - it.birthYear.toInt()) in range }
                AgeGroup(label, guestCount = count, percent = (count / total * PERCENT_FACTOR).round1())
            }

        return GuestStats(topByVisits, topByDays, genderDist, ageGroups)
    }

    private fun buildMonthStats(
        year: Int,
        month: Month,
        allReservations: List<Reservation>,
        byYear: Map<Int, List<Reservation>>,
        guestsById: Map<String, Guest>,
    ): MonthStats {
        val dates = MonthDates(year, month)
        val yearReservations = byYear[year] ?: emptyList()
        val monthlyReservations = yearReservations.filter { it.startDate.month == month }
        val totalVisits = monthlyReservations.size

        val countsByMonth = yearReservations.countByMonth()
        val deltas = calculateMonthlyVisitDeltas(allReservations, countsByMonth, dates, totalVisits)
        val daysStats = calculateMonthlyDaysStats(allReservations, monthlyReservations, dates)
        val nightsStats = calculateMonthlyNightsStats(allReservations, monthlyReservations, dates)
        val occupancy = computeMonthOccupancy(allReservations, dates)

        val guestStats = calculateMonthlyGuestStats(year, guestsById, dates, monthlyReservations)

        val drivingTime =
            calculateMonthDrivingTimeStats(year, month, allReservations)
                .takeIf { it.avgToCabinMinutes != null || it.avgFromCabinMinutes != null }
        val drivingMoments =
            calculateMonthDrivingMomentStats(year, month, allReservations)
                .takeIf { it.avgDepartureHomeMinutes != null || it.avgDepartureCabinMinutes != null }

        return MonthStats(
            year = year,
            monthNumber = month.ordinal + 1,
            monthName = monthNameOf(month),
            totalVisits = totalVisits,
            visitsComparedToLast30Days = deltas.visitsComparedToLast30Days,
            visitsComparedToSameMonthLastYear = deltas.visitsComparedToSameMonthLastYear,
            visitsComparedToYearToDateAverage = deltas.visitsComparedToYearToDateAverage,
            days = daysStats,
            nights = nightsStats,
            percentDaysOccupied = occupancy.percentDaysOccupied,
            percentWeeksOccupied = occupancy.percentWeeksOccupied,
            guests = guestStats,
            drivingTime = drivingTime,
            drivingMoments = drivingMoments,
        )
    }

    private data class YearVisitStats(
        val totalVisits: Int,
        val comparedToLast12: Int,
        val avgMonthlyVisits: Double,
        val avgGroupSize: Double?,
        val avgNightsPerVisit: Double?,
        val occupancy: no.slomic.smarthytte.statistics.calculator.YearOccupancy,
        val nightsComparedToLast12: Int,
        val avgMonthlyNights: Double,
        val monthMostVisits: MonthCount?,
        val monthFewestVisits: MonthCount?,
        val longestStay: MonthStay?,
    )

    private fun computeYearVisitStats(
        year: Int,
        allReservations: List<Reservation>,
        yearReservations: List<Reservation>,
    ): YearVisitStats {
        val totalVisits = yearReservations.size
        val comparedToLast12 = allReservations.diffVisitsCurrentYearWithLast12Months(year, totalVisits)
        val avgMonthlyVisits = (totalVisits.toDouble() / MONTHS_PER_YEAR).round1()
        val avgGroupSize =
            yearReservations
                .map { it.guestIds.size }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.round1()
                ?.takeIf { it > 0 }
        val avgNightsPerVisit =
            yearReservations
                .map { it.durationNights }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.round1()
                ?.takeIf { it > 0 }
        val occupancy = computeYearOccupancy(year, allReservations)
        val jan1 = firstDayOfYear(year)
        val nightsComparedToLast12 =
            occupancy.totalNights -
                allReservations.countOccupiedNightsInWindow(
                    firstDayOfYearBefore(year),
                    jan1,
                )
        val avgMonthlyNights = (occupancy.totalNights.toDouble() / MONTHS_PER_YEAR).round1()
        val countsByMonth = yearReservations.countByMonth()
        val monthMostVisits =
            countsByMonth
                .maxByOrNull { it.value }
                ?.takeIf { it.value > 0 }
                ?.let { MonthCount(it.key.ordinal + 1, monthNameOf(it.key), visitCount = it.value) }
        val monthFewestVisits =
            countsByMonth
                .filter { it.value > 0 }
                .minByOrNull { it.value }
                ?.let { MonthCount(it.key.ordinal + 1, monthNameOf(it.key), visitCount = it.value) }
        val longestStay =
            yearReservations
                .findMonthWithLongestStay()
                ?.let { (month, nights) -> MonthStay(month.ordinal + 1, monthNameOf(month), totalNights = nights) }
        return YearVisitStats(
            totalVisits,
            comparedToLast12,
            avgMonthlyVisits,
            avgGroupSize,
            avgNightsPerVisit,
            occupancy,
            nightsComparedToLast12,
            avgMonthlyNights,
            monthMostVisits,
            monthFewestVisits,
            longestStay,
        )
    }

    private data class EvStats(
        val totalKm: Double?,
        val totalKwh: Double?,
        val avgKwhPer100km: Double?,
        val totalRegeneratedKwh: Double?,
    )

    private fun computeEvStats(yearReservations: List<Reservation>): EvStats {
        val allCabinTrips = yearReservations.flatMap { it.toCabinVehicleTrips + it.fromCabinVehicleTrips }
        val totalKm = allCabinTrips.sumOf { it.distance }.takeIf { it > 0 }?.round1()
        val totalKwh =
            allCabinTrips
                .sumOf { it.averageEnergyConsumption * it.distance / KWH_PER_100KM_FACTOR }
                .takeIf { it > 0 }
                ?.round1()
        val avgKwhPer100km =
            allCabinTrips
                .takeIf { it.isNotEmpty() }
                ?.let { trips ->
                    val totalDist = trips.sumOf { it.distance }
                    if (totalDist > 0) {
                        (trips.sumOf { it.averageEnergyConsumption * it.distance } / totalDist).round1()
                    } else {
                        null
                    }
                }
        val totalRegeneratedKwh = allCabinTrips.sumOf { it.energyRegenerated }.takeIf { it > 0 }?.round1()
        return EvStats(totalKm, totalKwh, avgKwhPer100km, totalRegeneratedKwh)
    }
}
