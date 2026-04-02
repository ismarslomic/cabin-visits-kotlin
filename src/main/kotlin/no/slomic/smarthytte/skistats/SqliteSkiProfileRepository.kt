package no.slomic.smarthytte.skistats

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import kotlin.time.Clock

class SqliteSkiProfileRepository : SkiProfileRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteSkiProfileRepository::class.java.name)

    override suspend fun addOrUpdateProfile(profile: SkiProfile): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(id = profile.id, table = SkiProfileTable)
        val stored: SkiProfileEntity? = SkiProfileEntity.findById(entityId)

        if (stored == null) {
            addProfile(profile)
        } else {
            updateProfile(profile)
        }
    }

    private fun addProfile(profile: SkiProfile): PersistenceResult {
        logger.trace("Adding ski profile with id: ${profile.id}")

        SkiProfileEntity.new(profile.id) {
            name = profile.name
            profileImageUrl = profile.profileImageUrl
            isPrivate = profile.isPrivate
            createdTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Added ski profile with id: ${profile.id}")
        return PersistenceResult.ADDED
    }

    private fun updateProfile(profile: SkiProfile): PersistenceResult {
        logger.trace("Updating ski profile with id: ${profile.id}")

        val stored: SkiProfileEntity = SkiProfileEntity.findById(profile.id) ?: return PersistenceResult.NO_ACTION

        with(stored) {
            name = profile.name
            profileImageUrl = profile.profileImageUrl
            isPrivate = profile.isPrivate
        }

        val isDirty: Boolean = stored.writeValues.isNotEmpty()

        return if (isDirty) {
            stored.version = stored.version.inc()
            stored.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace("Updated ski profile with id: ${profile.id}")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for ski profile with id: ${profile.id}")
            PersistenceResult.NO_ACTION
        }
    }
}
