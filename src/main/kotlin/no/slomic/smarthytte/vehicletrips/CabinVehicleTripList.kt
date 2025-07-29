@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.vehicletrips

import kotlin.math.round
import kotlin.time.Duration

const val ONE_DECIMAL_ROUND = 10

class CabinVehicleTripList(private val allVehicleTrips: List<VehicleTrip>) {
    val cabinTrips: List<VehicleTrip>

    init {
        cabinTrips = findCabinVehicleTrips()
    }

    /**
     * Finds all pairs of [VehicleTrip]s representing trips to the cabin and the corresponding return trips home in the
     * given [allVehicleTrips] list.
     *
     * Stops processing when there are no more home-to-cabin trips or if an active (unreturned) cabin trip is detected.
     *
     * @param allVehicleTrips the list of [VehicleTrip] objects to inspect,
     * in chronological order (ordered ascending by the `startTime`).
     * @return a list of [CabinVehicleTrip]s, each containing a to-cabin trip and its corresponding return (if any).
     */
    private fun findCabinVehicleTrips(): List<VehicleTrip> {
        var sortedTrips: List<VehicleTrip> = allVehicleTrips.sortedBy { it.startTime }
        val cabinTrips: MutableList<VehicleTrip> = mutableListOf()

        var cabinTrip: CabinVehicleTrip? = findFirstCabinVehicleTrip(trips = sortedTrips)
        while (cabinTrip != null) {
            cabinTrips.add(cabinTrip.toCabin)

            if (cabinTrip.isActive) {
                break
            } else {
                cabinTrips.add(cabinTrip.fromCabin!!)
                sortedTrips = sortedTrips.subList(cabinTrip.indexInTempList + 1, sortedTrips.lastIndex + 1)
                cabinTrip = findFirstCabinVehicleTrip(trips = sortedTrips)
            }
        }

        return cabinTrips.sortedBy { it.startTime }
    }

    /**
     * Finds the first pair of [VehicleTrip]s representing a trip to the cabin
     * (and an optional corresponding return trip).
     *
     * A trip is considered for pairing only if there is a valid journey from home to the cabin.
     * If a corresponding return trip
     * from the cabin is present, it is included; otherwise, only the outbound trip is returned.
     * Active (unreturned) trips are excluded.
     *
     * @param trips the list of [VehicleTrip]s in chronological order (ordered ascending by the `startTime`).
     * @return a [CabinVehicleTrip] representing the first trip to the cabin and its corresponding return trip,
     * or null if no such trip exists.
     */
    private fun findFirstCabinVehicleTrip(trips: List<VehicleTrip>): CabinVehicleTrip? {
        val toCabinTrip: Pair<Int, VehicleTrip> = findToCabinTrip(trips) ?: return null
        val indexOfToCabinTrip: Int = toCabinTrip.first

        val fromCabinTrip: Pair<Int, VehicleTrip>? = findFromCabinTrip(trips, indexOfToCabinTrip)
        val indexOfFromCabinTrip: Int? = fromCabinTrip?.first

        return CabinVehicleTrip(
            toCabin = toCabinTrip.second,
            fromCabin = fromCabinTrip?.second,
            indexInTempList = indexOfFromCabinTrip ?: indexOfToCabinTrip,
        )
    }

    /**
     * Finds the first trip in [trips] representing a trip from home to the cabin.
     *
     * This function returns the index and the constructed [VehicleTrip] representing the journey from
     * home to the cabin.
     * If a direct trip from home to the cabin exists, it is returned as-is.
     * If the first trip to the cabin does not start from home, the function reconstructs the full journey by
     * finding the preceding start-from-home trip and including any intermediate trips with city changes as extra stops.
     * The reconstructed trip reflects the total distance, time, and extra stops of the home-to-cabin journey.
     *
     * @param trips the list of [VehicleTrip]s to inspect,
     * chronologically ordered (ordered ascending by the `startTime`).
     * @return a [Pair] of the index in [trips] and the (possibly reconstructed) home-to-cabin [VehicleTrip],
     * or null if no trip to the cabin is found.
     */
    @Suppress("DuplicatedCode")
    private fun findToCabinTrip(trips: List<VehicleTrip>): Pair<Int, VehicleTrip>? {
        // Find the index of a trip to the cabin
        val indexOfToCabinTrip: Int = trips.indexOfFirst { it.isToCabin }

        // No trip to the cabin found
        if (indexOfToCabinTrip == -1) {
            return null
        }

        // A from home to cabin trip found
        val toCabinTrip: VehicleTrip = trips[indexOfToCabinTrip]

        // Check if it's a direct trip from home to cabin, or a trip with extra stops/intermediate trips
        val theTrip = if (toCabinTrip.isFromHome) {
            toCabinTrip
        } else {
            val indexOfFromHomeTrip: Int = findStartTripIndex(trips, indexOfToCabinTrip, HOME_CITY_NAME)!!
            val fromHomeTrip: VehicleTrip = trips[indexOfFromHomeTrip]

            val extraTrips: List<VehicleTrip> = findCityChangingTripsBetween(
                trips = trips,
                fromIndex = indexOfFromHomeTrip,
                toIndex = indexOfToCabinTrip,
            )

            val extraTripsCities: List<String> = cityNames(fromHomeTrip.endCity, extraTrips)

            fromHomeTrip.copy(
                id = "${fromHomeTrip.id}-${toCabinTrip.id}",
                endTime = toCabinTrip.endTime,
                endAddress = toCabinTrip.endAddress,
                endCity = toCabinTrip.endCity,
                averageSpeed = weightedMeanSpeed(fromHomeTrip, toCabinTrip, extraTrips),
                distance = weightedMeanDistance(fromHomeTrip, toCabinTrip, extraTrips),
                totalDistance = weightedMeanDistance(fromHomeTrip, toCabinTrip, extraTrips),
                duration = sumTripDurations(fromHomeTrip, toCabinTrip, extraTrips),
                extraStops = extraTripsCities,
            )
        }

        return Pair(indexOfToCabinTrip, theTrip)
    }

    /**
     * Finds the first trip in [trips] representing a trip from the cabin to home, following a trip to the cabin.
     *
     * This function returns the index and the constructed [VehicleTrip] representing the journey
     * from the cabin to home.
     * If a direct trip from the cabin to home exists, it is returned as-is.
     * If the first return trip does not originate from the cabin, the function reconstructs the full return journey by
     * finding the appropriate start-from-cabin trip and including any intermediate trips with
     * city changes as extra stops.
     * The reconstructed trip reflects the total distance, time, and extra stops of the cabin-to-home journey.
     *
     * @param trips the list of [VehicleTrip]s to inspect,
     * chronologically ordered (ordered ascending by the `startTime`).
     * @param indexOfToCabinTrip the index of the trip to the cabin, used as the starting point to search for the return
     * trip.
     * @return a [Pair] of the index in [trips] and the (possibly reconstructed) cabin-to-home [VehicleTrip],
     * or null if no trip from the cabin is found.
     */
    @Suppress("DuplicatedCode")
    private fun findFromCabinTrip(trips: List<VehicleTrip>, indexOfToCabinTrip: Int): Pair<Int, VehicleTrip>? {
        // Find the index of a trip from cabin to home
        val indexOfToHomeTrip: Int? = findEndTripIndex(trips, indexOfToCabinTrip, HOME_CITY_NAME)

        // No trip from cabin to home found, most probably an active (unreturned) trip
        if (indexOfToHomeTrip == null) {
            return null
        }

        // A from cabin to home trip found
        val toHomeTrip: VehicleTrip = trips[indexOfToHomeTrip]

        // Check if it's a direct trip from cabin to home, or a trip with extra stops/intermediate trips
        val theTrip = if (toHomeTrip.isFromCabin) {
            toHomeTrip
        } else {
            val indexOfFromCabinTrip: Int? = findStartTripIndex(trips, indexOfToHomeTrip, CABIN_CITY_NAME)
            if (indexOfFromCabinTrip == null) {
                null
            } else {
                val fromCabinTrip: VehicleTrip = trips[indexOfFromCabinTrip]

                val extraTrips: List<VehicleTrip> = findCityChangingTripsBetween(
                    trips = trips,
                    fromIndex = indexOfFromCabinTrip,
                    toIndex = indexOfToHomeTrip,
                )
                val extraTripsCities = cityNames(fromCabinTrip.endCity, extraTrips)

                fromCabinTrip.copy(
                    id = "${fromCabinTrip.id}-${toHomeTrip.id}",
                    endTime = toHomeTrip.endTime,
                    endAddress = toHomeTrip.endAddress,
                    endCity = toHomeTrip.endCity,
                    averageSpeed = weightedMeanSpeed(fromCabinTrip, toHomeTrip, extraTrips),
                    distance = weightedMeanDistance(fromCabinTrip, toHomeTrip, extraTrips),
                    totalDistance = weightedMeanDistance(fromCabinTrip, toHomeTrip, extraTrips),
                    duration = sumTripDurations(fromCabinTrip, toHomeTrip, extraTrips),
                    extraStops = extraTripsCities,
                )
            }
        }

        return theTrip?.let { Pair(indexOfToHomeTrip, it) }
    }

    /**
     * Returns a list of [VehicleTrip]s occurring strictly between the specified indices in the given [trips] list,
     * excluding trips where the start and end city are the same.
     *
     * @param trips The full list of [VehicleTrip] objects.
     * @param fromIndex The index immediately before the first trip to consider (exclusive).
     * @param toIndex The index of the last trip to consider (exclusive).
     * @return A list of trips between the given indices where the start and end cities differ.
     */
    private fun findCityChangingTripsBetween(
        trips: List<VehicleTrip>,
        fromIndex: Int,
        toIndex: Int,
    ): List<VehicleTrip> = trips.subList(fromIndex + 1, toIndex).filter { it.startCity != it.endCity }

    /**
     * Returns a list of city names consisting of the [fromCityName] and the end city of each trip in [trips].
     *
     * The returned list begins with [fromCityName], followed by the `endCity` value of each [VehicleTrip] in the
     * provided [trips] list, maintaining their original order.
     *
     * Example:
     * ```
     * // If fromCityName = "Oslo"
     * // and trips = listOf(trip1(endCity="Gol"), trip2(endCity="Ullsåk"))
     * // the result will be: listOf("Oslo", "Gol", "Ullsåk")
     * ```
     *
     * @param fromCityName The name of the city to use as the starting point in the returned list.
     * @param trips A list of [VehicleTrip] objects whose end city names will be included,
     * in order, after [fromCityName].
     * @return A [List] of city names starting with [fromCityName], then each trip's end city in sequence.
     */
    private fun cityNames(fromCityName: String, trips: List<VehicleTrip>): List<String> =
        listOf(fromCityName) + trips.map { it.endCity }

    /**
     * Finds the index of a trip to the given list whose [startCity] matches the specified [startCity],
     * searching backwards from the trip at [endTripIndex - 1] down to the start of the list.
     *
     * @param trips The list of [VehicleTrip]s to search through.
     * @param endTripIndex The index after which to start searching backwards (exclusive).
     * @param startCity The city name to match as the start city of the trip.
     * @return The index of the first trip before [endTripIndex] with a [startCity] matching [startCity],
     * or `null` if not found.
     */
    private fun findStartTripIndex(trips: List<VehicleTrip>, endTripIndex: Int, startCity: String): Int? {
        for (i in endTripIndex - 1 downTo 0) {
            if (trips[i].startCity == startCity) {
                return i
            }
        }
        return null
    }

    /**
     * Finds the index of a trip to the given list whose [endCity] matches the specified [endCity],
     * searching forwards from [startTripIndex] up to the end of the list.
     *
     * @param trips The list of [VehicleTrip]s to search through.
     * @param startTripIndex The index at which to start searching (inclusive).
     * @param endCity The city name to match as the end city of the trip.
     * @return The index of the first trip from [startTripIndex] onwards with an [endCity] matching [endCity],
     * or `null` if not found.
     */
    @Suppress("SameParameterValue")
    private fun findEndTripIndex(trips: List<VehicleTrip>, startTripIndex: Int, endCity: String): Int? {
        for (i in startTripIndex until trips.size) {
            if (trips[i].endCity == endCity) {
                return i
            }
        }
        return null
    }

    /**
     * Calculates the total duration of multiple vehicle trips.
     *
     * The function sums the duration of the [start] trip, the [end] trip, and all trips in [extraTrips].
     *
     * @param start The starting trip whose duration is included in the total.
     * @param end The ending trip whose duration is included in the total.
     * @param extraTrips Additional trips whose durations are added to the total.
     * @return The combined [Duration] of all provided trips.
     */
    private fun sumTripDurations(start: VehicleTrip, end: VehicleTrip, extraTrips: List<VehicleTrip>): Duration {
        var duration: Duration = start.duration + end.duration
        extraTrips.forEach { duration += it.duration }
        return duration
    }

    /**
     * Calculates the total distance of multiple vehicle trips and rounds the result to one decimal place.
     *
     * The function sums the distances of the [start] trip, the [end] trip, and all trips in [extraTrips],
     * then rounds the total using the `ONE_DECIMAL_ROUND` constant to ensure the result has at most one decimal.
     *
     * @param start The initial [VehicleTrip] whose distance is included in the total.
     * @param end The final [VehicleTrip] whose distance is included in the total.
     * @param extraTrips Additional [VehicleTrip]s whose distances are included in the total.
     * @return The rounded sum of all trip distances, as a [Double].
     */
    private fun weightedMeanDistance(start: VehicleTrip, end: VehicleTrip, extraTrips: List<VehicleTrip>): Double {
        val extraTripsDistance = extraTrips.sumOf { it.totalDistance }
        val totalDistance = extraTripsDistance + start.totalDistance + end.totalDistance

        return round(totalDistance * ONE_DECIMAL_ROUND) / ONE_DECIMAL_ROUND
    }

    /**
     * Calculates the weighted mean speed of multiple vehicle trips and rounds the result to one decimal place.
     *
     * The function sums the mean speed of the [start] trip, the [end] trip, and all trips in [extraTrips],
     * then rounds the total using the `ONE_DECIMAL_ROUND` constant to ensure the result has at most one decimal.
     *
     * The weighted mean speed is computed by summing the product of each trip's average speed and distance,
     * then dividing by the total distance across all trips. The result is rounded to one decimal place.
     *
     * @param start The first trip in the sequence.
     * @param end The last trip in the sequence.
     * @param extraTrips Additional trips included in the calculation.
     * @return The weighted mean speed of all trips, rounded to one decimal.
     */
    private fun weightedMeanSpeed(start: VehicleTrip, end: VehicleTrip, extraTrips: List<VehicleTrip>): Double {
        val trips = extraTrips + start + end
        val sumSpeedDistance = trips.sumOf { it.averageSpeed * it.distance }
        val sumDistance = trips.sumOf { it.distance }

        return round((sumSpeedDistance / sumDistance) * ONE_DECIMAL_ROUND) / ONE_DECIMAL_ROUND
    }
}
