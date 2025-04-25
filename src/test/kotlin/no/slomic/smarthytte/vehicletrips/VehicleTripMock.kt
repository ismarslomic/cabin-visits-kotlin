package no.slomic.smarthytte.vehicletrips

import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

fun createTrip(
    id: String = UUID.randomUUID().toString(),
    startCity: String,
    endCity: String,
    startTime: String,
    endTime: String,
): VehicleTrip {
    val startTimestamp: Instant = Instant.parse(startTime)
    val endTimestamp: Instant = Instant.parse(endTime)

    return VehicleTrip(
        averageEnergyConsumption = 0.0,
        averageEnergyConsumptionUnit = "",
        averageSpeed = 0.0,
        distance = 0.0,
        distanceUnit = "",
        duration = 5.minutes,
        durationUnit = "",
        endAddress = "",
        endCity = endCity,
        endTime = endTimestamp,
        energyRegenerated = 0.0,
        energyRegeneratedUnit = "",
        id = id,
        speedUnit = "",
        startAddress = "",
        startCity = startCity,
        startTime = startTimestamp,
        totalDistance = 0.0,
    )
}
