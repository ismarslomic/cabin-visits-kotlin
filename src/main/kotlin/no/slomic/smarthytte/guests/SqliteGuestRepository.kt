@file:Suppress("TooManyFunctions")

package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
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

    override suspend fun update(guestId: String, update: GuestUpdate): PersistenceResult = suspendTransaction {
        logger.trace("Updating guest with id: $guestId from the API")

        val storedGuest: GuestEntity =
            GuestEntity.findById(guestId) ?: return@suspendTransaction PersistenceResult.NO_ACTION

        val changedFields: Set<GuestField> = storedGuest.applyUpdate(update)

        if (changedFields.isEmpty()) {
            logger.trace("No changes detected for guest with id: $guestId")
            PersistenceResult.NO_ACTION
        } else {
            storedGuest.apiEditedFields = (storedGuest.editedFields() + changedFields).joinToString(",") { it.name }
            storedGuest.version = storedGuest.version.inc()
            storedGuest.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace("Updated guest with id: $guestId, changed fields: $changedFields")
            PersistenceResult.UPDATED
        }
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

    override suspend fun avatarById(guestId: String): GuestAvatar? = suspendTransaction {
        GuestAvatarTable
            .selectAll()
            .where { GuestAvatarTable.guest eq guestId }
            .singleOrNull()
            ?.let { GuestAvatar(it[GuestAvatarTable.image].bytes, it[GuestAvatarTable.updatedTime]) }
    }

    override suspend fun allAvatarUpdatedTimes(): Map<String, Instant> = suspendTransaction {
        GuestAvatarTable
            .select(GuestAvatarTable.guest, GuestAvatarTable.updatedTime)
            .associate { it[GuestAvatarTable.guest].value to it[GuestAvatarTable.updatedTime] }
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
            appRole = guest.appRole
            createdTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Added guest with id: ${guest.id}")
        return PersistenceResult.ADDED
    }

    /**
     * Fields that have been changed through the API are not overwritten by the file.
     *
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * guest.
     */
    private fun updateGuest(guest: Guest): PersistenceResult {
        logger.trace("Updating guest with id: ${guest.id}")

        val updatedGuest: GuestEntity = GuestEntity.findById(guest.id) ?: return PersistenceResult.NO_ACTION

        updatedGuest.applyFromFile(guest)

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

    private fun GuestEntity.editedFields(): Set<GuestField> = apiEditedFields
        ?.split(',')
        ?.mapNotNull { name -> GuestField.entries.find { it.name == name } }
        ?.toSet()
        .orEmpty()

    private fun GuestEntity.applyFromFile(guest: Guest) {
        val edited: Set<GuestField> = editedFields()

        if (GuestField.FIRST_NAME !in edited) firstName = guest.firstName
        if (GuestField.LAST_NAME !in edited) lastName = guest.lastName
        if (GuestField.BIRTH_YEAR !in edited) birthYear = guest.birthYear
        if (GuestField.GENDER !in edited) gender = guest.gender
        if (GuestField.EMAIL !in edited) email = guest.email
        if (GuestField.IS_FAMILY !in edited) isFamily = guest.isFamily
        if (GuestField.APP_ROLE !in edited) appRole = guest.appRole
    }

    /** Sets the properties of [update] that differ from the stored ones, and returns the fields that changed. */
    private fun GuestEntity.applyUpdate(update: GuestUpdate): Set<GuestField> = buildSet {
        fun <T : Any> change(field: GuestField, new: T?, current: T?, assign: (T) -> Unit) {
            if (new != null && new != current) {
                assign(new)
                add(field)
            }
        }

        change(GuestField.FIRST_NAME, update.firstName, firstName) { firstName = it }
        change(GuestField.LAST_NAME, update.lastName, lastName) { lastName = it }
        change(GuestField.BIRTH_YEAR, update.birthYear, birthYear) { birthYear = it }
        change(GuestField.GENDER, update.gender, gender) { gender = it }
        change(GuestField.EMAIL, update.email, email) { email = it }
        change(GuestField.IS_FAMILY, update.isFamily, isFamily) { isFamily = it }
        change(GuestField.APP_ROLE, update.appRole, appRole) { appRole = it }
    }
}
