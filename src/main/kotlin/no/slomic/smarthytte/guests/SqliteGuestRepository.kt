@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.markUpdated
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant

class SqliteGuestRepository : GuestRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteGuestRepository::class.java.name)

    override suspend fun allGuests(): List<Guest> = suspendTransaction {
        GuestEntity.all().sortedBy { it.firstName }.map(::daoToModel)
    }

    override suspend fun guestById(id: String): Guest? = suspendTransaction {
        GuestEntity.findById(id)?.let(::daoToModel)
    }

    override suspend fun guestsByEmail(email: String): List<Guest> = suspendTransaction {
        GuestEntity.find { GuestTable.email.lowerCase() eq email.trim().lowercase() }.map(::daoToModel)
    }

    override suspend fun update(guestId: String, update: GuestUpdate, actor: String): PersistenceResult =
        suspendTransaction {
            logger.trace("Updating guest with id: $guestId from the API")

            val storedGuest: GuestEntity =
                GuestEntity.findById(guestId) ?: return@suspendTransaction PersistenceResult.NO_ACTION

            if (storedGuest.applyUpdate(update)) {
                storedGuest.markUpdated(actor)

                logger.trace("Updated guest with id: $guestId")
                PersistenceResult.UPDATED
            } else {
                logger.trace("No changes detected for guest with id: $guestId")
                PersistenceResult.NO_ACTION
            }
        }

    override suspend fun addOrUpdate(guest: Guest, guestAvatarImage: ByteArray?, actor: String): PersistenceResult =
        suspendTransaction {
            val entityId: EntityID<String> = EntityID(guest.id, GuestTable)
            val storedGuest: GuestEntity? = GuestEntity.findById(entityId)

            val guestResult: PersistenceResult = if (storedGuest ==
                null
            ) {
                addGuest(guest, actor)
            } else {
                updateGuest(guest, actor)
            }
            val avatarResult: PersistenceResult = addOrUpdateAvatarImage(guest.id, guestAvatarImage, actor)

            // An avatar change is reported as an update of the guest, even when the guest row itself is unchanged
            if (guestResult == PersistenceResult.NO_ACTION && avatarResult != PersistenceResult.NO_ACTION) {
                PersistenceResult.UPDATED
            } else {
                guestResult
            }
        }

    override suspend fun saveAvatar(guestId: String, image: ByteArray, actor: String): PersistenceResult =
        suspendTransaction {
            if (GuestEntity.findById(guestId) == null) {
                PersistenceResult.NO_ACTION
            } else {
                saveAvatarImage(guestId, image, actor)
            }
        }

    override suspend fun avatarById(guestId: String): GuestAvatar? = suspendTransaction {
        GuestAvatarTable
            .selectAll()
            .where { GuestAvatarTable.guest eq guestId }
            .singleOrNull()
            ?.let { GuestAvatar(it[GuestAvatarTable.image].bytes, it.versionTime()) }
    }

    override suspend fun allAvatarVersionTimes(): Map<String, Instant> = suspendTransaction {
        GuestAvatarTable
            .select(GuestAvatarTable.guest, GuestAvatarTable.createdTime, GuestAvatarTable.updatedTime)
            .associate { it[GuestAvatarTable.guest].value to it.versionTime() }
    }

    private fun storedAvatarImage(guestId: String): ByteArray? = GuestAvatarTable
        .select(GuestAvatarTable.image)
        .where { GuestAvatarTable.guest eq guestId }
        .singleOrNull()
        ?.get(GuestAvatarTable.image)
        ?.bytes

    /** A null [image] means the guest has no avatar, so any stored avatar is deleted. */
    private fun addOrUpdateAvatarImage(guestId: String, image: ByteArray?, actor: String): PersistenceResult =
        if (image == null) {
            if (storedAvatarImage(guestId) == null) PersistenceResult.NO_ACTION else deleteAvatarImage(guestId)
        } else {
            saveAvatarImage(guestId, image, actor)
        }

    /** First image creates the row (`created_*`), a different image replaces it (`updated_*`). */
    private fun saveAvatarImage(guestId: String, image: ByteArray, actor: String): PersistenceResult {
        val storedImage: ByteArray? = storedAvatarImage(guestId)

        return if (storedImage == null) {
            addAvatarImage(guestId, image, actor)
        } else {
            updateAvatarImage(guestId, storedImage, image, actor)
        }
    }

    private fun addAvatarImage(guestId: String, image: ByteArray, actor: String): PersistenceResult {
        logger.trace("Adding avatar image for guest with id: $guestId")

        GuestAvatarTable.insert {
            it[guest] = guestId
            it[GuestAvatarTable.image] = ExposedBlob(image)
            it[createdTime] = Clock.System.now().truncatedToMillis()
            it[createdBy] = actor
        }

        return PersistenceResult.ADDED
    }

    private fun updateAvatarImage(
        guestId: String,
        storedImage: ByteArray,
        image: ByteArray,
        actor: String,
    ): PersistenceResult {
        if (storedImage.contentEquals(image)) {
            logger.trace("No changes detected for avatar image for guest with id: $guestId")
            return PersistenceResult.NO_ACTION
        }

        logger.trace("Updating avatar image for guest with id: $guestId")

        GuestAvatarTable.update({ GuestAvatarTable.guest eq guestId }) {
            it[GuestAvatarTable.image] = ExposedBlob(image)
            it[updatedTime] = Clock.System.now().truncatedToMillis()
            it[updatedBy] = actor
        }

        return PersistenceResult.UPDATED
    }

    private fun deleteAvatarImage(guestId: String): PersistenceResult {
        logger.trace("Deleting avatar image for guest with id: $guestId")

        GuestAvatarTable.deleteWhere { guest eq guestId }

        return PersistenceResult.DELETED
    }

    private fun addGuest(guest: Guest, actor: String): PersistenceResult {
        logger.trace("Adding guest with id: ${guest.id}")

        GuestEntity.new(guest.id) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            email = guest.email
            gender = guest.gender
            isFamily = guest.isFamily
            role = guest.role
            createdTime = Clock.System.now().truncatedToMillis()
            createdBy = actor
        }

        logger.trace("Added guest with id: ${guest.id}")
        return PersistenceResult.ADDED
    }

    /**
     * Overwrites all properties with the values of [guest].
     *
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * guest.
     */
    private fun updateGuest(guest: Guest, actor: String): PersistenceResult {
        logger.trace("Updating guest with id: ${guest.id}")

        val updatedGuest: GuestEntity = GuestEntity.findById(guest.id) ?: return PersistenceResult.NO_ACTION

        with(updatedGuest) {
            firstName = guest.firstName
            lastName = guest.lastName
            birthYear = guest.birthYear
            gender = guest.gender
            email = guest.email
            isFamily = guest.isFamily
            role = guest.role
        }

        val isDirty: Boolean = updatedGuest.writeValues.isNotEmpty()

        return if (isDirty) {
            updatedGuest.markUpdated(actor)

            logger.trace("Updated guest with id: ${guest.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for guest with id: ${guest.id}")
            PersistenceResult.NO_ACTION
        }
    }

    /** Sets the properties of [update] that differ from the stored ones, and returns whether anything changed. */
    private fun GuestEntity.applyUpdate(update: GuestUpdate): Boolean {
        var changed = false

        fun <T : Any> change(new: T?, current: T?, assign: (T) -> Unit) {
            if (new != null && new != current) {
                assign(new)
                changed = true
            }
        }

        change(update.firstName, firstName) { firstName = it }
        change(update.lastName, lastName) { lastName = it }
        change(update.birthYear, birthYear) { birthYear = it }
        change(update.gender, gender) { gender = it }
        change(update.email, email) { email = it }
        change(update.isFamily, isFamily) { isFamily = it }
        change(update.role, role) { role = it }

        return changed
    }
}

/** `updated_time`, or `created_time` for an avatar that was never replaced. */
private fun ResultRow.versionTime(): Instant = this[GuestAvatarTable.updatedTime] ?: this[GuestAvatarTable.createdTime]
