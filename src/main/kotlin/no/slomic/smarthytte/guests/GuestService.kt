package no.slomic.smarthytte.guests

import io.ktor.server.application.Application
import io.ktor.server.application.log
import no.slomic.smarthytte.common.readGuestFromJsonFile
import no.slomic.smarthytte.properties.GuestPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

suspend fun Application.insertGuestsFromFile(guestRepository: GuestRepository) {
    val guestProperties = loadProperties<GuestPropertiesHolder>().guest

    val filePath = guestProperties.filePath

    log.info("Reading guests from file $filePath and updating database..")

    val guestsFromFile: List<Guest> = readGuestFromJsonFile(filePath)
    for (guest in guestsFromFile) {
        guestRepository.addOrUpdate(guest)
    }

    log.info("Updating guests in database complete")
}
