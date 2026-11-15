package no.slomic.smarthytte.vehicletrips.places

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.vehicletrips.GeoPoint
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

/** Place name search by point, [OpenAPI spec](https://ws.geonorge.no/stedsnavn/v1/#/). */
private const val PLACE_NAMES_URL = "https://ws.geonorge.no/stedsnavn/v1/punkt"

/** Address search by point, [OpenAPI spec](https://ws.geonorge.no/adresser/v1/#/). */
private const val ADDRESSES_URL = "https://ws.geonorge.no/adresser/v1/punktsok"

/** WGS84 lat/lon, the coordinate system the JLR coordinates are in. */
private const val COORDINATE_SYSTEM = "4258"

/** Search radius for both lookups. The cabin is 155 to 159 m from its own GPS points, so 200 m is too tight. */
private const val SEARCH_RADIUS_METERS = 300

/**
 * All place names around a point on one page, as the village is not always among the nearest and the answer is not
 * sorted by distance. 300 m in central Oslo hold 42 names, so 40 per page would cut two off. The API accepts 500.
 */
private const val PLACE_NAMES_PER_PAGE = 500

/** The nearest address is all we use. */
private const val ADDRESSES_PER_PAGE = 1

/** 5 decimals are about 1 m, so GPS points that close share one cache entry. */
private const val CACHE_DECIMALS_FACTOR = 100_000.0

private val json = Json { ignoreUnknownKeys = true }

/**
 * Finds city and address with the free Kartverket APIs (© Kartverket, CC BY 4.0).
 *
 * City, first match wins:
 * 1. [KnownPlaces]: the point is within the radius of home or cabin. Configured city and address, no call.
 * 2. Village: the approved name of the nearest `Bygdelag (bygd)` from the place name search.
 * 3. Postal town of the nearest address from the address search.
 *
 * Address: for a known place the configured one, otherwise the nearest address (null if none). It does not affect the
 * city. Both APIs are called for every other point, because the address is stored even when a village is found.
 *
 * A found city is kept in memory by coordinates rounded to 5 decimals. A point with no city, or a failed lookup, is
 * not kept, so it is looked up again the next time.
 */
class KartverketPlaceResolver(private val httpClient: HttpClient, private val knownPlaces: KnownPlaces) :
    PlaceResolver {
    private val cache = ConcurrentHashMap<Pair<Long, Long>, Place>()

    override suspend fun resolve(point: GeoPoint): Place {
        val known = knownPlaces.find(point)
        val key = point.cacheKey()
        return known ?: cache[key] ?: lookUp(point).also { if (it.city != null) cache[key] = it }
    }

    private suspend fun lookUp(point: GeoPoint): Place {
        val placeNames = fetchBody(PLACE_NAMES_URL, "place names") {
            parameter("nord", point.latitude)
            parameter("ost", point.longitude)
            parameter("koordsys", COORDINATE_SYSTEM)
            parameter("radius", SEARCH_RADIUS_METERS)
            parameter("treffPerSide", PLACE_NAMES_PER_PAGE)
        }.decode<KartverketPlaceNamesResponse>("place names")
        val addresses = fetchBody(ADDRESSES_URL, "addresses") {
            parameter("lat", point.latitude)
            parameter("lon", point.longitude)
            parameter("utkoordsys", COORDINATE_SYSTEM)
            parameter("radius", SEARCH_RADIUS_METERS)
            parameter("treffPerSide", ADDRESSES_PER_PAGE)
        }.decode<KartverketAddressesResponse>("addresses")
        return Place(city = placeNames.villageName() ?: addresses.postalTown(), address = addresses.toAddress())
    }

    /**
     * No exception is passed on as cause and no message has the URL: both hold the coordinates, which are personal
     * data. Any failure of the call (also a DNS error, which is not an `IOException`) is a [PlaceLookupException], so
     * one failing point does not stop the other points. A cancellation is not swallowed.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchBody(url: String, what: String, query: HttpRequestBuilder.() -> Unit): String {
        val (status, body) = try {
            httpClient.get(url) { query() }.let { it.status to it.bodyAsText() }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw PlaceLookupException("Kartverket $what lookup failed: ${e::class.simpleName ?: "Exception"}")
        }
        if (status !=
            HttpStatusCode.OK
        ) {
            throw PlaceLookupException("Kartverket $what lookup failed: status=${status.value}")
        }
        return body
    }
}

private inline fun <reified T> String.decode(what: String): T = runCatching { json.decodeFromString<T>(this) }
    .getOrElse { throw PlaceLookupException("Kartverket $what response could not be read") }

private fun GeoPoint.cacheKey(): Pair<Long, Long> =
    (latitude * CACHE_DECIMALS_FACTOR).roundToLong() to (longitude * CACHE_DECIMALS_FACTOR).roundToLong()
