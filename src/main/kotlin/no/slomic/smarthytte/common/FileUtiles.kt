package no.slomic.smarthytte.common

import kotlinx.serialization.json.Json
import no.slomic.smarthytte.guest.Guest
import no.slomic.smarthytte.vehicletrip.VehicleTrip
import java.io.File

fun readGuestFromJsonFile(filePath: String): List<Guest> {
    val file = File(filePath)
    val json = Json { prettyPrint = true }
    val jsonStringFromFile = file.readText()

    return json.decodeFromString(jsonStringFromFile)
}

fun readVehicleTripFromJsonFile(filePath: String): List<VehicleTrip> {
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
