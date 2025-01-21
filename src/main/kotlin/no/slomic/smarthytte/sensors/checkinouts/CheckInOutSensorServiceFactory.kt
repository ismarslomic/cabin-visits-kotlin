package no.slomic.smarthytte.sensors.checkinouts

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import kotlinx.datetime.Instant
import no.slomic.smarthytte.properties.CheckInProperties
import no.slomic.smarthytte.properties.InfluxDbProperties
import no.slomic.smarthytte.properties.InfluxDbPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

fun createCheckInOutSensorService(checkInOutSensorRepository: CheckInOutSensorRepository): CheckInOutSensorService {
    val influxdbProperties: InfluxDbProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb
    val checkInOutSensorProperties: CheckInProperties = influxdbProperties.checkIn

    val fullSyncStopTime: Instant? = if (checkInOutSensorProperties.rangeStop.isNullOrEmpty()) {
        null
    } else {
        Instant.parse(checkInOutSensorProperties.rangeStop)
    }

    return CheckInOutSensorService(
        checkInOutSensorRepository = checkInOutSensorRepository,
        bucketName = influxdbProperties.bucket,
        measurement = checkInOutSensorProperties.measurement,
        fullSyncStartTime = Instant.parse(checkInOutSensorProperties.rangeStart),
        fullSyncStopTime = fullSyncStopTime,
    )
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
