package no.slomic.smarthytte.vehicletrips

import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking
import no.slomic.smarthytte.common.UpsertStatus
import no.slomic.smarthytte.common.readVehicleTripFromJsonFile
import no.slomic.smarthytte.properties.VehicleTripPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

fun Application.insertVehicleTripsFromFile(vehicleTripRepository: VehicleTripRepository) {
    val vehicleTripProperties = loadProperties<VehicleTripPropertiesHolder>().vehicleTrip

    val filePath = vehicleTripProperties.filePath

    log.info("Reading vehicle trips from file $filePath and updating database..")

    val tripsFromFile: List<VehicleTripExternal>
    val upsertStatus: MutableList<UpsertStatus> = mutableListOf()
    runBlocking {
        tripsFromFile = readVehicleTripFromJsonFile(filePath)
        for (trip in tripsFromFile) {
            upsertStatus.add(vehicleTripRepository.addOrUpdate(trip.toInternal()))
        }
    }

    val addedCount = upsertStatus.count { it == UpsertStatus.ADDED }
    val updatedCount = upsertStatus.count { it == UpsertStatus.UPDATED }
    val noActionCount = upsertStatus.count { it == UpsertStatus.NO_ACTION }

    log.info(
        "Updating vehicle trips in database complete. " +
            "Total trips: ${tripsFromFile.size}, added: $addedCount, " +
            "updated: $updatedCount, no actions: $noActionCount",
    )
}
