package no.slomic.smarthytte.checkin

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.QueryKotlinApi
import com.influxdb.query.FluxRecord
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import no.slomic.smarthytte.BaseDbTest

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

class CheckInServiceTest :
    BaseDbTest({
        lateinit var checkInRepository: CheckInRepository
        lateinit var checkInService: CheckInService
        lateinit var mockQueryApi: QueryKotlinApi

        beforeEach {
            checkInRepository = SqliteCheckInRepository()
            checkInService = CheckInService(
                checkInRepository = checkInRepository,
                bucketName = "foo",
                measurement = "checked_in",
                fullSyncStart = Instant.parse("2025-01-14T10:00:00Z"),
                fullSyncStop = Instant.parse("2025-01-23T18:00:00Z"),
            )

            val mockClient: InfluxDBClientKotlin = mockk<InfluxDBClientKotlin>(relaxed = true)
            mockQueryApi = mockk<QueryKotlinApi>(relaxed = true)
            every { mockClient.getQueryKotlinApi() } returns mockQueryApi
            mockkObject(InfluxDBClientProvider)
            every { InfluxDBClientProvider.client() } returns mockClient
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

            checkInService.synchronizeCheckIns()

            val storedCheckIns = checkInRepository.allCheckIns()
            storedCheckIns shouldHaveSize 2

            val storedCheckInTrip1 = storedCheckIns.first()
            storedCheckInTrip1.id shouldBe checkInTimeVisit1.toString()
            storedCheckInTrip1.timestamp shouldBe checkInTimeVisit1
            storedCheckInTrip1.status shouldBe CheckInStatus.CHECKED_IN

            val storedCheckOutTrip1 = storedCheckIns.last()
            storedCheckOutTrip1.id shouldBe checkOutTimeVisit1.toString()
            storedCheckOutTrip1.timestamp shouldBe checkOutTimeVisit1
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

            checkInService.synchronizeCheckIns()

            val storedCheckIns = checkInRepository.allCheckIns()
            storedCheckIns shouldHaveSize 2

            val storedCheckInTrip1 = storedCheckIns.first()
            storedCheckInTrip1.id shouldBe checkInTime.toString()
            storedCheckInTrip1.timestamp shouldBe checkInTime
            storedCheckInTrip1.status shouldBe CheckInStatus.CHECKED_OUT

            val storedCheckOutTrip1 = storedCheckIns.last()
            storedCheckOutTrip1.id shouldBe checkOutTime.toString()
            storedCheckOutTrip1.timestamp shouldBe checkOutTime
            storedCheckOutTrip1.status shouldBe CheckInStatus.CHECKED_IN
        }

        "empty list of new check ins should not store check ins to database" {
            // Mock FluxRecord objects
            val mockChannel = emitMockRecords(listOf())

            // Stub the method to return our mock flow
            coEvery { mockQueryApi.query(any<String>()) } returns mockChannel

            checkInService.synchronizeCheckIns()

            val storedCheckIns = checkInRepository.allCheckIns()
            storedCheckIns shouldHaveSize 0
        }
    })
