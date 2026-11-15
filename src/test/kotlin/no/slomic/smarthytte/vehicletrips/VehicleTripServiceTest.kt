package no.slomic.smarthytte.vehicletrips

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.issues.IssueCode
import no.slomic.smarthytte.common.issues.RecordingIssueReporter
import no.slomic.smarthytte.properties.VehicleTripProperties
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.getResourceFilePath
import no.slomic.smarthytte.vehicletrips.jlr.FakeJlrTokenRepository
import no.slomic.smarthytte.vehicletrips.jlr.JlrAuthService
import no.slomic.smarthytte.vehicletrips.jlr.JlrJourneyClient
import no.slomic.smarthytte.vehicletrips.jlr.MutableClock
import no.slomic.smarthytte.vehicletrips.jlr.Reply
import no.slomic.smarthytte.vehicletrips.jlr.errorReply
import no.slomic.smarthytte.vehicletrips.jlr.ok
import no.slomic.smarthytte.vehicletrips.jlr.testSigner
import no.slomic.smarthytte.vehicletrips.jlr.tokens
import no.slomic.smarthytte.vehicletrips.places.FakePlaceResolver
import no.slomic.smarthytte.vehicletrips.places.Place
import no.slomic.smarthytte.vehicletrips.places.PlaceResolver
import no.slomic.smarthytte.vehicletrips.places.TripPlaceService
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-11-15T12:00:00Z")

// Synthetic positions, not the real home and cabin
private val START = GeoPoint(59.1, 10.1)
private val END = GeoPoint(60.2, 8.2)
private val OSLO = Place("Oslo", "Storgata 1, 0184 Oslo")
private val ULLSAK = Place("Ullsåk", "Cabinroad 2, 3560 Hemsedal")

private fun journeyJson(id: String, end: String = "2026-11-14T10:30:00Z", distance: Double = 150.5) =
    """{"journeyId":"$id","startTime":"2026-11-14T08:00:00Z","endTime":"$end","duration":9000.0,""" +
        """"distance":{"value":$distance,"unit":"km"},"averageSpeed":{"value":60.2,"unit":"km/h"},""" +
        """"startLocation":{"latitude":${START.latitude},"longitude":${START.longitude}},""" +
        """"endLocation":{"latitude":${END.latitude},"longitude":${END.longitude}}}"""

private fun page(vararg journeys: String) = ok(
    """{"items":[${journeys.joinToString(",")}],"page":{"totalElements":${journeys.size},"totalPages":1,""" +
        """"currentPage":0,"pageSize":100}}""",
)

/** Resolves like [delegate] until call number [failFromCall], then fails like a locked database. */
private class FlakyResolver(private val delegate: PlaceResolver, var failFromCall: Int) : PlaceResolver {
    private var calls = 0

    override suspend fun resolve(point: GeoPoint): Place {
        calls++
        if (calls >= failFromCall) error("database is locked")
        return delegate.resolve(point)
    }
}

private val endMillis = Instant.parse("2026-11-14T10:30:00Z").toEpochMilliseconds()

private class Fixture(
    syncEnabled: Boolean = true,
    resolver: PlaceResolver = FakePlaceResolver(
        mapOf(
            START to OSLO,
            END to ULLSAK,
        ),
    ),
) {
    val clock = MutableClock(NOW)
    val authScript = ArrayDeque<Reply>()
    val apiScript = ArrayDeque<Reply>()
    val apiQueries = mutableListOf<Map<String, String>>()
    val reporter = RecordingIssueReporter()
    val repository = SqliteVehicleTripRepository()
    val checkpoints = SyncCheckpointService(SqliteSyncCheckpointRepository())

    private val engine = MockEngine { request ->
        val isAuth = request.url.host == "auth.jlr-vcdp.com"
        if (!isAuth) apiQueries += request.url.parameters.entries().associate { it.key to it.value.first() }
        val reply = (if (isAuth) authScript else apiScript).removeFirstOrNull()
            ?: throw IllegalStateException("No scripted reply for ${request.url}")
        respond(reply.body, reply.status, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private val client = HttpClient(engine)
    private val authService = JlrAuthService(
        httpClient = client,
        signer = testSigner(clock),
        tokenRepository = FakeJlrTokenRepository("refresh-0"),
        issueReporter = reporter,
        clientId = "client-1",
        clock = clock,
        pause = { },
    )
    val service = VehicleTripService(
        vehicleTripRepository = repository,
        syncCheckpointService = checkpoints,
        journeyClient = JlrJourneyClient(client, authService, "SAL0000000VIN0001"),
        tripPlaceService = TripPlaceService(resolver, repository, reporter),
        issueReporter = reporter,
        vehicleTripPropertiesHolder = VehicleTripPropertiesHolder(
            VehicleTripProperties(
                filePath = getResourceFilePath("vehicleTrips.json"),
                syncEnabled = syncEnabled,
                syncFromDate = "2026-10-01",
            ),
        ),
        fetchInterval = 12.hours,
        clock = clock,
    )

    init {
        authScript += tokens("access-1", "refresh-1")
    }
}

class VehicleTripServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }

        afterTest { testDbSetup.teardownDb() }

        "a new journey is stored as a trip with the end time as id, its JLR id, coordinates and places" {
            val f = Fixture()
            f.apiScript += page(journeyJson("j1"))

            f.service.fetchVehicleTrips()

            val trip = f.repository.allVehicleTrips().single()
            trip.id shouldBe endMillis.toString()
            trip.jlrJourneyId shouldBe "j1"
            trip.startPoint shouldBe START
            trip.endPoint shouldBe END
            trip.startCity shouldBe "Oslo"
            trip.endCity shouldBe "Ullsåk"
            trip.dataQuality shouldBe DataQuality.COMPLETE
            trip.averageEnergyConsumption.shouldBeNull()
            trip.duration shouldBe 150.minutes
            f.reporter.reports.shouldBeEmpty()
        }

        "a journey whose place lookup fails is stored as PLACE_MISSING and the failure is reported" {
            val f = Fixture(resolver = FakePlaceResolver(failing = setOf(START, END)))
            f.apiScript += page(journeyJson("j1"))

            f.service.fetchVehicleTrips()

            val trip = f.repository.allVehicleTrips().single()
            trip.dataQuality shouldBe DataQuality.PLACE_MISSING
            trip.startCity.shouldBeNull()
            f.reporter.reports.map { it.code }.toSet() shouldBe
                setOf(IssueCode.PLACE_LOOKUP_FAILED, IssueCode.PLACE_MISSING)
        }

        "a journey that is an archive trip is linked and the archive row keeps its values, without a place lookup" {
            val resolver = FakePlaceResolver()
            val f = Fixture(resolver = resolver)
            val archive = createTrip(
                startCity = "Oslo",
                endCity = "Ullsåk",
                startTime = "2026-11-14T08:00:00Z",
                endTime = "2026-11-14T10:30:00Z",
                id = (endMillis + 1_500).toString(),
                averageEnergyConsumption = 21.5,
            ).copy(distance = 150.55, startAddress = "Old start", endAddress = "Old end")
            f.repository.addOrUpdate(archive, "system")
            f.apiScript += page(journeyJson("j1"))

            f.service.fetchVehicleTrips()

            val trip = f.repository.allVehicleTrips().single()
            trip.id shouldBe archive.id
            trip.jlrJourneyId shouldBe "j1"
            trip.startPoint shouldBe START
            trip.endPoint shouldBe END
            trip.averageEnergyConsumption shouldBe 21.5
            trip.startAddress shouldBe "Old start"
            trip.dataQuality shouldBe DataQuality.COMPLETE
            resolver.resolved.shouldBeEmpty()
        }

        "a journey with a different distance than the archive trip is a new trip" {
            val f = Fixture()
            f.repository.addOrUpdate(
                createTrip(
                    "Oslo",
                    "Ullsåk",
                    "2026-11-14T08:00:00Z",
                    "2026-11-14T10:30:00Z",
                    (endMillis + 1_500).toString(),
                )
                    .copy(distance = 180.0),
                "system",
            )
            f.apiScript += page(journeyJson("j1"))

            f.service.fetchVehicleTrips()

            f.repository.allVehicleTrips() shouldHaveSize 2
        }

        "a journey whose end is more than 10 seconds from the archive id is a new trip" {
            val f = Fixture()
            f.repository.addOrUpdate(
                createTrip(
                    "Oslo",
                    "Ullsåk",
                    "2026-11-14T08:00:00Z",
                    "2026-11-14T10:30:00Z",
                    (endMillis + 20_000).toString(),
                )
                    .copy(distance = 150.5),
                "system",
            )
            f.apiScript += page(journeyJson("j1"))

            f.service.fetchVehicleTrips()

            f.repository.allVehicleTrips() shouldHaveSize 2
        }

        "a second run with the same journey adds nothing and looks up no place" {
            val resolver = FakePlaceResolver(mapOf(START to OSLO, END to ULLSAK))
            val f = Fixture(resolver = resolver)
            f.apiScript += page(journeyJson("j1"))
            f.service.fetchVehicleTrips()
            val lookups = resolver.resolved.size
            f.apiScript += page(journeyJson("j1"))

            f.service.fetchVehicleTrips()

            f.repository.allVehicleTrips() shouldHaveSize 1
            resolver.resolved.size shouldBe lookups
        }

        "a second run does not link an archive trip twice" {
            val f = Fixture()
            f.repository.addOrUpdate(
                createTrip(
                    "Oslo",
                    "Ullsåk",
                    "2026-11-14T08:00:00Z",
                    "2026-11-14T10:30:00Z",
                    (endMillis + 1_500).toString(),
                )
                    .copy(distance = 150.5),
                "system",
            )
            f.apiScript += page(journeyJson("j1"))
            f.service.fetchVehicleTrips()
            f.apiScript += page(journeyJson("j1"))

            f.service.fetchVehicleTrips()

            f.repository.allVehicleTrips() shouldHaveSize 1
        }

        "two journeys close in time are each linked to their own archive trip" {
            val f = Fixture()
            val firstEnd = Instant.parse("2026-11-14T10:30:00Z")
            val secondEnd = Instant.parse("2026-11-14T10:31:00Z")
            val first = createTrip(
                "Oslo",
                "Ullsåk",
                "2026-11-14T08:00:00Z",
                "2026-11-14T10:30:00Z",
                (
                    firstEnd.toEpochMilliseconds() +
                        1_500
                    ).toString(),
            )
                .copy(distance = 150.5)
            val second = createTrip(
                "Ullsåk",
                "Oslo",
                "2026-11-14T10:30:30Z",
                "2026-11-14T10:31:00Z",
                (
                    secondEnd.toEpochMilliseconds() +
                        2_000
                    ).toString(),
            )
                .copy(distance = 90.0)
            f.repository.addOrUpdate(first, "system")
            f.repository.addOrUpdate(second, "system")
            f.apiScript += page(
                journeyJson("j1", end = "2026-11-14T10:30:00Z", distance = 150.5),
                journeyJson("j2", end = "2026-11-14T10:31:00Z", distance = 90.0),
            )

            f.service.fetchVehicleTrips()

            f.repository.allVehicleTrips().associate { it.id to it.jlrJourneyId } shouldBe mapOf(
                first.id to "j1",
                second.id to "j2",
            )
        }

        "the same journey twice in one answer is stored once" {
            val f = Fixture()
            f.apiScript += page(journeyJson("j1"), journeyJson("j1"))

            f.service.fetchVehicleTrips()

            f.repository.allVehicleTrips() shouldHaveSize 1
        }

        "a failure after the first journey keeps it, leaves the checkpoint, and the next run adds the rest once" {
            val flaky = FlakyResolver(FakePlaceResolver(mapOf(START to OSLO, END to ULLSAK)), failFromCall = 3)
            val f = Fixture(resolver = flaky)
            val answer = page(
                journeyJson("j1", end = "2026-11-14T10:30:00Z"),
                journeyJson("j2", end = "2026-11-14T12:30:00Z"),
            )
            f.apiScript += answer

            f.service.fetchVehicleTrips()

            f.reporter.reports.map { it.code } shouldContainExactly listOf(IssueCode.FETCH_FAILED)
            f.repository.allVehicleTrips() shouldHaveSize 1
            f.checkpoints.checkpointForVehicleTrips().shouldBeNull()

            flaky.failFromCall = Int.MAX_VALUE
            f.apiScript += answer
            f.service.fetchVehicleTrips()

            f.repository.allVehicleTrips().map { it.jlrJourneyId } shouldContainExactly listOf("j1", "j2")
            f.checkpoints.checkpointForVehicleTrips() shouldBe LocalDate(2026, 11, 14)
        }

        "the checkpoint becomes the Oslo date of the latest end time" {
            val f = Fixture()
            f.apiScript += page(
                journeyJson("j1", end = "2026-11-14T10:30:00Z"),
                journeyJson("j2", end = "2026-11-14T23:30:00Z"),
            )

            f.service.fetchVehicleTrips()

            // 23:30 UTC is 00:30 the next day in Oslo
            f.checkpoints.checkpointForVehicleTrips() shouldBe LocalDate(2026, 11, 15)
        }

        "the checkpoint stays when no journey is returned" {
            val f = Fixture()
            f.checkpoints.addOrUpdateCheckpointForVehicleTrips(LocalDate(2026, 11, 2))
            f.apiScript += page()

            f.service.fetchVehicleTrips()

            f.checkpoints.checkpointForVehicleTrips() shouldBe LocalDate(2026, 11, 2)
        }

        "the first fetch starts at the start of the sync from date in Oslo time, later fetches at the checkpoint" {
            val first = Fixture()
            first.apiScript += page()
            first.service.fetchVehicleTrips()
            // 2026-10-01 00:00 in Oslo (UTC+2)
            first.apiQueries.single()["from"] shouldBe "2026-09-30T22:00:00Z"

            val later = Fixture()
            later.checkpoints.addOrUpdateCheckpointForVehicleTrips(LocalDate(2026, 11, 2))
            later.apiScript += page()
            later.service.fetchVehicleTrips()
            // 2026-11-02 00:00 in Oslo (UTC+1)
            later.apiQueries.single()["from"] shouldBe "2026-11-01T23:00:00Z"
        }

        "places of PLACE_MISSING trips are looked up again first, also when the Data Hub fails after" {
            val f = Fixture()
            val missing = createTrip(
                startCity = null,
                endCity = null,
                startTime = "2026-11-01T08:00:00Z",
                endTime = "2026-11-01T10:00:00Z",
                id = "missing",
                dataQuality = DataQuality.PLACE_MISSING,
                startPoint = START,
                endPoint = END,
            )
            f.repository.addOrUpdate(missing, "system")
            f.repository.setJlrData("missing", "old-journey", START, END, "system")
            f.repository.updatePlaces(
                "missing",
                TripPlaces(null, null, null, null),
                DataQuality.PLACE_MISSING,
                "system",
            )
            f.apiScript += Reply(HttpStatusCode.ServiceUnavailable, "")

            f.service.fetchVehicleTrips()

            val trip = f.repository.allVehicleTrips().single()
            trip.dataQuality shouldBe DataQuality.COMPLETE
            trip.startCity shouldBe "Oslo"
        }

        "a rejected login is reported as JLR_AUTH_FAILED and does not escape" {
            val f = Fixture()
            f.authScript.clear()
            f.authScript += errorReply("invalid_client", HttpStatusCode.Unauthorized)

            f.service.fetchVehicleTrips()

            f.reporter.reports.map { it.code } shouldContainExactly listOf(IssueCode.JLR_AUTH_FAILED)
            f.service.isFetchDue(NOW) shouldBe true
        }

        "a failing Data Hub is reported as JLR_REQUEST_FAILED and does not escape" {
            val f = Fixture()
            f.apiScript += Reply(HttpStatusCode.ServiceUnavailable, "")

            f.service.fetchVehicleTrips()

            f.reporter.reports.map { it.code } shouldContainExactly listOf(IssueCode.JLR_REQUEST_FAILED)
            f.service.isFetchDue(NOW) shouldBe true
        }

        "an unexpected failure is reported as FETCH_FAILED and does not escape" {
            val broken = object : PlaceResolver {
                override suspend fun resolve(point: GeoPoint): Place = error("database is locked")
            }
            val f = Fixture(resolver = broken)
            f.apiScript += page(journeyJson("j1"))

            f.service.fetchVehicleTrips()

            f.reporter.reports.map { it.code } shouldContainExactly listOf(IssueCode.FETCH_FAILED)
            f.checkpoints.checkpointForVehicleTrips().shouldBeNull()
        }

        "a cancellation is not swallowed" {
            val cancelled = object : PlaceResolver {
                override suspend fun resolve(point: GeoPoint): Place = throw CancellationException("stopped")
            }
            val f = Fixture(resolver = cancelled)
            f.apiScript += page(journeyJson("j1"))

            shouldThrow<CancellationException> { f.service.fetchVehicleTrips() }

            f.reporter.reports.shouldBeEmpty()
        }

        "a fetch is due until one succeeds, then not before the interval has passed" {
            val f = Fixture()
            f.service.isFetchDue(NOW) shouldBe true
            f.apiScript += page()

            f.service.fetchVehicleTrips()

            f.service.isFetchDue(NOW + 11.hours) shouldBe false
            f.service.isFetchDue(NOW + 12.hours) shouldBe true
        }

        "nothing is called or stored when the sync is disabled" {
            val f = Fixture(syncEnabled = false)

            f.service.fetchVehicleTrips()

            f.apiQueries.shouldBeEmpty()
            f.repository.allVehicleTrips().shouldBeEmpty()
            f.checkpoints.checkpointForVehicleTrips().shouldBeNull()
            f.service.isFetchDue(NOW) shouldBe false
        }

        "trips from the archive file are added without touching the checkpoint" {
            val f = Fixture()

            f.service.insertVehicleTripsFromFile()

            val trips = f.repository.allVehicleTrips()
            trips shouldHaveSize 2
            trips.first().id shouldBe "1741200104073"
            f.checkpoints.checkpointForVehicleTrips().shouldBeNull()
        }
    })
