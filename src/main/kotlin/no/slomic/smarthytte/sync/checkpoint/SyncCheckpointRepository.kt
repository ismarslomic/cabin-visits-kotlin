package no.slomic.smarthytte.sync.checkpoint

import no.slomic.smarthytte.common.PersistenceResult

interface SyncCheckpointRepository {
    suspend fun checkpointById(id: String): String?
    suspend fun addOrUpdateCheckpoint(id: String, value: String): PersistenceResult
    suspend fun deleteCheckpoint(id: String): PersistenceResult
}
