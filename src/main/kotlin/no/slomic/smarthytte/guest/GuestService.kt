package no.slomic.smarthytte.guest

import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking
import no.slomic.smarthytte.common.readGuestFromJsonFile
import no.slomic.smarthytte.properties.GuestPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

fun Application.insertGuestsFromFile(guestRepository: GuestRepository) {
    val guestProperties = loadProperties<GuestPropertiesHolder>().guest

    val filePath = guestProperties.filePath

    log.info("Reading guests from file $filePath and updating database..")

    runBlocking {
        val guestsFromFile: List<Guest> = readGuestFromJsonFile(filePath)
        for (guest in guestsFromFile) {
            guestRepository.addOrUpdate(guest)
        }
    }

    log.info("Updating guests in database complete")
}
