package no.slomic.smarthytte.checkin

import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.query.FluxRecord
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.calendar.CalendarEventRepository
import no.slomic.smarthytte.common.nowInUtc
import no.slomic.smarthytte.properties.CheckInProperties
import no.slomic.smarthytte.properties.InfluxDbProperties
import no.slomic.smarthytte.properties.InfluxDbPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

fun analyzeInfluxDB(calendarRepository: CalendarEventRepository) {
    val checkIns: Map<LocalDate, List<CheckIn>> = readCheckInFromInfluxDb()
    runBlocking {
        val events = calendarRepository.allEvents()

        println("start;end;check_in;check_out")
        events.forEach { event ->
            val startDate = event.start.toLocalDateTime(TimeZone.UTC).date
            val startCheckIns: List<CheckIn>? = checkIns[startDate]
            val checkInTimestamp: Instant? =
                startCheckIns?.firstOrNull { it.status == CheckInStatus.CHECKED_IN }?.timestamp

            val endDate = event.end.toLocalDateTime(TimeZone.UTC).date
            val endCheckIns: List<CheckIn>? = checkIns[endDate]
            val checkOutTimestamp: Instant? =
                endCheckIns?.firstOrNull { it.status == CheckInStatus.CHECKED_OUT }?.timestamp
            println("${event.start};${event.end};$checkInTimestamp;$checkOutTimestamp")
        }
    }
}

private fun readCheckInFromInfluxDb(): Map<LocalDate, List<CheckIn>> = runBlocking {
    val influxdbProperties: InfluxDbProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb
    val checkInProperties: CheckInProperties = influxdbProperties.checkIn

    val influxDBClient = InfluxDBClientKotlinFactory.create(
        url = influxdbProperties.url,
        token = influxdbProperties.token.toCharArray(),
        org = influxdbProperties.org,
    )

    val rangeStop: String = if (checkInProperties.rangeStop.isNullOrEmpty()) {
        nowInUtc()
    } else {
        checkInProperties.rangeStop
    }

    val fluxQuery = (
        """
            from(bucket: "${influxdbProperties.bucket}")
              |> range(start: ${checkInProperties.rangeStart}, stop: $rangeStop)
              |> filter(fn: (r) => r["_measurement"] == "${checkInProperties.measurement}")
              |> filter(fn: (r) => r["_field"] == "state")
              |> keep(columns: ["_time", "_value"])
              |> sort(columns: ["_time"], desc: false)
              |> group(columns: [])
        """
        )

    // Reads check ins from InfluxDB and maps to the CheckIn class
    val receivedCheckIns: Map<LocalDate, List<CheckIn>> = influxDBClient
        .getQueryKotlinApi()
        .query(fluxQuery)
        .toList()
        .map { it.toCheckIn() }
        .sortedBy { it.timestamp }
        .groupBy { it.timestamp.toLocalDateTime(TimeZone.UTC).date }

    influxDBClient.close()

    return@runBlocking receivedCheckIns
}

private fun FluxRecord.toCheckIn(): CheckIn = CheckIn(
    timestamp = time!!.toKotlinInstant(),
    status = (value as String).toStatus(),
)

private fun String.toStatus() = if (this == "on") CheckInStatus.CHECKED_IN else CheckInStatus.CHECKED_OUT
