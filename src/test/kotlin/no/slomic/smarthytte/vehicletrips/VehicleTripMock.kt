package no.slomic.smarthytte.vehicletrips

import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

fun createTrip(
    startCity: String?,
    endCity: String?,
    startTime: String,
    endTime: String,
    id: String = UUID.randomUUID().toString(),
    averageEnergyConsumption: Double? = 0.0,
    energyRegenerated: Double? = 0.0,
    dataQuality: DataQuality = DataQuality.COMPLETE,
    jlrJourneyId: String? = null,
    startPoint: GeoPoint? = null,
    endPoint: GeoPoint? = null,
): VehicleTrip {
    val startTimestamp: Instant = Instant.parse(startTime)
    val endTimestamp: Instant = Instant.parse(endTime)

    return VehicleTrip(
        averageEnergyConsumption = averageEnergyConsumption,
        averageEnergyConsumptionUnit = averageEnergyConsumption?.let { "" },
        averageSpeed = 0.0,
        distance = 0.0,
        distanceUnit = "",
        duration = 5.minutes,
        durationUnit = "",
        endAddress = "",
        endCity = endCity,
        endTime = endTimestamp,
        energyRegenerated = energyRegenerated,
        energyRegeneratedUnit = energyRegenerated?.let { "" },
        id = id,
        speedUnit = "",
        startAddress = "",
        startCity = startCity,
        startTime = startTimestamp,
        totalDistance = 0.0,
        dataQuality = dataQuality,
        jlrJourneyId = jlrJourneyId,
        startPoint = startPoint,
        endPoint = endPoint,
    )
}
