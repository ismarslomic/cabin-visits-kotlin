package no.slomic.smarthytte.checkin

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import kotlinx.datetime.Instant
import no.slomic.smarthytte.properties.CheckInProperties
import no.slomic.smarthytte.properties.InfluxDbProperties
import no.slomic.smarthytte.properties.InfluxDbPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

fun createCheckInService(checkInRepository: CheckInRepository): CheckInService {
    val influxdbProperties: InfluxDbProperties = loadProperties<InfluxDbPropertiesHolder>().influxDb
    val checkInProperties: CheckInProperties = influxdbProperties.checkIn

    val fullSyncStop: Instant? = if (checkInProperties.rangeStop.isNullOrEmpty()) {
        null
    } else {
        Instant.parse(checkInProperties.rangeStop)
    }

    return CheckInService(
        checkInRepository = checkInRepository,
        bucketName = influxdbProperties.bucket,
        measurement = checkInProperties.measurement,
        fullSyncStart = Instant.parse(checkInProperties.rangeStart),
        fullSyncStop = fullSyncStop,
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
