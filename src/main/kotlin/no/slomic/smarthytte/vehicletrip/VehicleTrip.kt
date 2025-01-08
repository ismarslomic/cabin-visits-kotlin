package no.slomic.smarthytte.vehicletrip

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import no.slomic.smarthytte.common.StringToDoubleSerde
import no.slomic.smarthytte.common.StringToLocalDateSerde
import no.slomic.smarthytte.common.StringToLocalTimeSerde
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Serializable
data class VehicleTrip(
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
    val tripDuration: Duration
        get() = parseDuration(tripDurationFormatted)

    private fun parseDuration(durationString: String): Duration {
        val parts = durationString.split(":").map { it.toIntOrNull() ?: 0 }
        require(parts.size == 2) { "Invalid duration format. Expected format: HH:MM" }
        val hours = parts[0]
        val minutes = parts[1]
        return hours.hours + minutes.minutes
    }
}
