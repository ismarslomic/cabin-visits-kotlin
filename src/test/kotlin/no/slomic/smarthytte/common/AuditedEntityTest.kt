package no.slomic.smarthytte.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.sync.checkpoint.SyncCheckpointEntity
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock

/** The two functions are the only place that set version, created_* and updated_* of every audited entity. */
class AuditedEntityTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        "markCreated should set version 1, created time and created by and leave the update columns empty" {
            val before = Clock.System.now().truncatedToMillis()

            transaction {
                SyncCheckpointEntity.new("id") {
                    markCreated("creator")
                    checkpointValue = "value"
                }
            }

            transaction {
                val entity = SyncCheckpointEntity.findById("id")!!
                entity.version shouldBe 1
                entity.createdBy shouldBe "creator"
                entity.createdTime shouldBeGreaterThanOrEqualTo before
                entity.updatedBy.shouldBeNull()
                entity.updatedTime.shouldBeNull()
            }
        }

        "markUpdated should bump the version on every call and set updated time and updated by" {
            transaction {
                SyncCheckpointEntity.new("id") {
                    markCreated("creator")
                    checkpointValue = "value"
                }
            }

            transaction { SyncCheckpointEntity.findById("id")!!.markUpdated("first") }
            transaction { SyncCheckpointEntity.findById("id")!!.markUpdated("second") }

            transaction {
                val entity = SyncCheckpointEntity.findById("id")!!
                entity.version shouldBe 3
                entity.createdBy shouldBe "creator"
                entity.updatedBy shouldBe "second"
                entity.updatedTime!! shouldBeGreaterThanOrEqualTo entity.createdTime
            }
        }
    })
