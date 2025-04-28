package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.readGuestFromJsonFile
import no.slomic.smarthytte.properties.GuestPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

class GuestService(private val guestRepository: GuestRepository) {
    private val logger: Logger = KtorSimpleLogger(GuestService::class.java.name)
    private val guestProperties = loadProperties<GuestPropertiesHolder>().guest
    private val filePath = guestProperties.filePath

    suspend fun insertGuestsFromFile() {
        logger.info("Reading guests from file $filePath and updating database..")

        val persistenceResults: MutableList<PersistenceResult> = mutableListOf()

        val guestsFromFile: List<Guest> = readGuestFromJsonFile(filePath)
        for (guest in guestsFromFile) {
            persistenceResults.add(guestRepository.addOrUpdate(guest))
        }

        val addedCount = persistenceResults.count { it == PersistenceResult.ADDED }
        val updatedCount = persistenceResults.count { it == PersistenceResult.UPDATED }
        val noActionCount = persistenceResults.count { it == PersistenceResult.NO_ACTION }

        logger.info(
            "Updating guests in database complete. " +
                "Total guests in file: ${guestsFromFile.size}, added: $addedCount, " +
                "updated: $updatedCount, no actions: $noActionCount",
        )
    }
}
