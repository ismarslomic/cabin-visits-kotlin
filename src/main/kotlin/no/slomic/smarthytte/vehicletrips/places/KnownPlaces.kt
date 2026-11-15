package no.slomic.smarthytte.vehicletrips.places

import no.slomic.smarthytte.checkinouts.CABIN_CITY_NAME
import no.slomic.smarthytte.checkinouts.HOME_CITY_NAME
import no.slomic.smarthytte.properties.PlaceProperties
import no.slomic.smarthytte.properties.PlacesProperties
import no.slomic.smarthytte.vehicletrips.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** A place the app knows, with the city and address to store for a point within [radiusMeters] of [point]. */
data class KnownPlace(val point: GeoPoint, val radiusMeters: Double, val place: Place)

/**
 * The home and the cabin. A trip that starts or ends within the radius of one of them gets the configured city and
 * address without a lookup: the GPS point drifts from trip to trip, and the check-in/out logic needs the same city
 * every time.
 */
class KnownPlaces(private val places: List<KnownPlace>) {
    /** The nearest known place that [point] is within the radius of, or null. */
    fun find(point: GeoPoint): Place? = places
        .map { it to distanceMeters(point, it.point) }
        .filter { (known, distance) -> distance <= known.radiusMeters }
        .minByOrNull { (_, distance) -> distance }
        ?.first
        ?.place
}

fun PlacesProperties.toKnownPlaces(): KnownPlaces = KnownPlaces(
    listOf(home.toKnownPlace(city = HOME_CITY_NAME), cabin.toKnownPlace(city = CABIN_CITY_NAME)),
)

private fun PlaceProperties.toKnownPlace(city: String) = KnownPlace(
    point = GeoPoint(latitude, longitude),
    radiusMeters = radiusMeters,
    place = Place(city = city, address = address),
)

/** Haversine distance. Exact enough for radii of a few hundred meters. */
private fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
    val fromLatitude = Math.toRadians(from.latitude)
    val toLatitude = Math.toRadians(to.latitude)
    val deltaLatitude = toLatitude - fromLatitude
    val deltaLongitude = Math.toRadians(to.longitude - from.longitude)
    val a = sin(deltaLatitude / 2).pow(2) + cos(fromLatitude) * cos(toLatitude) * sin(deltaLongitude / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}
