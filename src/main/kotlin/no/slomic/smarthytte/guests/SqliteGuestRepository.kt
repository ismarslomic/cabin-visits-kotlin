@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.markCreated
import no.slomic.smarthytte.common.markUpdated
import no.slomic.smarthytte.common.suspendTransaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.select
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

    override suspend fun update(
        guestId: String,
        expectedVersion: Short,
        update: GuestProperties,
        actor: String,
    ): GuestUpdateOutcome = suspendTransaction {
        logger.trace("Updating guest with id: $guestId from the API")

        val storedGuest: GuestEntity? = GuestEntity.findById(guestId)

        when {
            storedGuest == null -> GuestUpdateOutcome.NotFound

            storedGuest.version != expectedVersion -> GuestUpdateOutcome.VersionMismatch

            storedGuest.applyUpdate(update) -> {
                storedGuest.markUpdated(actor)

                logger.trace("Updated guest with id: $guestId")
                GuestUpdateOutcome.Updated
            }

            else -> {
                logger.trace("No changes detected for guest with id: $guestId")
                GuestUpdateOutcome.Unchanged
            }
        }
    }

    override suspend fun create(guest: GuestProperties, actor: String): Guest = suspendTransaction {
        val takenIds: List<String> = GuestTable.select(GuestTable.id).map { it[GuestTable.id].value }
        val id: String = uniqueGuestId(guestSlug(guest.firstName, guest.lastName), takenIds)

        logger.trace("Creating guest with id: $id from the API")

        GuestEntity.new(id) {
            markCreated(actor)
            assign(guest)
        }.let(::daoToModel)
    }

    override suspend fun addOrUpdate(guest: Guest, actor: String): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(guest.id, GuestTable)
        val storedGuest: GuestEntity? = GuestEntity.findById(entityId)

        if (storedGuest == null) addGuest(guest, actor) else upsertExistingGuest(guest, actor)
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
        GuestAvatarEntity.findById(guestId)?.let { GuestAvatar(it.image.bytes, it.versionTime()) }
    }

    override suspend fun allAvatarVersionTimes(): Map<String, Instant> = suspendTransaction {
        // Only these columns, so the image bytes are not loaded
        GuestAvatarTable
            .select(GuestAvatarTable.id, GuestAvatarTable.createdTime, GuestAvatarTable.updatedTime)
            .associate {
                it[GuestAvatarTable.id].value to
                    (it[GuestAvatarTable.updatedTime] ?: it[GuestAvatarTable.createdTime])
            }
    }

    override suspend fun avatarVersionTime(guestId: String): Instant? = suspendTransaction {
        // Only these columns, so the image bytes are not loaded
        GuestAvatarTable
            .select(GuestAvatarTable.createdTime, GuestAvatarTable.updatedTime)
            .where { GuestAvatarTable.id eq guestId }
            .singleOrNull()
            ?.let { it[GuestAvatarTable.updatedTime] ?: it[GuestAvatarTable.createdTime] }
    }

    /** First image creates the row (`created_*`), a different image replaces it (`updated_*` and `version`). */
    private fun saveAvatarImage(guestId: String, image: ByteArray, actor: String): PersistenceResult {
        val stored: GuestAvatarEntity? = GuestAvatarEntity.findById(guestId)

        return when {
            stored == null -> addAvatarImage(guestId, image, actor)

            stored.image.bytes.contentEquals(image) -> {
                logger.trace("No changes detected for avatar image for guest with id: $guestId")
                PersistenceResult.NO_ACTION
            }

            else -> updateAvatarImage(stored, image, actor)
        }
    }

    private fun addAvatarImage(guestId: String, image: ByteArray, actor: String): PersistenceResult {
        logger.trace("Adding avatar image for guest with id: $guestId")

        GuestAvatarEntity.new(guestId) {
            markCreated(actor)
            this.image = ExposedBlob(image)
        }

        return PersistenceResult.ADDED
    }

    private fun updateAvatarImage(stored: GuestAvatarEntity, image: ByteArray, actor: String): PersistenceResult {
        logger.trace("Updating avatar image for guest with id: ${stored.id.value}")

        stored.image = ExposedBlob(image)
        stored.markUpdated(actor)

        return PersistenceResult.UPDATED
    }

    private fun addGuest(guest: Guest, actor: String): PersistenceResult {
        logger.trace("Adding guest with id: ${guest.id}")

        GuestEntity.new(guest.id) {
            markCreated(actor)
            assign(guest.toProperties())
        }

        logger.trace("Added guest with id: ${guest.id}")
        return PersistenceResult.ADDED
    }

    /**
     * The upsert path of [addOrUpdate] for a guest that exists: overwrites all properties with the values of [guest],
     * with the same change detection as [update] ([applyUpdate]), so the guest is only marked updated if at least one
     * property differs. Unlike [update] there is no version check.
     */
    private fun upsertExistingGuest(guest: Guest, actor: String): PersistenceResult {
        logger.trace("Updating guest with id: ${guest.id}")

        val stored: GuestEntity = GuestEntity.findById(guest.id) ?: return PersistenceResult.NO_ACTION

        return if (stored.applyUpdate(guest.toProperties())) {
            stored.markUpdated(actor)

            logger.trace("Updated guest with id: ${guest.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for guest with id: ${guest.id}")
            PersistenceResult.NO_ACTION
        }
    }

    /** Sets the properties of [update] that differ from the stored ones, and returns whether anything changed. */
    private fun GuestEntity.applyUpdate(update: GuestProperties): Boolean {
        var changed = false

        fun <T> change(new: T, current: T, assign: (T) -> Unit) {
            if (new != current) {
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

    /** Sets every writable property of a new guest. */
    private fun GuestEntity.assign(properties: GuestProperties) {
        firstName = properties.firstName
        lastName = properties.lastName
        birthYear = properties.birthYear
        gender = properties.gender
        email = properties.email
        isFamily = properties.isFamily
        role = properties.role
    }
}
