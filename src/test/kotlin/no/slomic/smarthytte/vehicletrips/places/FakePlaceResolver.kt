package no.slomic.smarthytte.vehicletrips.places

import no.slomic.smarthytte.vehicletrips.GeoPoint

/** Hand-written fake: answers from [places], throws [PlaceLookupException] for the points in [failing]. */
class FakePlaceResolver(
    private val places: Map<GeoPoint, Place> = emptyMap(),
    var failing: Set<GeoPoint> = emptySet(),
) : PlaceResolver {
    val resolved = mutableListOf<GeoPoint>()

    override suspend fun resolve(point: GeoPoint): Place {
        resolved += point
        if (point in failing) throw PlaceLookupException("Kartverket place names lookup failed: status=503")
        return places[point] ?: Place(city = null, address = null)
    }
}
