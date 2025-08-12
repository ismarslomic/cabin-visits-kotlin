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

    private fun classifyAsCabinTrip(
        roundTrip: List<VehicleTrip>,
        homeCity: String,
        cabinCity: String,
    ): CabinVehicleTrip? {
        val toCabin = mutableListOf<VehicleTrip>()
        val atCabin = mutableListOf<VehicleTrip>()
        val fromCabin = mutableListOf<VehicleTrip>()
        var state = "skip"
        for (trip in roundTrip) {
            when (state) {
                "skip" -> {
                    if (trip.startCity == homeCity) {
                        toCabin += trip
                        state = if (trip.endCity == cabinCity) "at" else "to"
                    }
                }

                "to" -> {
                    toCabin += trip
                    if (trip.endCity == cabinCity) state = "at"
                }

                "at" -> {
                    if (trip.startCity == cabinCity && trip.endCity == homeCity) {
                        fromCabin += trip
                        state = "done"
                    } else if (trip.startCity == cabinCity && trip.endCity != cabinCity) {
                        val remainingTrips = roundTrip.dropWhile { it != trip }.drop(1)
                        val nextKeyStop =
                            remainingTrips.firstOrNull { it.endCity == cabinCity || it.endCity == homeCity }

                        if (nextKeyStop?.endCity == homeCity) {
                            // We leave cabin and the next important stop is home -> start fromCabin
                            fromCabin += trip
                            state = "from"
                        } else {
                            // Either we return to cabin first or there is no home stop ahead -> still at cabin
                            atCabin += trip
                        }
                    } else if (trip.startCity != homeCity && trip.endCity != homeCity) {
                        atCabin += trip
                    }
                }

                "from" -> {
                    fromCabin += trip
                    if (trip.endCity == homeCity) state = "done"
                }
            }
        }

        return if (
            toCabin.isNotEmpty() &&
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
}
