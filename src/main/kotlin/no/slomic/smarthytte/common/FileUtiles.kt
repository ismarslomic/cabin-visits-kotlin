package no.slomic.smarthytte.common

import kotlinx.serialization.json.Json
import no.slomic.smarthytte.vehicletrips.VehicleTripResponse
import java.io.File

fun readContentFromFile(filePath: String): String {
    val file = File(filePath)
    return file.readText()
}

fun readVehicleTripsFromJsonFile(filePath: String): List<VehicleTripResponse> {
    val jsonStringFromFile = readContentFromFile(filePath)
    val json = Json { prettyPrint = true }

    return json.decodeFromString(jsonStringFromFile)
}
