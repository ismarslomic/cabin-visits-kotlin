package no.slomic.smarthytte.sensors.checkinouts

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.QueryKotlinApi
import com.influxdb.query.FluxRecord
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.channels.Channel
import no.slomic.smarthytte.properties.CheckInProperties
import no.slomic.smarthytte.properties.InfluxDbProperties
import no.slomic.smarthytte.properties.InfluxDbPropertiesHolder
import no.slomic.smarthytte.sync.checkpoint.SqliteSyncCheckpointRepository
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointService
import no.slomic.smarthytte.utils.TestDbSetup
import kotlin.time.Instant
import kotlin.time.toJavaInstant

data class MockFluxRecord(val time: Instant, val value: String)

fun emitMockRecords(records: List<MockFluxRecord>): Channel<FluxRecord> {
    val fluxRecords = records.map {
        mockk<FluxRecord> {
            every { time } returns it.time.toJavaInstant()
            every { measurement } returns "checked_in"
            every { value } returns it.value
        }
    }

    return Channel<FluxRecord>(Channel.UNLIMITED).apply {
        fluxRecords.forEach {
            trySend(it)
        }
        close()
    }
}

class CheckInOutSensorServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val influxDbPropertiesHolder = InfluxDbPropertiesHolder(
            influxDb = InfluxDbProperties(
                url = "http://localhost:8086",
                token = "foo-bar",
                org = "my-org",
                bucket = "foo",
                checkIn = CheckInProperties(
                    syncFrequencyMinutes = 10,
                    measurement = "checked_in",
                    rangeStart = "2025-01-14T10:00:00Z",
                    rangeStop = "2025-01-23T18:00:00Z",
                ),
            ),
        )

        lateinit var syncCheckpointService: SyncCheckpointService
        lateinit var checkInOutSensorRepository: CheckInOutSensorRepository
        lateinit var checkInOutSensorService: CheckInOutSensorService
        lateinit var mockQueryApi: QueryKotlinApi

        beforeEach {
            syncCheckpointService = SyncCheckpointService(SqliteSyncCheckpointRepository())
            checkInOutSensorRepository = SqliteCheckInOutSensorRepository()
            checkInOutSensorService = CheckInOutSensorService(
                checkInOutSensorRepository = checkInOutSensorRepository,
                influxDbPropertiesHolder = influxDbPropertiesHolder,
                syncCheckpointService = syncCheckpointService,
            )

            val mockClient: InfluxDBClientKotlin = mockk<InfluxDBClientKotlin>(relaxed = true)
            mockQueryApi = mockk<QueryKotlinApi>(relaxed = true)
            every { mockClient.getQueryKotlinApi() } returns mockQueryApi
            mockkObject(CheckInOutSensorService.InfluxDBClientProvider)
            every { CheckInOutSensorService.InfluxDBClientProvider.client() } returns mockClient
        }

        "list with new check ins should be stored to database" {
            val checkInTimeVisit1 = Instant.parse("2025-01-14T18:00:00Z")
            val checkOutTimeVisit1 = Instant.parse("2025-01-23T11:00:00Z")

            // Mock FluxRecord objects
            val mockChannel = emitMockRecords(
                listOf(
                    MockFluxRecord(checkInTimeVisit1, "on"),
                    MockFluxRecord(checkOutTimeVisit1, "off"),
                ),
            )

            // Stub the method to return our mock flow
            coEvery { mockQueryApi.query(any<String>()) } returns mockChannel

            checkInOutSensorService.fetchCheckInOut()

            val storedCheckIns = checkInOutSensorRepository.allCheckInOuts()
            storedCheckIns shouldHaveSize 2

            val storedCheckInTrip1 = storedCheckIns.first()
            storedCheckInTrip1.id shouldBe checkInTimeVisit1.toString()
            storedCheckInTrip1.time shouldBe checkInTimeVisit1
            storedCheckInTrip1.status shouldBe CheckInStatus.CHECKED_IN

            val storedCheckOutTrip1 = storedCheckIns.last()
            storedCheckOutTrip1.id shouldBe checkOutTimeVisit1.toString()
            storedCheckOutTrip1.time shouldBe checkOutTimeVisit1
            storedCheckOutTrip1.status shouldBe CheckInStatus.CHECKED_OUT
        }

        "list with updated check ins should be stored to database" {
            val checkInTime = Instant.parse("2025-01-14T18:00:00Z")
            val checkOutTime = Instant.parse("2025-01-23T11:00:00Z")

            // Mock FluxRecord objects
            val mockChannel = emitMockRecords(
                listOf(
                    MockFluxRecord(checkInTime, "on"),
                    MockFluxRecord(checkOutTime, "off"),
                    MockFluxRecord(checkInTime, "off"),
                    MockFluxRecord(checkOutTime, "on"),
                ),
            )

            // Stub the method to return our mock flow
            coEvery { mockQueryApi.query(any<String>()) } returns mockChannel

            checkInOutSensorService.fetchCheckInOut()

            val storedCheckIns = checkInOutSensorRepository.allCheckInOuts()
            storedCheckIns shouldHaveSize 2

            val storedCheckInTrip1 = storedCheckIns.first()
            storedCheckInTrip1.id shouldBe checkInTime.toString()
            storedCheckInTrip1.time shouldBe checkInTime
            storedCheckInTrip1.status shouldBe CheckInStatus.CHECKED_OUT

            val storedCheckOutTrip1 = storedCheckIns.last()
            storedCheckOutTrip1.id shouldBe checkOutTime.toString()
            storedCheckOutTrip1.time shouldBe checkOutTime
            storedCheckOutTrip1.status shouldBe CheckInStatus.CHECKED_IN
        }

        "empty list of new check ins should not store check ins to database" {
            // Mock FluxRecord objects
            val mockChannel = emitMockRecords(listOf())

            // Stub the method to return our mock flow
            coEvery { mockQueryApi.query(any<String>()) } returns mockChannel

            checkInOutSensorService.fetchCheckInOut()

            val storedCheckIns = checkInOutSensorRepository.allCheckInOuts()
            storedCheckIns shouldHaveSize 0
        }
    })
