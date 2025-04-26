package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.UpsertStatus
import no.slomic.smarthytte.common.readGuestFromJsonFile
import no.slomic.smarthytte.properties.GuestPropertiesHolder
import no.slomic.smarthytte.properties.loadProperties

class GuestService(private val guestRepository: GuestRepository) {
    private val logger: Logger = KtorSimpleLogger(GuestService::class.java.name)
    private val guestProperties = loadProperties<GuestPropertiesHolder>().guest
    private val filePath = guestProperties.filePath

    suspend fun insertGuestsFromFile() {
        logger.info("Reading guests from file $filePath and updating database..")

        val upsertStatus: MutableList<UpsertStatus> = mutableListOf()

        val guestsFromFile: List<Guest> = readGuestFromJsonFile(filePath)
        for (guest in guestsFromFile) {
            upsertStatus.add(guestRepository.addOrUpdate(guest))
        }

        val addedCount = upsertStatus.count { it == UpsertStatus.ADDED }
        val updatedCount = upsertStatus.count { it == UpsertStatus.UPDATED }
        val noActionCount = upsertStatus.count { it == UpsertStatus.NO_ACTION }

        logger.info(
            "Updating guests in database complete. " +
                "Total guests in file: ${guestsFromFile.size}, added: $addedCount, " +
                "updated: $updatedCount, no actions: $noActionCount",
        )
    }
}
