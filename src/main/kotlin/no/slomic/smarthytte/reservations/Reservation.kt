package no.slomic.smarthytte.reservations

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.datesUntil
import no.slomic.smarthytte.common.daysUntilSafe
import no.slomic.smarthytte.common.toUtcDate
import no.slomic.smarthytte.common.utcDateNow
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import kotlin.time.Duration
import kotlin.time.Instant

data class Reservation(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val guestIds: List<String>,
    val toCabinVehicleTrips: List<VehicleTrip> = emptyList(),
    val atCabinVehicleTrips: List<VehicleTrip> = emptyList(),
    val fromCabinVehicleTrips: List<VehicleTrip> = emptyList(),
    val summary: String? = null,
    val description: String? = null,
    val sourceCreatedTime: Instant? = null,
    val sourceUpdatedTime: Instant? = null,
    val notionId: String? = null,
    var checkIn: CheckIn? = null,
    var checkOut: CheckOut? = null,
) {
    val hasStarted: Boolean
        get() = startDate <= utcDateNow()

    val hasEnded: Boolean
        get() = endDate <= utcDateNow()

    val startDate: LocalDate
        get() = startTime.toUtcDate()

    val endDate: LocalDate
        get() = endTime.toUtcDate()

    val durationNights: Int
        get() = startDate.daysUntilSafe(endExclusive = endDate)

    // Calendar days of the full stay, including both arrival and departure day
    val durationDays: Int
        get() = durationNights + 1

    val toCabinDrivingDuration: Duration?
        get() = toCabinVehicleTrips
            .takeIf { it.isNotEmpty() }
            ?.map { it.duration }
            ?.reduce { acc, d -> acc + d }

    val fromCabinDrivingDuration: Duration?
        get() = fromCabinVehicleTrips
            .takeIf { it.isNotEmpty() }
            ?.map { it.duration }
            ?.reduce { acc, d -> acc + d }

    val toCabinDrivingDepartureTime: Instant?
        get() = toCabinVehicleTrips.firstOrNull()?.startTime

    val toCabinDrivingDepartureDate: LocalDate?
        get() = toCabinVehicleTrips.firstOrNull()?.startDate

    val toCabinDrivingArrivalTime: Instant?
        get() = toCabinVehicleTrips.lastOrNull()?.endTime

    val fromCabinDrivingDepartureTime: Instant?
        get() = fromCabinVehicleTrips.firstOrNull()?.startTime

    val fromCabinDrivingDepartureDate: LocalDate?
        get() = fromCabinVehicleTrips.firstOrNull()?.startDate

    val fromCabinDrivingArrivalTime: Instant?
        get() = fromCabinVehicleTrips.lastOrNull()?.endTime

    val toCabinDrivingDistanceKm: Double?
        get() = toCabinVehicleTrips.takeIf { it.isNotEmpty() }?.sumOf { it.distance }

    val fromCabinDrivingDistanceKm: Double?
        get() = fromCabinVehicleTrips.takeIf { it.isNotEmpty() }?.sumOf { it.distance }

    val toCabinAvgSpeedKmh: Double?
        get() = toCabinVehicleTrips.takeIf { it.isNotEmpty() }
            ?.let { trips -> trips.sumOf { it.averageSpeed } / trips.size }

    val fromCabinAvgSpeedKmh: Double?
        get() = fromCabinVehicleTrips.takeIf { it.isNotEmpty() }
            ?.let { trips -> trips.sumOf { it.averageSpeed } / trips.size }

    fun nightsInPeriod(periodStart: LocalDate, periodEndExclusive: LocalDate): Int {
        val overlapStart = maxOf(startDate, periodStart)
        val overlapEndExclusive = minOf(endDate, periodEndExclusive)
        return if (overlapStart < overlapEndExclusive) overlapStart.daysUntilSafe(overlapEndExclusive) else 0
    }

    fun daysInPeriod(periodStart: LocalDate, periodEndExclusive: LocalDate): Int {
        val endDateInclusive = endDate.plus(DatePeriod(days = 1))
        val overlapStart = maxOf(startDate, periodStart)
        val overlapEndExclusive = minOf(endDateInclusive, periodEndExclusive)
        return if (overlapStart < overlapEndExclusive) overlapStart.daysUntilSafe(overlapEndExclusive) else 0
    }
}

fun List<Reservation>.countByMonth(): Map<Month, Int> =
    Month.entries.associateWith { month -> count { it.startDate.month == month } }

fun List<Reservation>.countOccupiedNightsInWindow(startInclusive: LocalDate, endExclusive: LocalDate): Int {
    if (startInclusive >= endExclusive) return 0
    return this.asSequence().flatMap { r ->
        val start = maxOf(r.startDate, startInclusive)
        val endEx = minOf(r.endDate, endExclusive)
        if (start < endEx) start.datesUntil(endEx).toList() else emptyList()
    }.toSet().size
}

fun List<Reservation>.countOccupiedDaysInWindow(startInclusive: LocalDate, endExclusive: LocalDate): Int {
    if (startInclusive >= endExclusive) return 0
    return this.asSequence().flatMap { r ->
        val start = maxOf(r.startDate, startInclusive)
        val endEx = minOf(r.endDate.plus(DatePeriod(days = 1)), endExclusive)
        if (start < endEx) start.datesUntil(endEx).toList() else emptyList()
    }.toSet().size
}

fun List<Reservation>.visitsByGuest(): Map<String, Int> = this.flatMap { it.guestIds }.groupingBy { it }.eachCount()

fun List<Reservation>.daysByGuest(periodStart: LocalDate, periodEndExclusive: LocalDate): Map<String, Int> =
    this.flatMap { reservation ->
        val days = reservation.daysInPeriod(periodStart, periodEndExclusive)
        reservation.guestIds.map { it to days }
    }.groupingBy { (guestId, _) -> guestId }.fold(0) { acc, (_, days) -> acc + days }
