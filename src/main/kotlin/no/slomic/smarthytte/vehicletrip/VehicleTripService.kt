package no.slomic.smarthytte.vehicletrip

import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.slomic.smarthytte.calendar.CalendarEvent
import no.slomic.smarthytte.calendar.CalendarEventRepository
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

fun analyzeVehicleTrips(
    vehicleTripRepository: VehicleTripRepository,
    calendarEventRepository: CalendarEventRepository,
) {
    runBlocking {
        val allTrips: List<VehicleTrip> = vehicleTripRepository.allVehicleTrips()
        val events: List<CalendarEvent> = calendarEventRepository.allEvents()
        val homeCabinTrips: List<VehicleTrip> = findCabinTripsWithExtraStops(allTrips)

        homeCabinTrips.forEach {
            val foundInCalendar = events.find { event ->
                event.start.toLocalDateTime(TimeZone.UTC).date == it.endTimestamp.toLocalDateTime(TimeZone.UTC).date ||
                    event.end.toLocalDateTime(TimeZone.UTC).date == it.startTimestamp.toLocalDateTime(TimeZone.UTC).date
            } != null
            println("$it found: $foundInCalendar")
        }
    }
}
