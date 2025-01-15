package no.slomic.smarthytte.checkin

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

class CheckInService(
    val checkInRepository: CheckInRepository,
    val bucketName: String,
    val measurement: String,
    val fullSyncStart: Instant,
    val fullSyncStop: Instant?,
) {
    private val logger: Logger = KtorSimpleLogger(CheckInService::class.java.name)

    suspend fun synchronizeCheckIns() {
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
            val receivedCheckIns: List<CheckIn> = client
                .getQueryKotlinApi()
                .query(fluxQuery)
                .toList()
                .map { it.toCheckIn() }
                .sortedBy { it.timestamp }
            storeUpdates(receivedCheckIns)
        }
    }

    private suspend fun storeUpdates(checkIns: List<CheckIn>) {
        if (checkIns.isEmpty()) {
            logger.info("No check ins to update.")
        } else {
            for (checkIn in checkIns) {
                checkInRepository.addOrUpdate(checkIn)
            }
            val latestTimestamp: Instant? = checkIns.maxByOrNull { it.timestamp }?.timestamp

            if (latestTimestamp != null) {
                checkInRepository.addOrUpdate(latestTimestamp)
            }
            logger.info("Saved ${checkIns.size} check ins.")
        }
    }

    private val fullSyncStopOrDefault: Instant
        get() = fullSyncStop ?: Clock.System.now()

    private suspend fun filterTimeRange(): FilterTimeRange {
        val lastCheckInTimestamp: Instant? = checkInRepository.lastCheckInTimestamp()
        return if (lastCheckInTimestamp == null) {
            val range = FilterTimeRange(
                start = fullSyncStart.toIsoUtcString(),
                stop = fullSyncStopOrDefault.toIsoUtcString(),
            )
            logger.info("Performing full sync for check ins between $range")

            range
        } else {
            val range = // Adding 1 second to the start to exclude last check in already processed
                FilterTimeRange(
                    start = lastCheckInTimestamp.plus(1.seconds).toIsoUtcString(),
                    stop = nowIsoUtcString(),
                )
            logger.info("Performing incremental sync for check ins between $range")

            range
        }
    }

    private fun FluxRecord.toCheckIn(): CheckIn = CheckIn(
        id = time.toString(),
        timestamp = time!!.toKotlinInstant().truncatedToMillis(),
        status = (value as String).toStatus(),
    )

    private fun String.toStatus() = if (this == "on") CheckInStatus.CHECKED_IN else CheckInStatus.CHECKED_OUT

    private data class FilterTimeRange(val start: String, val stop: String) {
        override fun toString(): String = "$start - $stop"
    }
}
