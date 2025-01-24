package no.slomic.smarthytte.vehicletrips

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.common.StringToDoubleSerde
import no.slomic.smarthytte.common.StringToLocalDateSerde
import no.slomic.smarthytte.common.StringToLocalTimeSerde
import no.slomic.smarthytte.common.osloTimeZone
import no.slomic.smarthytte.common.toInstant
import no.slomic.smarthytte.common.toUtcDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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

/**
 * Data model representing JSON response from the Jaguar REST API
 */
@Serializable
data class VehicleTripExternal(
    @Serializable(with = StringToDoubleSerde::class)
    val averageEnergyConsumption: Double,
    val averageEnergyConsumptionUnit: String,
    val averagePHEVFuelConsumption: String,
    val distanceUnit: String,
    val durationUnit: String,
    val efficiencyUnit: String,
    val endAddress: String,
    val endCity: String,
    @Serializable(with = StringToLocalDateSerde::class)
    val endDateInUserFormat: LocalDate,
    @Serializable(with = StringToLocalTimeSerde::class)
    val endTimeInUserFormat: LocalTime,
    @Serializable(with = StringToDoubleSerde::class)
    val energyRegenerated: Double,
    val energyRegeneratedUnit: String,
    @Serializable(with = StringToDoubleSerde::class)
    val evDistance: Double,
    val journeyId: Long,
    val speedUnit: String,
    val startAddress: String,
    val startCity: String,
    @Serializable(with = StringToLocalDateSerde::class)
    val startDateInUserFormat: LocalDate,
    @Serializable(with = StringToLocalTimeSerde::class)
    val startTimeInUserFormat: LocalTime,
    @Serializable(with = StringToDoubleSerde::class)
    val totalDistance: Double,
    @Serializable(with = StringToDoubleSerde::class)
    val tripAverageSpeed: Double,
    @Serializable(with = StringToDoubleSerde::class)
    val tripDistance: Double,
    val tripDurationFormatted: String,
    @Serializable(with = StringToDoubleSerde::class)
    val tripEfficiency: Double,
) {
    fun toInternal() = VehicleTrip(
        averageEnergyConsumption = averageEnergyConsumption,
        averageEnergyConsumptionUnit = averageEnergyConsumptionUnit,
        averageSpeed = tripAverageSpeed,
        distance = tripDistance,
        distanceUnit = distanceUnit,
        duration = tripDuration,
        durationUnit = durationUnit,
        endAddress = endAddress,
        endCity = endCity,
        endTime = toInstant(date = endDateInUserFormat, time = endTimeInUserFormat, timeZone = osloTimeZone),
        energyRegenerated = energyRegenerated,
        energyRegeneratedUnit = energyRegeneratedUnit,
        id = journeyId.toString(),
        speedUnit = speedUnit,
        startAddress = startAddress,
        startCity = startCity,
        startTime = toInstant(date = startDateInUserFormat, time = startTimeInUserFormat, timeZone = osloTimeZone),
        totalDistance = totalDistance,
    )

    private val tripDuration: Duration
        get() = parseDuration(tripDurationFormatted)

    private fun parseDuration(durationString: String): Duration {
        val parts = durationString.split(":").map { it.toIntOrNull() ?: 0 }
        require(parts.size == 2) { "Invalid duration format. Expected format: HH:MM" }
        val hours = parts[0]
        val minutes = parts[1]
        return hours.hours + minutes.minutes
    }
}
