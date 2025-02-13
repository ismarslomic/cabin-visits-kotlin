package no.slomic.smarthytte.common

import kotlinx.serialization.json.Json
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.vehicletrips.VehicleTripResponse
import java.io.File

fun readGuestFromJsonFile(filePath: String): List<Guest> {
    val file = File(filePath)
    val json = Json { prettyPrint = true }
    val jsonStringFromFile = file.readText()

    return json.decodeFromString(jsonStringFromFile)
}

fun readVehicleTripFromJsonFile(filePath: String): List<VehicleTripResponse> {
    val file = File(filePath)
    val json = Json { prettyPrint = true }
    val jsonStringFromFile = file.readText()

    return json.decodeFromString(jsonStringFromFile)
}

fun readSummaryToGuestFromJsonFile(filePath: String): Map<String, List<String>> {
    val file = File(filePath)
    val json = Json { prettyPrint = true }
    val jsonStringFromFile = file.readText()

    return json.decodeFromString(jsonStringFromFile)
}
