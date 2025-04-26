package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.UpsertStatus
import no.slomic.smarthytte.common.suspendTransaction
import org.jetbrains.exposed.dao.id.EntityID

class SqliteGuestRepository : GuestRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteGuestRepository::class.java.name)

    override suspend fun addOrUpdate(guest: Guest): UpsertStatus = suspendTransaction {
        val entityId: EntityID<String> = EntityID(guest.id, GuestTable)
        val storedGuest: GuestEntity? = GuestEntity.findById(entityId)

        if (storedGuest == null) {
            addGuest(guest)
        } else {
            updateGuest(guest)
        }
    }

    override suspend fun setNotionId(notionId: String, guestId: String): UpsertStatus {
        logger.trace("Setting notion Id for guest with id: $guestId")

        val storedGuest: GuestEntity = GuestEntity.findById(guestId) ?: return UpsertStatus.NO_ACTION

        with(storedGuest) {
            this.notionId = notionId
            version = storedGuest.version.inc()
            updatedTime = Clock.System.now()
        }

        logger.trace("Notion id set for guest with id: $guestId")
        return UpsertStatus.UPDATED
    }

    private fun addGuest(guest: Guest): UpsertStatus {
        logger.trace("Adding guest with id: ${guest.id}")

        GuestEntity.new(guest.id) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
            createdTime = Clock.System.now()
        }

        logger.trace("Added guest with id: ${guest.id}")
        return UpsertStatus.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * guest.
     */
    private fun updateGuest(guest: Guest): UpsertStatus {
        logger.trace("Updating guest with id: ${guest.id}")

        val updatedGuest: GuestEntity = GuestEntity.findById(guest.id) ?: return UpsertStatus.NO_ACTION

        with(updatedGuest) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
        }

        val isDirty: Boolean = updatedGuest.writeValues.isNotEmpty()

        return if (isDirty) {
            updatedGuest.version = updatedGuest.version.inc()
            updatedGuest.updatedTime = Clock.System.now()

            logger.trace("Updated guest with id: ${guest.id}")
            UpsertStatus.UPDATED
        } else {
            logger.trace("No changes detected for guest with id: ${guest.id}")
            UpsertStatus.NO_ACTION
        }
    }
}
