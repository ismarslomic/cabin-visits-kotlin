package no.slomic.smarthytte.sensors.checkinouts

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.query.FluxRecord
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.channels.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import no.slomic.smarthytte.common.nowIsoUtcString
import no.slomic.smarthytte.common.toIsoUtcString
import no.slomic.smarthytte.common.truncatedToMillis
import kotlin.time.Duration.Companion.seconds

class CheckInOutSensorService(
    val checkInOutSensorRepository: CheckInOutSensorRepository,
    val bucketName: String,
    val measurement: String,
    val fullSyncStartTime: Instant,
    val fullSyncStopTime: Instant?,
) {
    private val logger: Logger = KtorSimpleLogger(CheckInOutSensorService::class.java.name)

    suspend fun synchronizeCheckInOut() {
        val filterTimeRange = filterTimeRange()

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
            // Reads check ins from InfluxDB and maps to the CheckIn class
            val receivedCheckInOutSensors: List<CheckInOutSensor> = client
                .getQueryKotlinApi()
                .query(fluxQuery)
                .toList()
                .map { it.toCheckIn() }
                .sortedBy { it.time }
            storeUpdates(receivedCheckInOutSensors)
        }
    }

    private suspend fun storeUpdates(checkInOutSensors: List<CheckInOutSensor>) {
        if (checkInOutSensors.isEmpty()) {
            logger.info("No check in/out entries to update.")
        } else {
            for (checkIn in checkInOutSensors) {
                checkInOutSensorRepository.addOrUpdate(checkIn)
            }
            val latestTimestamp: Instant? = checkInOutSensors.maxByOrNull { it.time }?.time

            if (latestTimestamp != null) {
                checkInOutSensorRepository.addOrUpdate(latestTimestamp)
            }
            logger.info("Saved ${checkInOutSensors.size} check in/out entries.")
        }
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
            logger.info("Performing full sync for check in/out entries between $range")

            range
        } else {
            val range = // Adding 1 second to the start to exclude last check in already processed
                FilterTimeRange(
                    start = lastCheckInTimestamp.plus(1.seconds).toIsoUtcString(),
                    stop = nowIsoUtcString(),
                )
            logger.info("Performing incremental sync for check in/out entries between $range")

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
}
