package no.slomic.smarthytte.vehicletrips.places

import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.issues.IssueCode
import no.slomic.smarthytte.issues.IssueReporter
import no.slomic.smarthytte.vehicletrips.DataQuality
import no.slomic.smarthytte.vehicletrips.GeoPoint
import no.slomic.smarthytte.vehicletrips.TripPlaces
import no.slomic.smarthytte.vehicletrips.VehicleTrip
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import no.slomic.smarthytte.vehicletrips.dataQuality

/** How many `PLACE_MISSING` trips a re-resolve gave a city, and how many are still without one. */
data class ReResolveResult(val healed: Int, val stillMissing: Int)

/**
 * Gives trips their start and end places. A lookup that fails does not fail the trip: the place stays empty, the
 * failure is reported (`PLACE_LOOKUP_FAILED`), and the trip is `PLACE_MISSING` until [reResolvePlaceMissing] finds a
 * city.
 */
class TripPlaceService(
    private val placeResolver: PlaceResolver,
    private val vehicleTripRepository: VehicleTripRepository,
    private val issueReporter: IssueReporter,
) {
    /**
     * The places of a trip from [start] to [end]. A side of [current] that has a city and an address is kept and not
     * looked up again. Reports `PLACE_MISSING` when the result has no start or no end city.
     */
    suspend fun resolvePlaces(
        tripId: String,
        start: GeoPoint,
        end: GeoPoint,
        current: TripPlaces = TripPlaces(null, null, null, null),
    ): TripPlaces {
        val startPlace = current.startPlace() ?: resolveOrNull(tripId, start)
        val endPlace = current.endPlace() ?: resolveOrNull(tripId, end)
        val places = TripPlaces(
            startCity = startPlace?.city ?: current.startCity,
            startAddress = startPlace?.address ?: current.startAddress,
            endCity = endPlace?.city ?: current.endCity,
            endAddress = endPlace?.address ?: current.endAddress,
        )
        when (places.dataQuality()) {
            DataQuality.PLACE_MISSING -> issueReporter.report(
                code = IssueCode.PLACE_MISSING,
                key = issueKey(IssueCode.PLACE_MISSING, tripId),
                message = "Trip $tripId has no ${missingSides(places)} city",
            )

            // Complete places: neither the missing city nor an earlier failed lookup is a problem any more
            DataQuality.COMPLETE -> listOf(IssueCode.PLACE_MISSING, IssueCode.PLACE_LOOKUP_FAILED).forEach {
                issueReporter.resolve(it, issueKey(it, tripId))
            }
        }
        return places
    }

    /** Looks up the places of every `PLACE_MISSING` trip again, and stores what was found. */
    suspend fun reResolvePlaceMissing(): ReResolveResult {
        var healed = 0
        var stillMissing = 0
        for (trip in vehicleTripRepository.findByDataQuality(DataQuality.PLACE_MISSING)) {
            when (reResolve(trip)) {
                DataQuality.COMPLETE -> healed++
                DataQuality.PLACE_MISSING -> stillMissing++
            }
        }
        return ReResolveResult(healed = healed, stillMissing = stillMissing)
    }

    private suspend fun reResolve(trip: VehicleTrip): DataQuality {
        val start = trip.startPoint
        val end = trip.endPoint
        // Trips without coordinates are archive rows, which are never PLACE_MISSING
        if (start == null || end == null) return DataQuality.PLACE_MISSING

        val current = TripPlaces(trip.startCity, trip.startAddress, trip.endCity, trip.endAddress)
        val places = resolvePlaces(tripId = trip.id, start = start, end = end, current = current)
        val quality = places.dataQuality()
        if (places != current || quality != trip.dataQuality) {
            vehicleTripRepository.updatePlaces(
                id = trip.id,
                places = places,
                quality = quality,
                actor = SYSTEM_ACTOR,
            )
        }
        return quality
    }

    private suspend fun resolveOrNull(tripId: String, point: GeoPoint): Place? = try {
        placeResolver.resolve(point)
    } catch (e: PlaceLookupException) {
        issueReporter.report(
            code = IssueCode.PLACE_LOOKUP_FAILED,
            key = issueKey(IssueCode.PLACE_LOOKUP_FAILED, tripId),
            message = "Place lookup failed for trip $tripId: ${e.message}",
            cause = e,
        )
        null
    }
}

private fun TripPlaces.startPlace(): Place? =
    if (startCity != null && startAddress != null) Place(startCity, startAddress) else null

private fun TripPlaces.endPlace(): Place? =
    if (endCity != null && endAddress != null) Place(endCity, endAddress) else null

private fun missingSides(places: TripPlaces): String = listOfNotNull(
    "start".takeIf { places.startCity == null },
    "end".takeIf { places.endCity == null },
).joinToString(" and ")

/** The dedupe key of a problem of one trip, e.g. `PLACE_MISSING:1765000000000`. */
private fun issueKey(code: IssueCode, tripId: String) = "$code:$tripId"
