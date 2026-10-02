package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import kotlin.time.Clock

class SqliteGuestRepository : GuestRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteGuestRepository::class.java.name)

    override suspend fun allGuests(): List<Guest> = suspendTransaction {
        GuestEntity.all().sortedBy { it.firstName }.map(::daoToModel)
    }

    override suspend fun guestById(id: String): Guest? = suspendTransaction {
        GuestEntity.findById(id)?.let(::daoToModel)
    }

    override suspend fun addOrUpdate(guest: Guest, guestAvatarImage: ByteArray?): PersistenceResult =
        suspendTransaction {
            val entityId: EntityID<String> = EntityID(guest.id, GuestTable)
            val storedGuest: GuestEntity? = GuestEntity.findById(entityId)

            if (storedGuest == null) {
                addGuest(guest, guestAvatarImage)
            } else {
                updateGuest(guest, guestAvatarImage)
            }
        }

    override suspend fun setNotionId(notionId: String, guestId: String): PersistenceResult = suspendTransaction {
        logger.trace("Setting notion Id for guest with id: $guestId")

        val storedGuest: GuestEntity =
            GuestEntity.findById(guestId) ?: return@suspendTransaction PersistenceResult.NO_ACTION

        with(storedGuest) {
            this.notionId = notionId
            version = storedGuest.version.inc()
            updatedTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Notion id set for guest with id: $guestId")
        PersistenceResult.UPDATED
    }

    override suspend fun avatarImageById(guestId: String): ByteArray? = suspendTransaction {
        GuestEntity.findById(guestId)?.avatarImage?.bytes
    }

    private fun addGuest(guest: Guest, guestAvatarImage: ByteArray?): PersistenceResult {
        logger.trace("Adding guest with id: ${guest.id}")

        GuestEntity.new(guest.id) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
            isFamily = guest.isFamily
            avatarImage = guestAvatarImage?.let { ExposedBlob(it) }
            createdTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Added guest with id: ${guest.id}")
        return PersistenceResult.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * guest.
     */
    private fun updateGuest(guest: Guest, guestAvatarImage: ByteArray?): PersistenceResult {
        logger.trace("Updating guest with id: ${guest.id}")

        val updatedGuest: GuestEntity = GuestEntity.findById(guest.id) ?: return PersistenceResult.NO_ACTION

        with(updatedGuest) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
            isFamily = guest.isFamily
            avatarImage = guestAvatarImage?.let { ExposedBlob(it) }
        }

        val isDirty: Boolean = updatedGuest.writeValues.isNotEmpty()

        return if (isDirty) {
            updatedGuest.version = updatedGuest.version.inc()
            updatedGuest.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace("Updated guest with id: ${guest.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for guest with id: ${guest.id}")
            PersistenceResult.NO_ACTION
        }
    }
}
