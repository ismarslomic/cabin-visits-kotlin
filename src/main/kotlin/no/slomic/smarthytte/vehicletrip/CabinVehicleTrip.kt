@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.vehicletrip

import kotlin.math.round
import kotlin.time.Duration

const val OSLO_CITY_NAME = "Oslo"
const val ULLSAK_CITY_NAME = "Ullsåk"
const val ONE_DECIMAL_ROUND = 10

fun findCabinTripsWithExtraStops(trips: List<VehicleTrip>): List<VehicleTrip> {
    var sortedTrips: List<VehicleTrip> = trips.sortedBy { it.startTimestamp }
    val homeCabinTrips: MutableList<VehicleTrip> = mutableListOf()

    var homeCabinWithExtraStop: OsloUllsakVehicleTrip? = findFirstCabinWithExtraStops(trips = sortedTrips)
    while (homeCabinWithExtraStop != null) {
        homeCabinTrips.add(homeCabinWithExtraStop.fromOsloToUllsak)
        homeCabinWithExtraStop.fromUllsakToOslo?.let { homeCabinTrips.add(it) }

        sortedTrips = sortedTrips.subList(homeCabinWithExtraStop.lastIndex + 1, sortedTrips.lastIndex + 1)
        homeCabinWithExtraStop = findFirstCabinWithExtraStops(trips = sortedTrips)
    }

    return homeCabinTrips.sortedBy { it.startTimestamp }
}

private fun findOsloToUllsakTrip(trips: List<VehicleTrip>): Pair<Int, VehicleTrip>? {
    val indexOfToUllsak: Int = trips.indexOfFirst { it.endCity == ULLSAK_CITY_NAME }
    if (indexOfToUllsak == -1) {
        return null
    }
    val toUllsakTrip: VehicleTrip = trips[indexOfToUllsak]

    var indexOfFromOslo: Int? = null
    var fromOsloTrip: VehicleTrip? = null
    if (toUllsakTrip.startCity != OSLO_CITY_NAME) {
        indexOfFromOslo = findStartTripIndex(trips, indexOfToUllsak, OSLO_CITY_NAME)!!
        fromOsloTrip = trips[indexOfFromOslo]
    }

    val fromOsloToUllsakTrip: VehicleTrip =
        if (fromOsloTrip == null) {
            toUllsakTrip
        } else {
            @Suppress("DuplicatedCode")
            val extraTrips: List<VehicleTrip> = findExtraTrips(
                trips = trips,
                fromIndex = indexOfFromOslo!!,
                toIndex = indexOfToUllsak,
            )

            @Suppress("DuplicatedCode")
            val extraTripsCities = getExtraTripsCities(fromOsloTrip.endCity, extraTrips)

            fromOsloTrip.copy(
                id = "${fromOsloTrip.id}-${toUllsakTrip.id}",
                endTimestamp = toUllsakTrip.endTimestamp,
                endAddress = toUllsakTrip.endAddress,
                endCity = toUllsakTrip.endCity,
                averageSpeed = weightedMeanSpeed(fromOsloTrip, toUllsakTrip, extraTrips),
                distance = weightedMeanDistance(fromOsloTrip, toUllsakTrip, extraTrips),
                totalDistance = weightedMeanDistance(fromOsloTrip, toUllsakTrip, extraTrips),
                duration = totalDuration(fromOsloTrip, toUllsakTrip, extraTrips),
                extraStops = extraTripsCities,
            )
        }

    return Pair(indexOfToUllsak, fromOsloToUllsakTrip)
}

private fun findUllsakToOsloTrip(trips: List<VehicleTrip>, indexOfToUllsak: Int): Pair<Int, VehicleTrip>? {
    val indexOfToOslo: Int? = findEndTripIndex(trips, indexOfToUllsak, OSLO_CITY_NAME)
    val toOsloTrip: VehicleTrip? = indexOfToOslo?.let { trips[indexOfToOslo] }

    var indexOfFromUllsak: Int? = null
    var fromUllsakTrip: VehicleTrip? = null
    if (toOsloTrip != null && toOsloTrip.startCity != ULLSAK_CITY_NAME) {
        indexOfFromUllsak = findStartTripIndex(trips, indexOfToOslo, ULLSAK_CITY_NAME)
        fromUllsakTrip = indexOfFromUllsak?.let { trips[indexOfFromUllsak] }
    }

    val fromUllsakToOsloTrip: VehicleTrip? =
        if (toOsloTrip != null && toOsloTrip.startCity == ULLSAK_CITY_NAME) {
            toOsloTrip
        } else if (toOsloTrip != null && fromUllsakTrip != null) {
            val extraTrips: List<VehicleTrip> = findExtraTrips(
                trips = trips,
                fromIndex = indexOfFromUllsak!!,
                toIndex = indexOfToOslo,
            )
            val extraTripsCities = getExtraTripsCities(fromUllsakTrip.endCity, extraTrips)

            fromUllsakTrip.copy(
                id = "${fromUllsakTrip.id}-${toOsloTrip.id}",
                endTimestamp = toOsloTrip.endTimestamp,
                endAddress = toOsloTrip.endAddress,
                endCity = toOsloTrip.endCity,
                averageSpeed = weightedMeanSpeed(fromUllsakTrip, toOsloTrip, extraTrips),
                distance = weightedMeanDistance(fromUllsakTrip, toOsloTrip, extraTrips),
                totalDistance = weightedMeanDistance(fromUllsakTrip, toOsloTrip, extraTrips),
                duration = totalDuration(fromUllsakTrip, toOsloTrip, extraTrips),
                extraStops = extraTripsCities,
            )
        } else {
            toOsloTrip
        }

    return if (indexOfToOslo == null) {
        null
    } else {
        Pair(indexOfToOslo, fromUllsakToOsloTrip!!)
    }
}

private fun findExtraTrips(trips: List<VehicleTrip>, fromIndex: Int, toIndex: Int): List<VehicleTrip> =
    trips.subList(fromIndex + 1, toIndex).filter { it.startCity != it.endCity }

private fun getExtraTripsCities(fromCityName: String, extraTrips: List<VehicleTrip>): List<String> =
    listOf(fromCityName) + extraTrips.map { it.endCity }

private fun findFirstCabinWithExtraStops(trips: List<VehicleTrip>): OsloUllsakVehicleTrip? {
    val fromOsloToUllsakTrip: Pair<Int, VehicleTrip> = findOsloToUllsakTrip(trips) ?: return null
    val indexOfToUllsak: Int = fromOsloToUllsakTrip.first

    val fromUllsakToOsloTrip: Pair<Int, VehicleTrip>? = findUllsakToOsloTrip(trips, indexOfToUllsak)
    val indexOfToOslo: Int? = fromUllsakToOsloTrip?.first

    return OsloUllsakVehicleTrip(
        fromOsloToUllsak = fromOsloToUllsakTrip.second,
        fromUllsakToOslo = fromUllsakToOsloTrip?.second,
        lastIndex = indexOfToOslo ?: indexOfToUllsak,
    )
}

private fun findStartTripIndex(trips: List<VehicleTrip>, endTripIndex: Int, startCity: String): Int? {
    for (i in endTripIndex - 1 downTo 0) {
        if (trips[i].startCity == startCity) {
            return i
        }
    }
    return null
}

@Suppress("SameParameterValue")
private fun findEndTripIndex(trips: List<VehicleTrip>, startTripIndex: Int, endCity: String): Int? {
    for (i in startTripIndex until trips.size) {
        if (trips[i].endCity == endCity) {
            return i
        }
    }
    return null
}

private fun totalDuration(start: VehicleTrip, end: VehicleTrip, extraTrips: List<VehicleTrip>): Duration {
    var duration: Duration = start.duration + end.duration
    extraTrips.forEach { duration += it.duration }
    return duration
}

private fun weightedMeanDistance(start: VehicleTrip, end: VehicleTrip, extraTrips: List<VehicleTrip>): Double {
    val extraTripsDistance = extraTrips.sumOf { it.totalDistance }
    val totalDistance = extraTripsDistance + start.totalDistance + end.totalDistance

    return round(totalDistance * ONE_DECIMAL_ROUND) / ONE_DECIMAL_ROUND
}

private fun weightedMeanSpeed(start: VehicleTrip, end: VehicleTrip, extraTrips: List<VehicleTrip>): Double {
    val trips = extraTrips + start + end
    val sumSpeedDistance = trips.sumOf { it.averageSpeed * it.distance }
    val sumDistance = trips.sumOf { it.distance }

    return round((sumSpeedDistance / sumDistance) * ONE_DECIMAL_ROUND) / ONE_DECIMAL_ROUND
}
