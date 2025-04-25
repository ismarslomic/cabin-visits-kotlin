package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.readGuestFromJsonFile
import no.slomic.smarthytte.properties.GuestPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

class GuestService(private val guestRepository: GuestRepository) {
    private val logger: Logger = KtorSimpleLogger(GuestService::class.java.name)
    val guestProperties = loadProperties<GuestPropertiesHolder>().guest
    val filePath = guestProperties.filePath

    suspend fun insertGuestsFromFile() {
        logger.info("Reading guests from file $filePath and updating database..")

        val guestsFromFile: List<Guest> = readGuestFromJsonFile(filePath)
        for (guest in guestsFromFile) {
            guestRepository.addOrUpdate(guest)
        }

        logger.info("Updating guests in database complete")
    }
}
