package no.slomic.smarthytte.vehicletrips.places

import no.slomic.smarthytte.vehicletrips.GeoPoint

/** Looks up the city and address of a point. */
interface PlaceResolver {
    /**
     * @return the place of [point]; a [Place] with null parts when nothing was found
     * @throws PlaceLookupException when the lookup itself failed, so the caller can try again later
     */
    suspend fun resolve(point: GeoPoint): Place
}

/** The place lookup failed (server error, network, unreadable answer). The message has no coordinates. */
class PlaceLookupException(message: String) : RuntimeException(message)
