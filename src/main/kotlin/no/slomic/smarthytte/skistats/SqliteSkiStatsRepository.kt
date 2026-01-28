package no.slomic.smarthytte.skistats

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import kotlin.time.Clock

class SqliteSkiStatsRepository : SkiStatsRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteSkiStatsRepository::class.java.name)

    override suspend fun tokensByProfile(id: String): SkiStatsTokens? =
        suspendTransaction { SkiStatsTokenEntity.findById(id)?.let(::daoToModel) }

    override suspend fun addOrUpdateTokens(id: String, tokens: SkiStatsTokens): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(id = id, table = SkiStatsTokenTable)
        val storedProfileTokens: SkiStatsTokenEntity? = SkiStatsTokenEntity.findById(entityId)

        if (storedProfileTokens == null) {
            addProfileTokens(id, tokens)
        } else {
            updateProfileTokens(id, tokens)
        }
    }

    private fun addProfileTokens(id: String, tokens: SkiStatsTokens): PersistenceResult {
        logger.trace("Adding profile tokens with id: $id")

        SkiStatsTokenEntity.new(id) {
            accessToken = tokens.accessToken
            refreshToken = tokens.refreshToken
            expiresAtEpochSeconds = tokens.expiresAtEpochSeconds
            createdTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Added profile tokens with id: $id")
        return PersistenceResult.ADDED
    }

    private fun updateProfileTokens(id: String, tokens: SkiStatsTokens): PersistenceResult {
        logger.trace("Updating profile tokens with id: $id")

        val storedProfileTokens: SkiStatsTokenEntity =
            SkiStatsTokenEntity.findById(id) ?: return PersistenceResult.NO_ACTION

        with(storedProfileTokens) {
            accessToken = tokens.accessToken
            refreshToken = tokens.refreshToken
            expiresAtEpochSeconds = tokens.expiresAtEpochSeconds
        }

        val isDirty: Boolean = storedProfileTokens.writeValues.isNotEmpty()

        return if (isDirty) {
            storedProfileTokens.version = storedProfileTokens.version.inc()
            storedProfileTokens.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace("Updated profile tokens with id: $id")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for profile tokens with id: $id")
            PersistenceResult.NO_ACTION
        }
    }
}
