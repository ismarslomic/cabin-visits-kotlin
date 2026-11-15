package no.slomic.smarthytte.vehicletrips.places

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import no.slomic.smarthytte.properties.PlaceProperties
import no.slomic.smarthytte.properties.PlacesProperties
import no.slomic.smarthytte.vehicletrips.GeoPoint
import java.io.IOException

private val HOME = GeoPoint(59.0, 10.0)
private val CABIN = GeoPoint(60.0, 8.0)
private val OTHER = GeoPoint(59.9139, 10.7522)

private val knownPlaces = PlacesProperties(
    home = PlaceProperties(HOME.latitude, HOME.longitude, "Homestreet 1, 0100 Oslo", radiusMeters = 200.0),
    cabin = PlaceProperties(CABIN.latitude, CABIN.longitude, "Cabinroad 2, 3560 Hemsedal", radiusMeters = 200.0),
).toKnownPlaces()

private fun placeName(distance: Int, type: String, spelling: String, status: String = "godkjent og prioritert") =
    """{"meterFraPunkt":$distance,"navneobjekttype":"$type","stedsnavn":[{"navnestatus":"hovednavn",""" +
        """"skrivemåte":"$spelling","skrivemåtestatus":"$status","språk":"Norsk"}],"stedsnummer":1}"""

private fun placeNames(vararg names: String) =
    """{"metadata":{"totaltAntallTreff":${names.size}},"navn":[${names.joinToString(",")}]}"""

private fun address(text: String = "Storgata 28B", postalCode: String = "0184", town: String = "OSLO") =
    """{"metadata":{"totaltAntallTreff":1},"adresser":[{"adressetekst":"$text","postnummer":"$postalCode",""" +
        """"poststed":"$town","meterDistanseTilPunkt":4.9}]}"""

private const val NO_ADDRESSES = """{"metadata":{"totaltAntallTreff":0},"adresser":[]}"""
private const val NO_NAMES = """{"metadata":{"totaltAntallTreff":0},"navn":[]}"""

private class Fixture {
    var namesBody = NO_NAMES
    var addressBody = NO_ADDRESSES
    var namesStatus = HttpStatusCode.OK
    var addressStatus = HttpStatusCode.OK
    var failure: Throwable? = null
    val requests = mutableListOf<io.ktor.http.Url>()

    private val engine = MockEngine { request ->
        requests += request.url
        failure?.let { throw it }
        val isNames = request.url.encodedPath.contains("stedsnavn")
        respond(
            content = if (isNames) namesBody else addressBody,
            status = if (isNames) namesStatus else addressStatus,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val resolver = KartverketPlaceResolver(HttpClient(engine), knownPlaces)
}

class KartverketPlaceResolverTest :
    ShouldSpec({
        context("known places") {
            should("answer for the home and the cabin without any call") {
                val f = Fixture()

                f.resolver.resolve(HOME) shouldBe Place("Oslo", "Homestreet 1, 0100 Oslo")
                f.resolver.resolve(CABIN) shouldBe Place("Ullsåk", "Cabinroad 2, 3560 Hemsedal")

                f.requests.size shouldBe 0
            }
        }

        context("city and address from Kartverket") {
            should("use the village as city and the address with postal code and town in title case") {
                val f = Fixture()
                f.namesBody = placeNames(placeName(120, "Bygdelag (bygd)", "Ullsåk"))
                f.addressBody = address(text = "Storgata 28B", postalCode = "0184", town = "OSLO")

                f.resolver.resolve(OTHER) shouldBe Place(city = "Ullsåk", address = "Storgata 28B, 0184 Oslo")
            }

            should("use the postal town as city when there is no village") {
                val f = Fixture()
                f.namesBody = placeNames(placeName(11, "Adressenavn", "Storgata"))
                f.addressBody = address(town = "NORDRE FOLLO")

                f.resolver.resolve(OTHER).city shouldBe "Nordre Follo"
            }

            should("choose the nearest village") {
                val f = Fixture()
                f.namesBody = placeNames(
                    placeName(250, "Bygdelag (bygd)", "Far"),
                    placeName(40, "Bygdelag (bygd)", "Near"),
                )

                f.resolver.resolve(OTHER).city shouldBe "Near"
            }

            should("ignore a village whose spelling is not approved and priority, and other kinds of names") {
                val f = Fixture()
                f.namesBody = placeNames(
                    placeName(10, "Bygdelag (bygd)", "Dialect", status = "vedtatt"),
                    placeName(20, "Kirke", "Church"),
                )
                f.addressBody = address(town = "GOL")

                f.resolver.resolve(OTHER).city shouldBe "Gol"
            }

            should("keep hyphens and small words of a postal town") {
                listOf(
                    "VÅLER I SOLØR" to "Våler i Solør",
                    "MO I RANA" to "Mo i Rana",
                    "NORDRE-FOLLO" to "Nordre-Follo",
                    "SKJÅK" to "Skjåk",
                    "KRISTIANSAND S" to "Kristiansand S",
                ).forEach { (town, expected) ->
                    val f = Fixture()
                    f.addressBody = address(town = town)

                    f.resolver.resolve(OTHER).city shouldBe expected
                }
            }

            should("skip a place name entry that lacks fields instead of failing the whole answer") {
                val f = Fixture()
                f.namesBody = placeNames(
                    """{"stedsnavn":[{"skrivemåte":"Odd"}]}""",
                    placeName(120, "Bygdelag (bygd)", "Ullsåk"),
                )

                f.resolver.resolve(OTHER).city shouldBe "Ullsåk"
            }

            should("give a place without city and address when Kartverket knows nothing there") {
                val f = Fixture()

                f.resolver.resolve(OTHER) shouldBe Place(city = null, address = null)
            }

            should("give a city without address when only the village is known") {
                val f = Fixture()
                f.namesBody = placeNames(placeName(120, "Bygdelag (bygd)", "Ullsåk"))

                f.resolver.resolve(OTHER) shouldBe Place(city = "Ullsåk", address = null)
            }

            should("send the point to both APIs with the radius and the coordinate system") {
                val f = Fixture()

                f.resolver.resolve(OTHER)

                val (names, addresses) = f.requests
                names.parameters["nord"] shouldBe "59.9139"
                names.parameters["ost"] shouldBe "10.7522"
                names.parameters["koordsys"] shouldBe "4258"
                names.parameters["radius"] shouldBe "300"
                names.parameters["treffPerSide"] shouldBe "500"
                addresses.parameters["lat"] shouldBe "59.9139"
                addresses.parameters["lon"] shouldBe "10.7522"
                addresses.parameters["utkoordsys"] shouldBe "4258"
                addresses.parameters["radius"] shouldBe "300"
                addresses.parameters["treffPerSide"] shouldBe "1"
            }
        }

        context("cache") {
            should("not call again for a point it has found a city for") {
                val f = Fixture()
                f.addressBody = address()

                f.resolver.resolve(OTHER)
                val calls = f.requests.size
                f.resolver.resolve(OTHER)

                f.requests.size shouldBe calls
            }

            should("share an entry between points less than 5 decimals apart, not between points 5 decimals apart") {
                val f = Fixture()
                f.addressBody = address()

                f.resolver.resolve(GeoPoint(59.91390, 10.75220))
                val calls = f.requests.size
                f.resolver.resolve(GeoPoint(59.913904, 10.752204))
                f.requests.size shouldBe calls

                f.resolver.resolve(GeoPoint(59.91391, 10.75220))
                f.requests.size shouldBe calls * 2
            }

            should("look a point up again when nothing was found for it") {
                val f = Fixture()

                f.resolver.resolve(OTHER)
                val calls = f.requests.size
                f.resolver.resolve(OTHER)

                f.requests.size shouldBe calls * 2
            }

            should("not keep a failure") {
                val f = Fixture()
                f.namesStatus = HttpStatusCode.ServiceUnavailable
                shouldThrow<PlaceLookupException> { f.resolver.resolve(OTHER) }

                f.namesStatus = HttpStatusCode.OK
                f.addressBody = address()

                f.resolver.resolve(OTHER).city shouldBe "Oslo"
            }
        }

        context("failures") {
            should("throw a lookup exception for a server error, without the coordinates in the message") {
                val f = Fixture()
                f.namesStatus = HttpStatusCode.BadGateway

                val message = shouldThrow<PlaceLookupException> { f.resolver.resolve(OTHER) }.message.orEmpty()

                message shouldBe "Kartverket place names lookup failed: status=502"
                message shouldNotContain "59.9139"
            }

            should("throw a lookup exception for a network failure, without the URL in the message or as cause") {
                val f = Fixture()
                f.failure =
                    IOException("timeout [url=https://ws.geonorge.no/stedsnavn/v1/punkt?nord=59.9139&ost=10.7522]")

                val error = shouldThrow<PlaceLookupException> { f.resolver.resolve(OTHER) }

                error.message shouldBe "Kartverket place names lookup failed: IOException"
                error.cause shouldBe null
            }

            should("throw a lookup exception for a failure that is not an IOException, such as a DNS error") {
                val f = Fixture()
                f.failure = java.nio.channels.UnresolvedAddressException()

                shouldThrow<PlaceLookupException> { f.resolver.resolve(OTHER) }
                    .message shouldBe "Kartverket place names lookup failed: UnresolvedAddressException"
            }

            should("throw, and not give half an answer, when the address call fails after the place names worked") {
                val f = Fixture()
                f.namesBody = placeNames(placeName(120, "Bygdelag (bygd)", "Ullsåk"))
                f.addressStatus = HttpStatusCode.InternalServerError

                shouldThrow<PlaceLookupException> { f.resolver.resolve(OTHER) }
                    .message shouldBe "Kartverket addresses lookup failed: status=500"
            }

            should("throw a lookup exception for an answer that cannot be read") {
                val f = Fixture()
                f.addressBody = """{"adresser":"not a list"}"""

                shouldThrow<PlaceLookupException> { f.resolver.resolve(OTHER) }
                    .message shouldBe "Kartverket addresses response could not be read"
            }
        }
    })
