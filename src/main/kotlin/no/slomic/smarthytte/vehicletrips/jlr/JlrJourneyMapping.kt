package no.slomic.smarthytte.vehicletrips.jlr

import no.slomic.smarthytte.vehicletrips.DataQuality
import no.slomic.smarthytte.vehicletrips.GeoPoint
import no.slomic.smarthytte.vehicletrips.TripPlaces
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import no.slomic.smarthytte.vehicletrips.dataQuality
import kotlin.time.Duration.Companion.seconds

// The JLR response has no unit for the duration, it is seconds. `duration` itself is a Duration (stored as
// nanoseconds), so nothing is converted and the unit is only a label that nothing reads. The archive rows say "min".
private const val DURATION_UNIT = "sec"

/**
 * Maps a journey to a new trip. The id is the end time in epoch milliseconds, the same scheme as the archive rows, so
 * the links in `reservation_vehicle_trip` look alike. Energy is unknown, so it is null.
 *
 * Distance and speed are stored as received, with the unit strings of the response ("km" and "km/h"). Nothing reads
 * these labels, so they are not checked.
 *
 * [places] is null when the place lookup failed. The trip is complete when it has a start and an end city, an address
 * is for display only.
 */
fun JlrJourneyResponse.toVehicleTrip(places: TripPlaces?): VehicleTrip = VehicleTrip(
    averageEnergyConsumption = null,
    averageEnergyConsumptionUnit = null,
    averageSpeed = averageSpeed.value,
    distance = distance.value,
    distanceUnit = distance.unit,
    duration = duration.seconds,
    durationUnit = DURATION_UNIT,
    endAddress = places?.endAddress,
    endCity = places?.endCity,
    endTime = endTime,
    energyRegenerated = null,
    energyRegeneratedUnit = null,
    id = endTime.toEpochMilliseconds().toString(),
    speedUnit = averageSpeed.unit,
    startAddress = places?.startAddress,
    startCity = places?.startCity,
    startTime = startTime,
    totalDistance = distance.value,
    dataQuality = places?.dataQuality() ?: DataQuality.PLACE_MISSING,
    jlrJourneyId = journeyId,
    startPoint = GeoPoint(startLocation.latitude, startLocation.longitude),
    endPoint = GeoPoint(endLocation.latitude, endLocation.longitude),
)

fun JlrLocationResponse.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
