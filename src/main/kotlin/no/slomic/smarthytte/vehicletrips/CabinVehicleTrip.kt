package no.slomic.smarthytte.vehicletrips

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
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
 * Extracts a list of total driving durations in minutes for all trips _to the cabin_
 * that started within the specified [year].
 *
 * @param year The calendar year to filter trips by their start date.
 * @return A list of integers representing the total duration in minutes for each qualifying trip.
 */
fun List<CabinVehicleTrip>.toCabinDurations(year: Int): List<Int> = this.filter { it.toCabinStartDate?.year == year }
    .mapNotNull { it.toCabinTrips.totalDurationMinutes() }

/**
 * Extracts a list of total driving durations in minutes for all trips _to the cabin_
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
 * Extracts a list of total driving durations in minutes for all trips _from the cabin_
 * that started within the specified [year].
 *
 * @param year The calendar year to filter trips by their start date.
 * @return A list of integers representing the total duration in minutes for each qualifying trip.
 */
fun List<CabinVehicleTrip>.fromCabinDurations(year: Int): List<Int> =
    this.filter { it.fromCabinStartDate?.year == year }
        .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }

/**
 * Extracts a list of total driving durations in minutes for all trips _from the cabin_
 * that started within the specified [year] and [month].
 *
 * @param year The calendar year to filter trips by their start date.
 * @param month The month to filter trips by their start date.
 * @return A list of integers representing the total duration in minutes for each qualifying trip.
 */
fun List<CabinVehicleTrip>.fromCabinDurations(year: Int, month: Month): List<Int> =
    this.filter { it.fromCabinStartDate?.year == year && it.fromCabinStartDate?.month == month }
        .mapNotNull { it.fromCabinTrips.totalDurationMinutes() }
