package no.slomic.smarthytte.vehicletrips

class CabinVehicleTripList(private val allVehicleTrips: List<VehicleTrip>) {
    val cabinTrips: List<CabinVehicleTrip>

    init {
        cabinTrips = buildCabinTrips(
            vehicleTrips = allVehicleTrips.sortedBy { it.startTime },
            homeCity = HOME_CITY_NAME,
            cabinCity = CABIN_CITY_NAME,
        )
    }

    fun buildCabinTrips(vehicleTrips: List<VehicleTrip>, homeCity: String, cabinCity: String): List<CabinVehicleTrip> {
        val roundTrips = groupTripsByRoundTrip(vehicleTrips)
        return roundTrips.mapNotNull { classifyAsCabinTrip(it, homeCity, cabinCity) }
    }

    fun groupTripsByRoundTrip(trips: List<VehicleTrip>): List<List<VehicleTrip>> {
        if (allVehicleTrips.isEmpty()) return emptyList()

        val sorted = trips.sortedBy { it.startTime }
        val result = mutableListOf<MutableList<VehicleTrip>>()

        var currentGroup = mutableListOf<VehicleTrip>()
        var groupStartCity: String? = null

        for (trip in sorted) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(trip)
                groupStartCity = trip.startCity
                continue
            }

            if (trip.startCity == groupStartCity) {
                result.add(currentGroup)
                currentGroup = mutableListOf(trip)
                groupStartCity = trip.startCity
            } else {
                currentGroup.add(trip)
                if (trip.endCity == groupStartCity) {
                    result.add(currentGroup)
                    currentGroup = mutableListOf()
                    groupStartCity = null
                }
            }
        }

        if (currentGroup.isNotEmpty()) {
            result.add(currentGroup)
        }

        return result
    }

    /**
     * Classifies a list of trips that form a round trip into a structured cabin visit.
     *
     * A cabin visit consists of three consecutive legs in the given roundTrip:
     * 1) toCabin: leaving home and arriving at the cabin (possibly with intermediate stops).
     * 2) atCabin: local driving while staying at or around the cabin (no definitive progress towards home).
     * 3) fromCabin: the journey that leaves the cabin and ultimately returns home
     * (can include intermediate stops).
     *
     * Rules and assumptions used:
     * - We only consider sequences that start from [homeCity] and first reach [cabinCity].
     * - Departure from the cabin (start of fromCabin) begins with the first trip that starts at the cabin and whose
     *   next key stop (endCity of a future trip that is either cabin or home) is home; or a direct cabin -> home trip.
     * - Trips that neither start nor end at home while we are between arrival and departure are considered atCabin.
     * - If no valid toCabin leg (home -> ... -> cabin) is present, the roundTrip is not a cabin visit.
     */
    private fun classifyAsCabinTrip(
        roundTrip: List<VehicleTrip>,
        homeCity: String,
        cabinCity: String,
    ): CabinVehicleTrip? {
        if (roundTrip.isEmpty()) return null

        // 1) Find and collect the to-cabin leg starting with the first trip that departs from home
        val startFromHomeIndex = roundTrip.indexOfFirst { it.startCity == homeCity }

        var isValid = startFromHomeIndex != -1
        var toCabin: List<VehicleTrip> = emptyList()
        var afterArrivalIndex: Int? = null

        if (isValid) {
            val collected = collectToCabin(roundTrip, startFromHomeIndex, cabinCity)
            toCabin = collected.first
            afterArrivalIndex = collected.second
            isValid = toCabin.isNotEmpty() && afterArrivalIndex != null
        }

        var atCabin: List<VehicleTrip> = emptyList()
        var departureStartIndex: Int? = null
        if (isValid) {
            val atAndDeparture = collectAtCabinAndFindDeparture(
                roundTrip,
                afterArrivalIndex!!,
                homeCity,
                cabinCity,
            )
            atCabin = atAndDeparture.first
            departureStartIndex = atAndDeparture.second
        }

        val fromCabin: List<VehicleTrip> = if (departureStartIndex != null) {
            collectUntilHome(roundTrip, departureStartIndex, homeCity)
        } else {
            emptyList()
        }

        // Validate the structure of a proper cabin visit
        return if (
            isValid &&
            toCabin.first().startCity == homeCity &&
            toCabin.last().endCity == cabinCity
        ) {
            CabinVehicleTrip(
                toCabinTrips = toCabin,
                atCabinTrips = atCabin,
                fromCabinTrips = fromCabin,
            )
        } else {
            null
        }
    }

    /** Collect trips from [startIndex] until the first trip that ends at [cabinCity] (inclusive). */
    private fun collectToCabin(
        trips: List<VehicleTrip>,
        startIndex: Int,
        cabinCity: String,
    ): Pair<List<VehicleTrip>, Int?> {
        val acc = mutableListOf<VehicleTrip>()
        var i = startIndex
        while (i < trips.size) {
            val t = trips[i]
            acc += t
            if (t.endCity == cabinCity) {
                return acc to (i + 1) // index right after arrival
            }
            i++
        }
        return emptyList<VehicleTrip>() to null
    }

    /**
     * From [fromIndex], collect local movements while staying at/around the cabin and determine
     * the index where departure towards home begins.
     */
    private fun collectAtCabinAndFindDeparture(
        trips: List<VehicleTrip>,
        fromIndex: Int,
        homeCity: String,
        cabinCity: String,
    ): Pair<List<VehicleTrip>, Int?> {
        val atCabin = mutableListOf<VehicleTrip>()
        var i = fromIndex
        var departureStartIndex: Int? = null

        while (i < trips.size && departureStartIndex == null) {
            val t = trips[i]

            val directDeparture = t.startCity == cabinCity && t.endCity == homeCity
            val leavingCabin = t.startCity == cabinCity && t.endCity != cabinCity

            if (directDeparture) {
                departureStartIndex = i
            } else if (leavingCabin) {
                val nextKeyStop = trips.asSequence()
                    .drop(i + 1)
                    .firstOrNull { it.endCity == cabinCity || it.endCity == homeCity }

                if (nextKeyStop?.endCity == homeCity) {
                    // We left a cabin, and the next significant stop is home => this trip starts the departure
                    departureStartIndex = i
                } else {
                    // We either return to the cabin later or never hit home in this group => still local driving
                    atCabin += t
                }
            } else {
                val isLocalMovement = t.startCity != homeCity && t.endCity != homeCity
                if (isLocalMovement) {
                    atCabin += t
                } else if (t.endCity == homeCity) {
                    // We encountered a trip that ends at home before an explicit departure from the cabin
                    departureStartIndex = i
                }
            }

            i++
        }

        return atCabin to departureStartIndex
    }

    /** Collect trips starting at [startIndex] until the first one that ends at [homeCity] (inclusive). */
    private fun collectUntilHome(trips: List<VehicleTrip>, startIndex: Int, homeCity: String): List<VehicleTrip> {
        val acc = mutableListOf<VehicleTrip>()
        var i = startIndex
        while (i < trips.size) {
            val t = trips[i]
            acc += t
            if (t.endCity == homeCity) break
            i++
        }
        return acc
    }
}
