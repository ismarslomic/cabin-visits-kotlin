package no.slomic.smarthytte.vehicletrips

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
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
