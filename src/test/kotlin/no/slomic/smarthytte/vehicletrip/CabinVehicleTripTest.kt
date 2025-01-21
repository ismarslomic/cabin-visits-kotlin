package no.slomic.smarthytte.vehicletrip

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import no.slomic.smarthytte.BaseDbTest
import java.util.*
import kotlin.time.Duration.Companion.minutes

class CabinVehicleTripTest :
    BaseDbTest({
        val osloCity = "Oslo"
        val ullsakCity = "Ullsåk"
        val golCity = "Gol"
        val hemsedalCity = "Hemsedal"
        val nesoddtangen = "Nesoddtangen"
        val lillehammerCity = "Lillehammer"
        val sjusjoenCity = "Sjusjoen"

        "should find all trips between Cabin and Home" {
            val trips = listOf(
                // 2025-01-01
                createTrip(
                    startCity = osloCity,
                    endCity = osloCity,
                    startTime = "2025-01-01T11:15:00+01:00",
                    endTime = "2025-01-01T11:22:00+01:00",
                ),
                createTrip(
                    startCity = osloCity,
                    endCity = osloCity,
                    startTime = "2025-01-01T12:25:00+01:00",
                    endTime = "2025-01-01T12:33:00+01:00",
                ),
                // 2025-01-02
                createTrip(
                    startCity = osloCity,
                    endCity = golCity,
                    startTime = "2025-01-02T15:13:00+01:00",
                    endTime = "2025-01-02T17:59:00+01:00",
                ),
                createTrip(
                    startCity = golCity,
                    endCity = ullsakCity,
                    startTime = "2025-01-02T18:02:00+01:00",
                    endTime = "2025-01-02T18:28:00+01:00",
                ),
                createTrip(
                    startCity = ullsakCity,
                    endCity = hemsedalCity,
                    startTime = "2025-01-02T19:24:00+01:00",
                    endTime = "2025-01-02T19:35:00+01:00",
                ),
                createTrip(
                    startCity = hemsedalCity,
                    endCity = ullsakCity,
                    startTime = "2025-01-02T19:45:00+01:00",
                    endTime = "2025-01-02T19:55:00+01:00",
                ),
                // 2025-01-04
                createTrip(
                    startCity = ullsakCity,
                    endCity = osloCity,
                    startTime = "2025-01-04T12:00:00+01:00",
                    endTime = "2025-01-04T15:05:00+01:00",
                ),
                // 2025-01-10
                createTrip(
                    startCity = osloCity,
                    endCity = nesoddtangen,
                    startTime = "2025-01-10T12:00:00+01:00",
                    endTime = "2025-01-10T15:00:00+01:00",
                ),
                createTrip(
                    startCity = nesoddtangen,
                    endCity = osloCity,
                    startTime = "2025-01-11T16:00:00+01:00",
                    endTime = "2025-01-11T17:00:00+01:00",
                ),
                // 2025-01-17
                createTrip(
                    startCity = osloCity,
                    endCity = ullsakCity,
                    startTime = "2025-01-17T14:00:00+01:00",
                    endTime = "2025-01-17T17:15:00+01:00",
                ),
                // 2025-01-19
                createTrip(
                    startCity = ullsakCity,
                    endCity = golCity,
                    startTime = "2025-01-19T11:00:00+01:00",
                    endTime = "2025-01-19T11:30:00+01:00",
                ),
                createTrip(
                    startCity = golCity,
                    endCity = osloCity,
                    startTime = "2025-01-19T11:45:00+01:00",
                    endTime = "2025-01-19T15:30:00+01:00",
                ),
                // 2025-01-26
                createTrip(
                    startCity = osloCity,
                    endCity = ullsakCity,
                    startTime = "2025-01-26T14:30:00+01:00",
                    endTime = "2025-01-26T18:00:00+01:00",
                ),
                // 2025-01-27
                createTrip(
                    startCity = ullsakCity,
                    endCity = hemsedalCity,
                    startTime = "2025-01-27T11:00:00+01:00",
                    endTime = "2025-01-27T11:14:00+01:00",
                ),
                createTrip(
                    startCity = ullsakCity,
                    endCity = hemsedalCity,
                    startTime = "2025-01-27T14:05:00+01:00",
                    endTime = "2025-01-27T14:15:00+01:00",
                ),
                // 2025-01-28
                createTrip(
                    startCity = ullsakCity,
                    endCity = osloCity,
                    startTime = "2025-01-28T15:00:00+01:00",
                    endTime = "2025-01-28T18:00:00+01:00",
                ),
                // 2025-02-02
                createTrip(
                    startCity = osloCity,
                    endCity = ullsakCity,
                    startTime = "2025-02-02T14:40:00+01:00",
                    endTime = "2025-02-02T17:50:00+01:00",
                ),
                // 2025-02-04
                createTrip(
                    startCity = ullsakCity,
                    endCity = lillehammerCity,
                    startTime = "2025-02-04T11:00:00+01:00",
                    endTime = "2025-02-04T11:30:00+01:00",
                ),
                createTrip(
                    startCity = lillehammerCity,
                    endCity = sjusjoenCity,
                    startTime = "2025-02-04T11:40:00+01:00",
                    endTime = "2025-02-04T13:25:00+01:00",
                ),
                // 2025-02-04
                createTrip(
                    startCity = sjusjoenCity,
                    endCity = sjusjoenCity,
                    startTime = "2025-02-05T11:40:00+01:00",
                    endTime = "2025-02-05T12:00:00+01:00",
                ),
                createTrip(
                    startCity = sjusjoenCity,
                    endCity = sjusjoenCity,
                    startTime = "2025-02-05T15:35:00+01:00",
                    endTime = "2025-02-05T15:44:00+01:00",
                ),
                // 2025-02-06
                createTrip(
                    startCity = sjusjoenCity,
                    endCity = osloCity,
                    startTime = "2025-02-06T13:15:00+01:00",
                    endTime = "2025-02-06T15:23:00+01:00",
                ),
                // 2025-02-14
                createTrip(
                    startCity = osloCity,
                    endCity = ullsakCity,
                    startTime = "2025-02-14T15:00:00+01:00",
                    endTime = "2025-02-14T18:00:00+01:00",
                ),
            )
            val homeCabinTrips: List<VehicleTrip> = findCabinTripsWithExtraStops(trips)

            homeCabinTrips shouldHaveSize 9

            assertTrip(
                vehicleTrip = homeCabinTrips[0],
                startCity = osloCity,
                endCity = ullsakCity,
                extraStops = listOf(golCity),
                startTimestamp = "2025-01-02T15:13:00+01:00",
                endTimestamp = "2025-01-02T18:28:00+01:00",
            )

            assertTrip(
                vehicleTrip = homeCabinTrips[1],
                startCity = ullsakCity,
                endCity = osloCity,
                extraStops = listOf(),
                startTimestamp = "2025-01-04T12:00:00+01:00",
                endTimestamp = "2025-01-04T15:05:00+01:00",
            )

            assertTrip(
                vehicleTrip = homeCabinTrips[2],
                startCity = osloCity,
                endCity = ullsakCity,
                extraStops = listOf(),
                startTimestamp = "2025-01-17T14:00:00+01:00",
                endTimestamp = "2025-01-17T17:15:00+01:00",
            )

            assertTrip(
                vehicleTrip = homeCabinTrips[3],
                startCity = ullsakCity,
                endCity = osloCity,
                extraStops = listOf(golCity),
                startTimestamp = "2025-01-19T11:00:00+01:00",
                endTimestamp = "2025-01-19T15:30:00+01:00",
            )

            assertTrip(
                vehicleTrip = homeCabinTrips[4],
                startCity = osloCity,
                endCity = ullsakCity,
                extraStops = listOf(),
                startTimestamp = "2025-01-26T14:30:00+01:00",
                endTimestamp = "2025-01-26T18:00:00+01:00",
            )

            assertTrip(
                vehicleTrip = homeCabinTrips[5],
                startCity = ullsakCity,
                endCity = osloCity,
                extraStops = listOf(),
                startTimestamp = "2025-01-28T15:00:00+01:00",
                endTimestamp = "2025-01-28T18:00:00+01:00",
            )

            assertTrip(
                vehicleTrip = homeCabinTrips[6],
                startCity = osloCity,
                endCity = ullsakCity,
                extraStops = listOf(),
                startTimestamp = "2025-02-02T14:40:00+01:00",
                endTimestamp = "2025-02-02T17:50:00+01:00",
            )

            assertTrip(
                vehicleTrip = homeCabinTrips[7],
                startCity = ullsakCity,
                endCity = osloCity,
                extraStops = listOf(lillehammerCity, sjusjoenCity),
                startTimestamp = "2025-02-04T11:00:00+01:00",
                endTimestamp = "2025-02-06T15:23:00+01:00",
            )

            assertTrip(
                vehicleTrip = homeCabinTrips[8],
                startCity = osloCity,
                endCity = ullsakCity,
                extraStops = listOf(),
                startTimestamp = "2025-02-14T15:00:00+01:00",
                endTimestamp = "2025-02-14T18:00:00+01:00",
            )
        }
    })

@Suppress("LongParameterList")
fun assertTrip(
    vehicleTrip: VehicleTrip,
    startCity: String,
    endCity: String,
    extraStops: List<String>,
    startTimestamp: String,
    endTimestamp: String,
) {
    vehicleTrip.startCity shouldBe startCity
    vehicleTrip.endCity shouldBe endCity
    vehicleTrip.extraStops shouldBe extraStops
    vehicleTrip.startTime shouldBe Instant.parse(startTimestamp)
    vehicleTrip.endTime shouldBe Instant.parse(endTimestamp)
}

fun createTrip(
    id: String = UUID.randomUUID().toString(),
    startCity: String,
    endCity: String,
    startTime: String,
    endTime: String,
): VehicleTrip {
    val startTimestamp: Instant = Instant.parse(startTime)
    val endTimestamp: Instant = Instant.parse(endTime)

    return VehicleTrip(
        averageEnergyConsumption = 0.0,
        averageEnergyConsumptionUnit = "",
        averageSpeed = 0.0,
        distance = 0.0,
        distanceUnit = "",
        duration = 5.minutes,
        durationUnit = "",
        endAddress = "",
        endCity = endCity,
        endTime = endTimestamp,
        energyRegenerated = 0.0,
        energyRegeneratedUnit = "",
        id = id,
        speedUnit = "",
        startAddress = "",
        startCity = startCity,
        startTime = startTimestamp,
        totalDistance = 0.0,
    )
}
