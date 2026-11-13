package no.slomic.smarthytte.reservations

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.common.SYSTEM_ACTOR
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
    val guests: List<ReservationGuest>,
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
    // Set by the repository when a stored reservation is read. Not part of equals/hashCode/copy (decision 34): never
    // copy a loaded reservation and write it back.
    var version: Short = 1
        internal set
    var createdTime: Instant = Instant.DISTANT_PAST
        internal set
    var createdBy: String = SYSTEM_ACTOR
        internal set
    var updatedTime: Instant? = null
        internal set
    var updatedBy: String? = null
        internal set

    /** The ids of all linked guests, whatever their own stay. */
    val guestIds: List<String>
        get() = guests.map { it.guestId }

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

    /**
     * The own stay of every guest: a guest's own dates, or the dates of the reservation where they have none. Sorted by
     * guest id, like [guests].
     */
    val stays: List<GuestStay>
        get() = guests.map {
            GuestStay(it.guestId, id, startDate = it.fromDate ?: startDate, endDate = it.toDate ?: endDate)
        }

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

    /**
     * [VisitStatus.UPCOMING] before the start date, [VisitStatus.ONGOING] while the end date is after [today], and
     * [VisitStatus.COMPLETED] from the end date itself on (UTC dates). The live statistics keep the check-out day as
     * occupied and use their own rule.
     */
    fun visitStatus(today: LocalDate): VisitStatus = visitStatusOf(startDate, endDate, today)

    fun daysInPeriod(periodStart: LocalDate, periodEndExclusive: LocalDate): Int =
        daysWithin(startDate, endDate, periodStart, periodEndExclusive)
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
