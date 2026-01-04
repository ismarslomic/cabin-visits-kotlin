package no.slomic.smarthytte.vehicletrips

import kotlinx.datetime.Instant
import java.util.*

fun createTrip(
    startCity: String,
    endCity: String,
    startTime: Instant,
    endTime: Instant,
    id: String = UUID.randomUUID().toString(),
): VehicleTrip = VehicleTrip(
    averageEnergyConsumption = 0.0,
    averageEnergyConsumptionUnit = "",
    averageSpeed = 0.0,
    distance = 0.0,
    distanceUnit = "",
    duration = endTime - startTime,
    durationUnit = "",
    endAddress = "",
    endCity = endCity,
    endTime = endTime,
    energyRegenerated = 0.0,
    energyRegeneratedUnit = "",
    id = id,
    speedUnit = "",
    startAddress = "",
    startCity = startCity,
    startTime = startTime,
    totalDistance = 0.0,
)

fun createTrip(
    startCity: String,
    endCity: String,
    startTime: String,
    endTime: String,
    id: String = UUID.randomUUID().toString(),
): VehicleTrip = createTrip(
    startCity = startCity,
    endCity = endCity,
    startTime = Instant.parse(startTime),
    endTime = Instant.parse(endTime),
    id = id,
)
