package no.slomic.smarthytte.guest

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.suspendTransaction
import org.jetbrains.exposed.dao.id.EntityID

class SqliteGuestRepository : GuestRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteGuestRepository::class.java.name)

    override suspend fun addOrUpdate(guest: Guest): Guest = suspendTransaction {
        val entityId: EntityID<String> = EntityID(guest.id, GuestTable)
        val storedGuest: GuestEntity? = GuestEntity.findById(entityId)

        if (storedGuest == null) {
            addGuest(guest)
        } else {
            updateGuest(guest)!!
        }
    }

    private fun addGuest(guest: Guest): Guest {
        logger.info("Adding guest with id: ${guest.id}")
        val newGuest = GuestEntity.new(guest.id) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
            createdTime = Clock.System.now()
        }

        logger.info("Added guest with id: ${guest.id}")

        return daoToModel(newGuest)
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * guest.
     */
    private fun updateGuest(guest: Guest): Guest? {
        logger.info("Updating guest with id: ${guest.id}")

        val updatedGuest: GuestEntity = GuestEntity.findById(guest.id) ?: return null

        with(updatedGuest) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
        }

        val isDirty: Boolean = updatedGuest.writeValues.isNotEmpty()

        if (isDirty) {
            updatedGuest.version = updatedGuest.version.inc()
            updatedGuest.updatedTime = Clock.System.now()

            logger.info("Updated guest with id: ${guest.id}")
        } else {
            logger.info("No changes detected for guest with id: ${guest.id}")
        }

        return daoToModel(updatedGuest)
    }
}
