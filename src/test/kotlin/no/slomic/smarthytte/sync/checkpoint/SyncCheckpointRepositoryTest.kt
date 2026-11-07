package no.slomic.smarthytte.sync.checkpoint

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SyncCheckpointRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest {
            testDbSetup.setupDb()
        }

        afterTest {
            testDbSetup.teardownDb()
        }

        val repository: SyncCheckpointRepository = SqliteSyncCheckpointRepository()
        val checkPointId = "foo-bar"
        val checkPointValue = "12345"

        "add or update should record the actor in created_by and, for a change, in updated_by" {
            repository.addOrUpdateCheckpoint(checkPointId, checkPointValue, "first")
            transaction {
                val created = SyncCheckpointEntity.all().single()
                created.createdBy shouldBe "first"
                created.updatedBy.shouldBeNull()
            }

            repository.addOrUpdateCheckpoint(checkPointId, "changed", "second")
            repository.addOrUpdateCheckpoint(checkPointId, "changed", "third")

            transaction {
                val updated = SyncCheckpointEntity.all().single()
                updated.createdBy shouldBe "first"
                updated.updatedBy shouldBe "second"
                updated.version shouldBe 2
            }
        }

        "add or update with new id should add new checkpoint and return persistence result ADDED" {
            val persistenceResult = repository.addOrUpdateCheckpoint(checkPointId, checkPointValue, SYSTEM_ACTOR)
            persistenceResult shouldBe PersistenceResult.ADDED

            transaction {
                val allSyncCheckpoints: List<SyncCheckpointEntity> = SyncCheckpointEntity.all().toList()
                allSyncCheckpoints shouldHaveSize 1

                val storedSyncCheckpoint: SyncCheckpointEntity = allSyncCheckpoints.first()
                storedSyncCheckpoint.shouldBeEqualTo(
                    id = checkPointId,
                    checkpointValue = checkPointValue,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "add or update with existing id should update the existing checkpoint and return persistence result UPDATED" {
            repository.addOrUpdateCheckpoint(checkPointId, checkPointValue, SYSTEM_ACTOR)

            val updatedCheckPointValue = "new-value"
            val updatePersistenceResult = repository.addOrUpdateCheckpoint(
                checkPointId,
                updatedCheckPointValue,
                SYSTEM_ACTOR,
            )
            updatePersistenceResult shouldBe PersistenceResult.UPDATED

            transaction {
                val allSyncCheckpoints: List<SyncCheckpointEntity> = SyncCheckpointEntity.all().toList()
                allSyncCheckpoints shouldHaveSize 1

                val storedSyncCheckpoint: SyncCheckpointEntity = allSyncCheckpoints.first()
                storedSyncCheckpoint.shouldBeEqualTo(
                    id = checkPointId,
                    checkpointValue = updatedCheckPointValue,
                    expectedVersion = 2,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = false,
                )
            }
        }

        "update existing checkpoint without property changes should not update and return NO_ACTION" {
            repository.addOrUpdateCheckpoint(checkPointId, checkPointValue, SYSTEM_ACTOR)
            val updatePersistenceResult = repository.addOrUpdateCheckpoint(checkPointId, checkPointValue, SYSTEM_ACTOR)
            updatePersistenceResult shouldBe PersistenceResult.NO_ACTION

            transaction {
                val allSyncCheckpoints: List<SyncCheckpointEntity> = SyncCheckpointEntity.all().toList()
                allSyncCheckpoints shouldHaveSize 1

                val storedSyncCheckpoint: SyncCheckpointEntity = allSyncCheckpoints.first()
                storedSyncCheckpoint.shouldBeEqualTo(
                    id = checkPointId,
                    checkpointValue = checkPointValue,
                    expectedVersion = 1,
                    shouldCreatedTimeBeNull = false,
                    shouldUpdatedTimeBeNull = true,
                )
            }
        }

        "reading non existing checkpoint should return null" {
            val storedCheckpoint: String? = repository.checkpointById("non-existing-id")
            storedCheckpoint.shouldBeNull()
        }

        "reading existing sync checkpoint should return the checkpoint value" {
            repository.addOrUpdateCheckpoint(checkPointId, checkPointValue, SYSTEM_ACTOR)

            val storedCheckpoint: String? = repository.checkpointById(checkPointId)
            storedCheckpoint shouldBe checkPointValue
        }

        "deleting non existing checkpoint should return persistence result NO_ACTION" {
            val persistenceResult = repository.deleteCheckpoint("non-existing-id")
            persistenceResult shouldBe PersistenceResult.NO_ACTION
        }

        "deleting existing sync checkpoint should delete existing checkpoint and return persistence result DELETED" {
            repository.addOrUpdateCheckpoint(checkPointId, checkPointValue, SYSTEM_ACTOR)
            val persistenceResult = repository.deleteCheckpoint(checkPointId)

            persistenceResult shouldBe PersistenceResult.DELETED

            transaction {
                val allSyncCheckpoints: List<SyncCheckpointEntity> = SyncCheckpointEntity.all().toList()
                allSyncCheckpoints shouldHaveSize 0
            }
        }
    })

private fun SyncCheckpointEntity.shouldBeEqualTo(
    id: String,
    checkpointValue: String,
    expectedVersion: Short,
    shouldCreatedTimeBeNull: Boolean,
    shouldUpdatedTimeBeNull: Boolean,
) {
    this.id.value shouldBe id
    this.checkpointValue shouldBe checkpointValue

    if (shouldCreatedTimeBeNull) {
        createdTime.shouldBeNull()
    } else {
        createdTime.shouldNotBeNull()
    }

    if (shouldUpdatedTimeBeNull) {
        updatedTime.shouldBeNull()
    } else {
        updatedTime.shouldNotBeNull()
    }

    version shouldBe expectedVersion
}
