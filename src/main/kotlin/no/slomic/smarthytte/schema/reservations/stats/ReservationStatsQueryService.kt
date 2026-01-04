package no.slomic.smarthytte.schema.reservations.stats

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Query
import kotlinx.datetime.Month
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.countByMonth
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
        val reservations = reservationRepository.allReservations(sortByLatestReservation = true)
        val guestsById = guestRepository.allGuests().associateBy { it.id }
        val allTrips = vehicleTripRepository.allVehicleTrips()
        val cabinTrips = CabinVehicleTripList(allTrips).cabinTrips

        // Group by calendar year using reservation start date
        val byYear: Map<Int, List<Reservation>> =
            reservations.groupBy { it.startDate.year }

        val targetYears: Set<Int> = years?.toSet() ?: byYear.keys

        return targetYears.sorted().map { year ->
            val yearReservations = byYear[year].orEmpty()

            // Build counts by month and compute monthly stats
            val countsByMonth: Map<Month, Int> = yearReservations.countByMonth()
            val monthStats = buildMonthStats(
                MonthStatsContext(
                    year = year,
                    yearReservations = yearReservations,
                    allReservations = reservations,
                    countsByMonth = countsByMonth,
                    guestsById = guestsById,
                    cabinTrips = cabinTrips,
                ),
            )

            buildYearStats(
                YearStatsContext(
                    year = year,
                    yearReservations = yearReservations,
                    allReservations = reservations,
                    countsByMonth = countsByMonth,
                    guestsById = guestsById,
                    monthStats = monthStats,
                    cabinTrips = cabinTrips,
                    byYear = byYear,
                ),
            )
        }
    }
}
