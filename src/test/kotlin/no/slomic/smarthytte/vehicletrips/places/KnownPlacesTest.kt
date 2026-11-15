package no.slomic.smarthytte.vehicletrips.places

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.properties.PlaceProperties
import no.slomic.smarthytte.properties.PlacesProperties
import no.slomic.smarthytte.vehicletrips.GeoPoint

// Synthetic positions, not the real home and cabin. 0.001 degrees of latitude are about 111 m.
private val HOME = GeoPoint(59.0, 10.0)
private val CABIN = GeoPoint(60.0, 8.0)

private val knownPlaces = PlacesProperties(
    home = PlaceProperties(HOME.latitude, HOME.longitude, "Homestreet 1, 0100 Oslo", radiusMeters = 200.0),
    cabin = PlaceProperties(CABIN.latitude, CABIN.longitude, "Cabinroad 2, 3560 Hemsedal", radiusMeters = 200.0),
).toKnownPlaces()

class KnownPlacesTest :
    ShouldSpec({
        should("give the configured city and address for a point at the home") {
            knownPlaces.find(HOME) shouldBe Place(city = "Oslo", address = "Homestreet 1, 0100 Oslo")
        }

        should("give the cabin city for a point within the radius of the cabin") {
            // about 111 m north of the cabin
            knownPlaces.find(GeoPoint(60.001, 8.0)) shouldBe
                Place(city = "Ullsåk", address = "Cabinroad 2, 3560 Hemsedal")
        }

        should("give null for a point outside both radii") {
            // about 333 m north of the home
            knownPlaces.find(GeoPoint(59.003, 10.0)).shouldBeNull()
        }

        should("use the radius of each place") {
            val wide = PlacesProperties(
                home = PlaceProperties(HOME.latitude, HOME.longitude, "H", radiusMeters = 500.0),
                cabin = PlaceProperties(CABIN.latitude, CABIN.longitude, "C", radiusMeters = 50.0),
            ).toKnownPlaces()

            wide.find(GeoPoint(59.003, 10.0))?.city shouldBe "Oslo"
            wide.find(GeoPoint(60.001, 8.0)).shouldBeNull()
        }

        should("choose the nearest place when the radii overlap") {
            val overlapping = PlacesProperties(
                home = PlaceProperties(59.0, 10.0, "H", radiusMeters = 1_000.0),
                cabin = PlaceProperties(59.004, 10.0, "C", radiusMeters = 1_000.0),
            ).toKnownPlaces()

            overlapping.find(GeoPoint(59.003, 10.0))?.city shouldBe "Ullsåk"
            overlapping.find(GeoPoint(59.001, 10.0))?.city shouldBe "Oslo"
        }

        should("measure the distance east-west along the parallel, not as degrees") {
            // 0.002 degrees of longitude at latitude 60 are about 111 m, at the equator 222 m
            val place = PlacesProperties(
                home = PlaceProperties(0.0, 0.0, "H", radiusMeters = 150.0),
                cabin = PlaceProperties(60.0, 8.0, "C", radiusMeters = 150.0),
            ).toKnownPlaces()

            place.find(GeoPoint(60.0, 8.002))?.city shouldBe "Ullsåk"
            place.find(GeoPoint(0.0, 0.002)).shouldBeNull()
        }
    })
