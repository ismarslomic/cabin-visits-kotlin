package no.slomic.smarthytte.common

import kotlinx.serialization.json.Json
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.vehicletrips.VehicleTripResponse
import java.io.File

fun readContentFromFile(filePath: String): String {
    val file = File(filePath)
    return file.readText()
}

fun readGuestFromJsonFile(filePath: String): List<Guest> {
    val jsonStringFromFile = readContentFromFile(filePath)
    val json = Json { prettyPrint = true }

    return json.decodeFromString(jsonStringFromFile)
}

fun readVehicleTripFromJsonFile(filePath: String): List<VehicleTripResponse> {
    val jsonStringFromFile = readContentFromFile(filePath)
    val json = Json { prettyPrint = true }

    return json.decodeFromString(jsonStringFromFile)
}

fun readSummaryToGuestFromJsonFile(filePath: String): Map<String, List<String>> {
    val jsonStringFromFile = readContentFromFile(filePath)
    val json = Json { prettyPrint = true }

    return json.decodeFromString(jsonStringFromFile)
}
