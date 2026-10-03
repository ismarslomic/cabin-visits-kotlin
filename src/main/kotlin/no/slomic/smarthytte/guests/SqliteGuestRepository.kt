@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
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

            val guestResult: PersistenceResult = if (storedGuest == null) addGuest(guest) else updateGuest(guest)
            val avatarResult: PersistenceResult = addOrUpdateAvatarImage(guest.id, guestAvatarImage)

            // An avatar change is reported as an update of the guest, even when the guest row itself is unchanged
            if (guestResult == PersistenceResult.NO_ACTION && avatarResult != PersistenceResult.NO_ACTION) {
                PersistenceResult.UPDATED
            } else {
                guestResult
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
        storedAvatarImage(guestId)
    }

    private fun storedAvatarImage(guestId: String): ByteArray? = GuestAvatarTable
        .select(GuestAvatarTable.image)
        .where { GuestAvatarTable.guest eq guestId }
        .singleOrNull()
        ?.get(GuestAvatarTable.image)
        ?.bytes

    /** A null [image] means the guest has no avatar, so any stored avatar is deleted. */
    private fun addOrUpdateAvatarImage(guestId: String, image: ByteArray?): PersistenceResult {
        val storedImage: ByteArray? = storedAvatarImage(guestId)

        return when {
            image == null && storedImage == null -> PersistenceResult.NO_ACTION
            image == null -> deleteAvatarImage(guestId)
            storedImage == null -> addAvatarImage(guestId, image)
            else -> updateAvatarImage(guestId, storedImage, image)
        }
    }

    private fun addAvatarImage(guestId: String, image: ByteArray): PersistenceResult {
        logger.trace("Adding avatar image for guest with id: $guestId")

        GuestAvatarTable.insert {
            it[guest] = guestId
            it[GuestAvatarTable.image] = ExposedBlob(image)
            it[updatedTime] = Clock.System.now().truncatedToMillis()
        }

        return PersistenceResult.ADDED
    }

    private fun updateAvatarImage(guestId: String, storedImage: ByteArray, image: ByteArray): PersistenceResult {
        if (storedImage.contentEquals(image)) {
            logger.trace("No changes detected for avatar image for guest with id: $guestId")
            return PersistenceResult.NO_ACTION
        }

        logger.trace("Updating avatar image for guest with id: $guestId")

        GuestAvatarTable.update({ GuestAvatarTable.guest eq guestId }) {
            it[GuestAvatarTable.image] = ExposedBlob(image)
            it[updatedTime] = Clock.System.now().truncatedToMillis()
        }

        return PersistenceResult.UPDATED
    }

    private fun deleteAvatarImage(guestId: String): PersistenceResult {
        logger.trace("Deleting avatar image for guest with id: $guestId")

        GuestAvatarTable.deleteWhere { guest eq guestId }

        return PersistenceResult.DELETED
    }

    private fun addGuest(guest: Guest): PersistenceResult {
        logger.trace("Adding guest with id: ${guest.id}")

        GuestEntity.new(guest.id) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
            isFamily = guest.isFamily
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
    private fun updateGuest(guest: Guest): PersistenceResult {
        logger.trace("Updating guest with id: ${guest.id}")

        val updatedGuest: GuestEntity = GuestEntity.findById(guest.id) ?: return PersistenceResult.NO_ACTION

        with(updatedGuest) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
            isFamily = guest.isFamily
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
