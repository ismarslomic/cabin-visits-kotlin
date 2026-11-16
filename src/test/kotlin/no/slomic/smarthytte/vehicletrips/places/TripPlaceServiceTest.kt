package no.slomic.smarthytte.vehicletrips.places

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.issues.IssueCode
import no.slomic.smarthytte.issues.RecordingIssueReporter
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.DataQuality
import no.slomic.smarthytte.vehicletrips.GeoPoint
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.TripPlaces
import no.slomic.smarthytte.vehicletrips.VehicleTripEntity
import no.slomic.smarthytte.vehicletrips.VehicleTripRepository
import no.slomic.smarthytte.vehicletrips.createTrip
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val START = GeoPoint(59.1, 10.1)
private val END = GeoPoint(60.2, 8.2)
private val OSLO = Place(city = "Oslo", address = "Storgata 1, 0184 Oslo")
private val ULLSAK = Place(city = "Ullsåk", address = "Cabinroad 2, 3560 Hemsedal")

class TripPlaceServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }

        afterTest { testDbSetup.teardownDb() }

        val repository: VehicleTripRepository = SqliteVehicleTripRepository()
        val reporter = RecordingIssueReporter()

        beforeTest {
            reporter.reports.clear()
            reporter.resolved.clear()
        }

        fun service(resolver: PlaceResolver) = TripPlaceService(resolver, repository, reporter)

        suspend fun storeMissing(id: String, startCity: String? = null, endCity: String? = null) {
            val trip = createTrip(
                startCity = startCity,
                endCity = endCity,
                startTime = "2026-11-01T10:00:00Z",
                endTime = "2026-11-01T11:00:00Z",
                id = id,
                dataQuality = DataQuality.PLACE_MISSING,
                startPoint = START,
                endPoint = END,
            ).copy(startAddress = startCity?.let { "street" }, endAddress = endCity?.let { "street" })
            repository.addOrUpdate(trip, "system")
            repository.setJlrData(id, "journey-$id", START, END, "system")
            repository.updatePlaces(
                id = id,
                places = TripPlaces(trip.startCity, trip.startAddress, trip.endCity, trip.endAddress),
                quality = DataQuality.PLACE_MISSING,
                actor = "system",
            )
        }

        "resolve places looks up both points and reports nothing when both have a city" {
            val places = service(FakePlaceResolver(mapOf(START to OSLO, END to ULLSAK)))
                .resolvePlaces("t1", START, END)

            places shouldBe TripPlaces("Oslo", "Storgata 1, 0184 Oslo", "Ullsåk", "Cabinroad 2, 3560 Hemsedal")
            reporter.reports.shouldBeEmpty()
            // Complete places close both problems of the trip, a missing city and a failed lookup
            reporter.resolved shouldContainExactly listOf(
                IssueCode.PLACE_MISSING to "PLACE_MISSING:t1",
                IssueCode.PLACE_LOOKUP_FAILED to "PLACE_LOOKUP_FAILED:t1",
            )
        }

        "resolve places reports the lookup failure and PLACE_MISSING, and keeps the side that worked" {
            val resolver = FakePlaceResolver(mapOf(START to OSLO), failing = setOf(END))

            val places = service(resolver).resolvePlaces("t1", START, END)

            places shouldBe TripPlaces("Oslo", "Storgata 1, 0184 Oslo", null, null)
            reporter.reports.map { it.code to it.key } shouldContainExactly listOf(
                IssueCode.PLACE_LOOKUP_FAILED to "PLACE_LOOKUP_FAILED:t1",
                IssueCode.PLACE_MISSING to "PLACE_MISSING:t1",
            )
            reporter.reports[1].message shouldBe "Trip t1 has no end city"
            reporter.resolved.shouldBeEmpty()
        }

        "resolve places names both sides when neither has a city" {
            val resolver = FakePlaceResolver()

            service(resolver).resolvePlaces("t1", START, END)

            reporter.reports.single { it.code == IssueCode.PLACE_MISSING }.message shouldBe
                "Trip t1 has no start and end city"
        }

        "resolve places does not look up a side that already has city and address" {
            val resolver = FakePlaceResolver(mapOf(END to ULLSAK))
            val current = TripPlaces("Oslo", "Storgata 1, 0184 Oslo", null, null)

            val places = service(resolver).resolvePlaces("t1", START, END, current)

            resolver.resolved shouldContainExactly listOf(END)
            places.startCity shouldBe "Oslo"
            places.endCity shouldBe "Ullsåk"
        }

        "resolve places keeps an existing city when the new lookup only finds an address" {
            val resolver = FakePlaceResolver(mapOf(END to Place(city = null, address = "New street 1")))
            val current = TripPlaces("Oslo", "Storgata 1, 0184 Oslo", "Ullsåk", null)

            val places = service(resolver).resolvePlaces("t1", START, END, current)

            places.endCity shouldBe "Ullsåk"
            places.endAddress shouldBe "New street 1"
        }

        "re-resolve heals a PLACE_MISSING trip and stores city, address and COMPLETE" {
            storeMissing("t1")

            val result = service(FakePlaceResolver(mapOf(START to OSLO, END to ULLSAK))).reResolvePlaceMissing()

            result shouldBe ReResolveResult(healed = 1, stillMissing = 0)
            reporter.resolved shouldContainExactly listOf(
                IssueCode.PLACE_MISSING to "PLACE_MISSING:t1",
                IssueCode.PLACE_LOOKUP_FAILED to "PLACE_LOOKUP_FAILED:t1",
            )
            transaction {
                val row = VehicleTripEntity.findById("t1")!!
                row.dataQuality shouldBe DataQuality.COMPLETE
                row.startCity shouldBe "Oslo"
                row.endAddress shouldBe "Cabinroad 2, 3560 Hemsedal"
            }
            reporter.reports.shouldBeEmpty()
        }

        "re-resolve leaves a trip PLACE_MISSING when Kartverket is down, and does not change the row" {
            storeMissing("t1")
            val versionBefore = transaction { VehicleTripEntity.findById("t1")!!.version }

            val result = service(FakePlaceResolver(failing = setOf(START, END))).reResolvePlaceMissing()

            result shouldBe ReResolveResult(healed = 0, stillMissing = 1)
            transaction {
                val row = VehicleTripEntity.findById("t1")!!
                row.dataQuality shouldBe DataQuality.PLACE_MISSING
                row.version shouldBe versionBefore
            }
            reporter.reports.map { it.code } shouldContainExactly listOf(
                IssueCode.PLACE_LOOKUP_FAILED,
                IssueCode.PLACE_LOOKUP_FAILED,
                IssueCode.PLACE_MISSING,
            )
        }

        "re-resolve leaves a trip PLACE_MISSING when Kartverket knows no city there" {
            storeMissing("t1")

            val result = service(FakePlaceResolver()).reResolvePlaceMissing()

            result shouldBe ReResolveResult(healed = 0, stillMissing = 1)
            reporter.reports.single().code shouldBe IssueCode.PLACE_MISSING
        }

        "re-resolve only looks up the side that is missing" {
            storeMissing("t1", startCity = "Oslo")
            val resolver = FakePlaceResolver(mapOf(END to ULLSAK))

            val result = service(resolver).reResolvePlaceMissing()

            result shouldBe ReResolveResult(healed = 1, stillMissing = 0)
            resolver.resolved shouldContainExactly listOf(END)
            transaction { VehicleTripEntity.findById("t1")!!.startCity shouldBe "Oslo" }
        }

        "re-resolve ignores COMPLETE trips and does nothing when no trip is missing" {
            repository.addOrUpdate(
                createTrip(
                    startCity = "Oslo",
                    endCity = "Ullsåk",
                    startTime = "2026-11-01T10:00:00Z",
                    endTime = "2026-11-01T11:00:00Z",
                    id = "complete",
                ),
                "system",
            )
            val resolver = FakePlaceResolver()

            service(resolver).reResolvePlaceMissing() shouldBe ReResolveResult(0, 0)

            resolver.resolved.shouldBeEmpty()
        }

        "re-resolve counts a PLACE_MISSING trip without coordinates as still missing, without a lookup" {
            repository.addOrUpdate(
                createTrip(
                    startCity = null,
                    endCity = null,
                    startTime = "2026-11-01T10:00:00Z",
                    endTime = "2026-11-01T11:00:00Z",
                    id = "no-points",
                    dataQuality = DataQuality.PLACE_MISSING,
                ),
                "system",
            )
            // addOrUpdate does not store data_quality on insert from the archive path, so set it explicitly
            repository.updatePlaces(
                "no-points",
                TripPlaces(null, null, null, null),
                DataQuality.PLACE_MISSING,
                "system",
            )
            val resolver = FakePlaceResolver()

            val result = service(resolver).reResolvePlaceMissing()

            result shouldBe ReResolveResult(healed = 0, stillMissing = 1)
            resolver.resolved.shouldBeEmpty()
        }
    })
