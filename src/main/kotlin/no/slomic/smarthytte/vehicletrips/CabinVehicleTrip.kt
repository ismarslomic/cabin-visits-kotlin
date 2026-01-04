package no.slomic.smarthytte.vehicletrips

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.common.averageOrNullInt
import no.slomic.smarthytte.common.minutesOfDay
import no.slomic.smarthytte.common.osloTimeZone
import no.slomic.smarthytte.common.toUtcDate

const val HOME_CITY_NAME = "Oslo"
const val CABIN_CITY_NAME = "Ullsåk"

data class CabinVehicleTrip(
    val toCabinTrips: List<VehicleTrip>,
    val atCabinTrips: List<VehicleTrip>,
    val fromCabinTrips: List<VehicleTrip>,
) {
    val toCabinStartTimestamp: Instant? get() = toCabinTrips.firstOrNull()?.startTime
    val toCabinStartDate: LocalDate? get() = toCabinStartTimestamp?.toUtcDate()
    val toCabinEndTimestamp: Instant? get() = toCabinTrips.lastOrNull()?.endTime
    val toCabinEndDate: LocalDate? get() = toCabinEndTimestamp?.toUtcDate()
    val fromCabinStartTimestamp: Instant? get() = fromCabinTrips.firstOrNull()?.startTime
    val fromCabinStartDate: LocalDate? get() = fromCabinStartTimestamp?.toUtcDate()
    val fromCabinEndTimestamp: Instant? get() = fromCabinTrips.lastOrNull()?.endTime

    val toCabinTripId: String?
        get() =
            when (toCabinTrips.size) {
                0 -> {
                    null
                }

                1 -> {
                    toCabinTrips.first().id
                }

                else -> {
                    "${toCabinTrips.first().id}-${toCabinTrips.last().id}"
                }
            }

    val fromCabinTripId: String?
        get() =
            when (fromCabinTrips.size) {
                0 -> {
                    null
                }

                1 -> {
                    fromCabinTrips.first().id
                }

                else -> {
                    "${fromCabinTrips.first().id}-${fromCabinTrips.last().id}"
                }
            }

    fun hasArrivedCabinAt(utcDate: LocalDate): Boolean =
        toCabinEndDate != null && (toCabinEndDate == utcDate || toCabinEndDate == utcDate.plus(1, DateTimeUnit.DAY))

    fun hasDepartedCabinAt(utcDate: LocalDate): Boolean = fromCabinStartDate == utcDate
}

/**
 * Calculates the total duration in minutes for a list of vehicle trips.
 * If the list is empty, `null` is returned.
 *
 * @return The total duration in minutes as an integer, or `null` if the list is empty.
 */
fun List<VehicleTrip>.totalDurationMinutes(): Int? = this.map { it.duration }
    .let {
        if (it.isEmpty()) {
            null
        } else {
            it.reduce { acc, d -> acc + d }.inWholeMinutes.toInt()
        }
    }

/**
 * Calculates the total driving duration in minutes for all trips _to the cabin_
 * that started within the specified [year].
 *
 * @param year The calendar year to filter trips by their start date.
 * @return A list of integers representing the total duration in minutes for each qualifying trip.
 */
fun List<CabinVehicleTrip>.toCabinDurations(year: Int): List<Int> = this.filter { it.toCabinStartDate?.year == year }
    .mapNotNull { it.toCabinTrips.totalDurationMinutes() }

/**
 * Calculates the total driving duration in minutes for all trips _to the cabin_
 * that started within the specified [year] and [month].
 *
 * @param year The calendar year to filter trips by their start date.
 * @param month The month to filter trips by their start date.
 * @return A list of integers representing the total duration in minutes for each qualifying trip.
 */
fun List<CabinVehicleTrip>.toCabinDurations(year: Int, month: Month): List<Int> =
    this.filter { it.toCabinStartDate?.year == year && it.toCabinStartDate?.month == month }
        .mapNotNull { it.toCabinTrips.totalDurationMinutes() }

/**
 * Calculates the total driving duration in minutes for all trips _from the cabin_
 * that started within the specified [year].
 *
 * @param year The calendar year to filter trips by their start date.
 * @return A list of integers representing the total duration in minutes for each qualifying trip.
 */
fun List<CabinVehicleTrip>.fromCabinDurations(year: Int): List<Int> =
    this.filter { it.fromCabinStartDate?.year == year }
        .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }

/**
 * Calculates the total driving duration in minutes for all trips _from the cabin_
 * that started within the specified [year] and [month].
 *
 * @param year The calendar year to filter trips by their start date.
 * @param month The month to filter trips by their start date.
 * @return A list of integers representing the total duration in minutes for each qualifying trip.
 */
fun List<CabinVehicleTrip>.fromCabinDurations(year: Int, month: Month): List<Int> =
    this.filter { it.fromCabinStartDate?.year == year && it.fromCabinStartDate?.month == month }
        .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }

/**
 * Calculates the average departure time from home in minutes since midnight for all trips to the cabin
 * that occurred within the specified [year] and optionally [month].
 *
 * The departure time is represented as minutes since midnight in the Oslo time zone.
 *
 * @param year The calendar year to filter trips by.
 * @param month The optional month to filter trips by.
 * @return The average departure minutes since midnight for all qualifying trips, or null if no trips found.
 */
fun List<CabinVehicleTrip>.avgDepartureHomeMinutes(year: Int, month: Month? = null): Int? =
    this.averageMoment(year, month) { it.toCabinStartTimestamp }

/**
 * Calculates the average arrival time at the cabin in minutes since midnight for all trips to the cabin
 * that occurred within the specified [year] and optionally [month].
 *
 * The arrival time is represented as minutes since midnight in the Oslo time zone.
 *
 * @param year The calendar year to filter trips by.
 * @param month The optional month to filter trips by.
 * @return The average arrival minutes since midnight for all qualifying trips, or null if no trips found.
 */
fun List<CabinVehicleTrip>.avgArrivalCabinMinutes(year: Int, month: Month? = null): Int? =
    this.averageMoment(year, month) { it.toCabinEndTimestamp }

/**
 * Calculates the average departure time from the cabin in minutes since midnight for all trips from the cabin
 * that occurred within the specified [year] and optionally [month].
 *
 * The departure time is represented as minutes since midnight in the Oslo time zone.
 *
 * @param year The calendar year to filter trips by.
 * @param month The optional month to filter trips by.
 * @return The average departure minutes since midnight for all qualifying trips, or null if no trips found.
 */
fun List<CabinVehicleTrip>.avgDepartureCabinMinutes(year: Int, month: Month? = null): Int? =
    this.averageMoment(year, month) { it.fromCabinStartTimestamp }

/**
 * Calculates the average arrival time at home in minutes since midnight for all trips from the cabin
 * that occurred within the specified [year] and optionally [month].
 *
 * The arrival time is represented as minutes since midnight in the Oslo time zone.
 *
 * @param year The calendar year to filter trips by.
 * @param month The optional month to filter trips by.
 * @return The average arrival minutes since midnight for all qualifying trips, or null if no trips found.
 */
fun List<CabinVehicleTrip>.avgArrivalHomeMinutes(year: Int, month: Month? = null): Int? =
    this.averageMoment(year, month) { it.fromCabinEndTimestamp }

/**
 * Calculates the average time-of-day moment in minutes since midnight for cabin vehicle trips
 * based on a selected timestamp, filtered by [year] and optionally [month].
 *
 * The filtering is performed based on the date of the selected timestamp in the Oslo time zone.
 *
 * @param year The calendar year to filter the moments by.
 * @param month The optional month to filter the moments by.
 * @param timestampSelector A function that selects the relevant [Instant] from a [CabinVehicleTrip].
 * @return The average minutes since midnight for qualifying trips' selected moments, or null if no trips found.
 */
private fun List<CabinVehicleTrip>.averageMoment(
    year: Int,
    month: Month?,
    timestampSelector: (CabinVehicleTrip) -> Instant?,
): Int? = this.mapNotNull { trip ->
    timestampSelector(trip)?.toLocalDateTime(osloTimeZone)?.let { dt ->
        if (dt.date.year == year && (month == null || dt.date.month == month)) {
            dt.time.minutesOfDay()
        } else {
            null
        }
    }
}.averageOrNullInt()
