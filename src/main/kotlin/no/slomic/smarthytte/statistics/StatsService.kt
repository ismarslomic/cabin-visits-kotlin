package no.slomic.smarthytte.statistics

import kotlinx.datetime.Month
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.firstDayOfYear
import no.slomic.smarthytte.common.firstDayOfYearAfter
import no.slomic.smarthytte.common.monthNameOf
import no.slomic.smarthytte.common.osloDateNow
import no.slomic.smarthytte.common.round1
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.countByMonth
import no.slomic.smarthytte.reservations.daysByGuest
import no.slomic.smarthytte.reservations.visitsByGuest
import no.slomic.smarthytte.statistics.calculator.MonthDates
import no.slomic.smarthytte.statistics.calculator.calculateMonthDrivingDistanceStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthDrivingMomentStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthDrivingTimeStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthlyDaysStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthlyGuestStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthlyNightsStats
import no.slomic.smarthytte.statistics.calculator.calculateMonthlyVisitDeltas
import no.slomic.smarthytte.statistics.calculator.calculateYearDaysStats
import no.slomic.smarthytte.statistics.calculator.calculateYearDrivingDistanceStats
import no.slomic.smarthytte.statistics.calculator.calculateYearDrivingMomentStats
import no.slomic.smarthytte.statistics.calculator.calculateYearDrivingTimeStats
import no.slomic.smarthytte.statistics.calculator.calculateYearNightsStats
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
import no.slomic.smarthytte.statistics.model.MonthStats
import no.slomic.smarthytte.statistics.model.MonthVisitsStats
import no.slomic.smarthytte.statistics.model.MonthlyVisitCount
import no.slomic.smarthytte.statistics.model.NextReservationInfo
import no.slomic.smarthytte.statistics.model.OccupancyStats
import no.slomic.smarthytte.statistics.model.YearEvStats
import no.slomic.smarthytte.statistics.model.YearStats
import no.slomic.smarthytte.statistics.model.YearVisitsStats

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

        val jan1 = firstDayOfYear(year)
        val jan1Next = firstDayOfYearAfter(year)

        val visitStats = computeYearVisitStats(year, allReservations, yearReservations)
        val daysStats = calculateYearDaysStats(year, allReservations, yearReservations)
        val nightsStats = calculateYearNightsStats(year, allReservations, yearReservations)
        val occupancy = computeYearOccupancy(year, allReservations)
        val guestStats = computeYearGuestStats(year, yearReservations, guestsById, byYear)
        val drivingDistance = calculateYearDrivingDistanceStats(jan1, jan1Next, allReservations)
        val ev = computeYearEvStats(yearReservations)

        val drivingTime =
            calculateYearDrivingTimeStats(jan1, jan1Next, allReservations)
                .takeIf { it.avgToCabinMinutes != null || it.avgFromCabinMinutes != null }
        val drivingMoments =
            calculateYearDrivingMomentStats(jan1, jan1Next, allReservations)
                .takeIf { it.avgDepartureHomeMinutes != null || it.avgDepartureCabinMinutes != null }

        val months =
            Month.entries.map { month ->
                buildMonthStats(year, month, allReservations, byYear, guestsById)
            }

        return YearStats(
            year = year,
            visits = visitStats,
            days = daysStats,
            nights = nightsStats,
            occupancy = OccupancyStats(
                dayOccupancy = occupancy.dayOccupancy,
                weekOccupancy = occupancy.weekOccupancy,
                monthOccupancy = occupancy.monthOccupancy,
            ),
            topGuestByDays = guestStats.topGuestByDays,
            newGuests = guestStats.newGuests,
            guests = guestStats.allGuestsSorted,
            drivingDistance = drivingDistance,
            ev = ev,
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
        val daysByGuest = allReservations.daysByGuest(jan1, jan1Next)

        val rankings =
            (visitsByGuest.keys + daysByGuest.keys).toSet().mapNotNull { guestId ->
                val guest = guestsById[guestId] ?: return@mapNotNull null
                GuestRanking(
                    guestId = guestId,
                    firstName = guest.firstName,
                    lastName = guest.lastName,
                    totalVisits = visitsByGuest[guestId] ?: 0,
                    totalDays = daysByGuest[guestId] ?: 0,
                )
            }

        val topByVisits = rankings.sortedByDescending { it.totalVisits }.take(TOP_GUESTS_LIMIT)
        val topByDays = rankings.sortedByDescending { it.totalDays }.take(TOP_GUESTS_LIMIT)

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

        return GuestStats(
            topGuestsByVisits = topByVisits,
            topGuestsByDays = topByDays,
            genderDistribution = genderDist,
            ageGroups = ageGroups,
        )
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
        val daysStats = calculateMonthlyDaysStats(allReservations, dates)
        val nightsStats = calculateMonthlyNightsStats(allReservations, dates)
        val occupancy = computeMonthOccupancy(allReservations, dates)

        val guestStats = calculateMonthlyGuestStats(year, guestsById, dates, allReservations)

        val monthFrom = dates.firstOfMonth
        val monthTo = dates.firstOfNextMonth
        val drivingDistance = calculateMonthDrivingDistanceStats(monthFrom, monthTo, allReservations)
        val drivingTime =
            calculateMonthDrivingTimeStats(monthFrom, monthTo, allReservations)
                .takeIf { it.avgToCabinMinutes != null || it.avgFromCabinMinutes != null }
        val drivingMoments =
            calculateMonthDrivingMomentStats(monthFrom, monthTo, allReservations)
                .takeIf { it.avgDepartureHomeMinutes != null || it.avgDepartureCabinMinutes != null }

        return MonthStats(
            year = year,
            monthNumber = month.ordinal + 1,
            monthName = monthNameOf(month),
            visits = MonthVisitsStats(
                totalVisits = totalVisits,
                comparedToPreviousMonth = deltas.comparedToPreviousMonth,
                comparedToSameMonthLastYear = deltas.comparedToSameMonthLastYear,
                comparedToYearToDateAverage = deltas.comparedToYearToDateAverage,
            ),
            days = daysStats,
            nights = nightsStats,
            occupancy = OccupancyStats(
                dayOccupancy = occupancy.percentDaysOccupied,
                weekOccupancy = occupancy.percentWeeksOccupied,
            ),
            guests = guestStats,
            drivingDistance = drivingDistance,
            drivingTime = drivingTime,
            drivingMoments = drivingMoments,
        )
    }

    private fun computeYearVisitStats(
        year: Int,
        allReservations: List<Reservation>,
        yearReservations: List<Reservation>,
    ): YearVisitsStats {
        val totalVisits = yearReservations.size
        val prevYearVisits = allReservations.count { it.startDate.year == year - 1 }
        val avgMonthlyVisits = (totalVisits.toDouble() / MONTHS_PER_YEAR).round1()
        val avgGroupSize =
            yearReservations
                .map { it.guestIds.size }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.round1()
                ?.takeIf { it > 0 }
        val countsByMonth = yearReservations.countByMonth()
        val monthMostVisits =
            countsByMonth
                .maxByOrNull { it.value }
                ?.takeIf { it.value > 0 }
                ?.let { MonthlyVisitCount(it.key.ordinal + 1, monthNameOf(it.key), visitCount = it.value) }
        val monthFewestVisits =
            countsByMonth
                .filter { it.value > 0 }
                .minByOrNull { it.value }
                ?.let { MonthlyVisitCount(it.key.ordinal + 1, monthNameOf(it.key), visitCount = it.value) }
        return YearVisitsStats(
            totalVisits = totalVisits,
            comparedToPreviousYear = totalVisits - prevYearVisits,
            avgMonthlyVisits = avgMonthlyVisits,
            avgGroupSize = avgGroupSize,
            monthMostVisits = monthMostVisits,
            monthFewestVisits = monthFewestVisits,
        )
    }

    private fun computeYearEvStats(yearReservations: List<Reservation>): YearEvStats? {
        val allCabinTrips = yearReservations.flatMap { it.toCabinVehicleTrips + it.fromCabinVehicleTrips }
        if (allCabinTrips.isEmpty()) return null
        val totalKwh =
            allCabinTrips
                .sumOf { it.averageEnergyConsumption * it.distance / KWH_PER_100KM_FACTOR }
                .takeIf { it > 0 }
                ?.round1()
        val avgKwhPer100km =
            allCabinTrips
                .let { trips ->
                    val totalDist = trips.sumOf { it.distance }
                    if (totalDist > 0) {
                        (trips.sumOf { it.averageEnergyConsumption * it.distance } / totalDist).round1()
                    } else {
                        null
                    }
                }
        val totalRegeneratedKwh = allCabinTrips.sumOf { it.energyRegenerated }.takeIf { it > 0 }?.round1()
        return YearEvStats(
            totalEnergyConsumedKwh = totalKwh,
            avgEnergyConsumptionKwhPer100km = avgKwhPer100km,
            totalEnergyRegeneratedKwh = totalRegeneratedKwh,
        )
    }
}
