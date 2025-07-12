package no.slomic.smarthytte.sync.checkpoint

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.datetime.Clock
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.dao.id.EntityID

class SqliteSyncCheckpointRepository : SyncCheckpointRepository {
    private val logger: Logger = KtorSimpleLogger(SqliteSyncCheckpointRepository::class.java.name)

    override suspend fun checkpointById(id: String): String? =
        suspendTransaction { SyncCheckpointEntity.findById(id)?.checkpointValue }

    override suspend fun addOrUpdateCheckpoint(id: String, value: String): PersistenceResult = suspendTransaction {
        val entityId: EntityID<String> = EntityID(id = id, table = SyncCheckpointTable)
        val storedSyncCheckpoint: SyncCheckpointEntity? = SyncCheckpointEntity.findById(entityId)

        if (storedSyncCheckpoint == null) {
            addSyncCheckpoint(id, value)
        } else {
            updateCheckpoint(id, value)
        }
    }

    override suspend fun deleteCheckpoint(id: String): PersistenceResult = suspendTransaction {
        logger.trace("Deleting sync checkpoint with id: $id")

        val entityId: EntityID<String> = EntityID(id = id, table = SyncCheckpointTable)
        val storedSyncCheckpoint: SyncCheckpointEntity? = SyncCheckpointEntity.findById(entityId)

        storedSyncCheckpoint?.delete()

        val wasDeleted: Boolean = storedSyncCheckpoint != null

        return@suspendTransaction if (wasDeleted) {
            logger.trace("Deleted sync checkpoint with id: $id")
            PersistenceResult.DELETED
        } else {
            logger.warn("Sync checkpoint with id: $id was not deleted because it did not exist in database.")
            PersistenceResult.NO_ACTION
        }
    }

    private fun addSyncCheckpoint(id: String, value: String): PersistenceResult {
        logger.trace("Adding sync checkpoint with id: $id")

        SyncCheckpointEntity.new(id) {
            checkpointValue = value
            createdTime = Clock.System.now().truncatedToMillis()
        }

        logger.trace("Added sync checkpoint with id: $id")
        return PersistenceResult.ADDED
    }

    /**
     * Note that actual database update is only performed if at least one column has changed the value, so
     * calling findByIdAndUpdate is not necessary doing any update if all columns have the same value in stored and new
     * sync checkpoint.
     */
    private fun updateCheckpoint(id: String, value: String): PersistenceResult {
        logger.trace("Updating sync checkpoint with id: $id")

        val storedSyncCheckpoint: SyncCheckpointEntity =
            SyncCheckpointEntity.findById(id) ?: return PersistenceResult.NO_ACTION

        with(storedSyncCheckpoint) {
            checkpointValue = value
        }

        val isDirty: Boolean = storedSyncCheckpoint.writeValues.isNotEmpty()

        return if (isDirty) {
            storedSyncCheckpoint.version = storedSyncCheckpoint.version.inc()
            storedSyncCheckpoint.updatedTime = Clock.System.now().truncatedToMillis()

            logger.trace("Updated sync checkpoint with id: $id")
            PersistenceResult.UPDATED
        } else {
            logger.trace("No changes detected for sync checkpoint with id: $id")
            PersistenceResult.NO_ACTION
        }
    }
}
