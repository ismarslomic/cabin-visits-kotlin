package no.slomic.smarthytte.sensors.checkinouts

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.query.FluxRecord
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.channels.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import no.slomic.smarthytte.common.UpsertStatus
import no.slomic.smarthytte.common.nowIsoUtcString
import no.slomic.smarthytte.common.toIsoUtcString
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.properties.CheckInProperties
import no.slomic.smarthytte.properties.InfluxDbProperties
import no.slomic.smarthytte.properties.InfluxDbPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import kotlin.time.Duration.Companion.seconds

class CheckInOutSensorService(
    val checkInOutSensorRepository: CheckInOutSensorRepository,
    influxDbPropertiesHolder: InfluxDbPropertiesHolder = loadProperties<InfluxDbPropertiesHolder>(),
) {
    private val logger: Logger = KtorSimpleLogger(CheckInOutSensorService::class.java.name)
    private val influxdbProperties: InfluxDbProperties = influxDbPropertiesHolder.influxDb
    private val checkInOutSensorProperties: CheckInProperties = influxdbProperties.checkIn
    private val fullSyncStartTime = Instant.parse(checkInOutSensorProperties.rangeStart)
    private val fullSyncStopTime: Instant? = if (checkInOutSensorProperties.rangeStop.isNullOrEmpty()) {
        null
    } else {
        Instant.parse(checkInOutSensorProperties.rangeStop)
    }
    private val bucketName = influxdbProperties.bucket
    private val measurement = checkInOutSensorProperties.measurement

    suspend fun fetchCheckInOut() {
        val filterTimeRange = filterTimeRange()
        logger.info("Fetching check in and check out from InfluxDb in time range $filterTimeRange..")

        val fluxQuery = (
            """
            from(bucket: "$bucketName")
              |> range(start: ${filterTimeRange.start}, stop: ${filterTimeRange.stop})
              |> filter(fn: (r) => r["_measurement"] == "$measurement")
              |> filter(fn: (r) => r["_field"] == "state")
              |> keep(columns: ["_time", "_value"])
              |> sort(columns: ["_time"], desc: false)
              |> group(columns: [])
            """
            )

        val influxDbClient: InfluxDBClientKotlin = InfluxDBClientProvider.client()
        influxDbClient.use { client ->
            // Reads check in and out from InfluxDB and maps to the CheckIn class
            val receivedCheckInOutSensors: List<CheckInOutSensor> = client
                .getQueryKotlinApi()
                .query(fluxQuery)
                .toList()
                .map { it.toCheckIn() }
                .sortedBy { it.time }

            // Store check in and out sensor data to db
            val upsertStatus = storeUpdates(receivedCheckInOutSensors)

            val addedCount = upsertStatus.count { it == UpsertStatus.ADDED }
            val updatedCount = upsertStatus.count { it == UpsertStatus.UPDATED }
            val noActionCount = upsertStatus.count { it == UpsertStatus.NO_ACTION }

            logger.info(
                "Fetching check in and check out complete. " +
                    "Total check in/out in response: ${receivedCheckInOutSensors.size}, added: $addedCount, " +
                    "updated: $updatedCount, no actions: $noActionCount",
            )
        }
    }

    private suspend fun storeUpdates(checkInOutSensors: List<CheckInOutSensor>): MutableList<UpsertStatus> {
        val upsertStatus: MutableList<UpsertStatus> = mutableListOf()

        for (checkIn in checkInOutSensors) {
            upsertStatus.add(checkInOutSensorRepository.addOrUpdate(checkIn))
        }

        val latestTimestamp: Instant? = checkInOutSensors.maxByOrNull { it.time }?.time

        if (latestTimestamp != null) {
            checkInOutSensorRepository.addOrUpdate(latestTimestamp)
        }

        return upsertStatus
    }

    private val fullSyncStopOrDefault: Instant
        get() = fullSyncStopTime ?: Clock.System.now()

    private suspend fun filterTimeRange(): FilterTimeRange {
        val lastCheckInTimestamp: Instant? = checkInOutSensorRepository.latestTime()
        return if (lastCheckInTimestamp == null) {
            val range = FilterTimeRange(
                start = fullSyncStartTime.toIsoUtcString(),
                stop = fullSyncStopOrDefault.toIsoUtcString(),
            )
            logger.trace("Performing full sync for check in/out entries between {}", range)

            range
        } else {
            val range = // Adding 1 second to the start to exclude last check in already processed
                FilterTimeRange(
                    start = lastCheckInTimestamp.plus(1.seconds).toIsoUtcString(),
                    stop = nowIsoUtcString(),
                )
            logger.trace("Performing incremental sync for check in/out entries between {}", range)

            range
        }
    }

    private fun FluxRecord.toCheckIn(): CheckInOutSensor = CheckInOutSensor(
        id = time.toString(),
        time = time!!.toKotlinInstant().truncatedToMillis(),
        status = (value as String).toStatus(),
    )

    private fun String.toStatus() = if (this == "on") CheckInStatus.CHECKED_IN else CheckInStatus.CHECKED_OUT

    private data class FilterTimeRange(val start: String, val stop: String) {
        override fun toString(): String = "$start - $stop"
    }

    object InfluxDBClientProvider {
        fun client(): InfluxDBClientKotlin {
            val influxdbProperties: InfluxDbProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb

            return InfluxDBClientKotlinFactory.create(
                url = influxdbProperties.url,
                token = influxdbProperties.token.toCharArray(),
                org = influxdbProperties.org,
            )
        }
    }
}
