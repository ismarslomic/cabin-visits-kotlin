package no.slomic.smarthytte.vehicletrip

import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking
import no.slomic.smarthytte.common.readVehicleTripFromJsonFile
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

fun Application.insertVehicleTripsFromFile(vehicleTripRepository: VehicleTripRepository) {
    val vehicleTripProperties = loadProperties<VehicleTripPropertiesHolder>().vehicleTrip

    val filePath = vehicleTripProperties.filePath

    log.info("Reading vehicle trips from file $filePath and updating database..")

    runBlocking {
        val tripsFromFile: List<VehicleTrip> = readVehicleTripFromJsonFile(filePath)
        for (trip in tripsFromFile) {
            vehicleTripRepository.addOrUpdate(trip)
        }
    }

    log.info("Updating vehicle trips in database complete")
}
