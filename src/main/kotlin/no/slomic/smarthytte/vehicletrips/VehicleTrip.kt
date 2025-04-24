package no.slomic.smarthytte.vehicletrips

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.toUtcDate
import kotlin.time.Duration

data class VehicleTrip(
    val averageEnergyConsumption: Double,
    val averageEnergyConsumptionUnit: String,
    val averageSpeed: Double,
    val distance: Double,
    val distanceUnit: String,
    val duration: Duration,
    val durationUnit: String,
    val endAddress: String,
    val endCity: String,
    val endTime: Instant,
    val energyRegenerated: Double,
    val energyRegeneratedUnit: String,
    val id: String,
    val speedUnit: String,
    val startAddress: String,
    val startCity: String,
    val startTime: Instant,
    val totalDistance: Double,
    /**
     * List of city names with extra stops between start and end city
     */
    val extraStops: List<String> = listOf(),
    val notionId: String? = null,
) {
    fun hasArrivedCabinAt(utcDate: LocalDate): Boolean = endDate == utcDate && endCity == ULLSAK_CITY_NAME

    fun hasDepartedCabinAt(utcDate: LocalDate): Boolean = startDate == utcDate && startCity == ULLSAK_CITY_NAME

    private val startDate: LocalDate
        get() = startTime.toUtcDate()

    private val endDate: LocalDate
        get() = endTime.toUtcDate()

    override fun toString(): String {
        val extraStopCityNames =
            if (extraStops.isEmpty()) {
                "none"
            } else {
                extraStops.joinToString(",")
            }

        val str = "$startCity ($startTime) " +
            "- $endCity ($endTime) " +
            "$averageSpeed kmh " +
            "$totalDistance km " +
            "$duration " +
            "(extra stops $extraStopCityNames)"

        return str
    }
}

data class OsloUllsakVehicleTrip(
    val fromOsloToUllsak: VehicleTrip,
    val fromUllsakToOslo: VehicleTrip?,
    val lastIndex: Int,
)
