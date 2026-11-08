package no.slomic.smarthytte.guests

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** `created_by`/`updated_by` for guests and avatars, and the avatar version time. */
class GuestRepositoryAuditTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository: GuestRepository = SqliteGuestRepository()

        "create should record the actor in created_by and leave the update columns empty" {
            repository.addOrUpdate(guest, actor = "ola")

            val stored = repository.guestById(guest.id).shouldNotBeNull()
            stored.createdBy shouldBe "ola"
            stored.updatedBy.shouldBeNull()
            stored.updatedTime.shouldBeNull()
            stored.version shouldBe 1
        }

        "create without an actor should record the system actor" {
            repository.addOrUpdate(guest)

            repository.guestById(guest.id)!!.createdBy shouldBe SYSTEM_ACTOR
        }

        "upsert with a change should record the actor in updated_by" {
            repository.addOrUpdate(guest, actor = "ola")
            repository.addOrUpdate(guest.copy(firstName = "Changed"), actor = "kari") shouldBe PersistenceResult.UPDATED

            val stored = repository.guestById(guest.id)!!
            stored.createdBy shouldBe "ola"
            stored.updatedBy shouldBe "kari"
            stored.updatedTime.shouldNotBeNull()
            stored.version shouldBe 2
        }

        "update from the API should record the actor in updated_by and an unchanged update should not" {
            repository.addOrUpdate(guest, actor = "ola")

            repository.updateCurrent(guest.id, GuestChange(firstName = guest.firstName), "kari") shouldBe
                GuestUpdateOutcome.Unchanged
            repository.guestById(guest.id)!!.updatedBy.shouldBeNull()

            repository.updateCurrent(guest.id, GuestChange(firstName = "Changed"), "kari") shouldBe
                GuestUpdateOutcome.Updated
            repository.guestById(guest.id)!!.updatedBy shouldBe "kari"
        }

        "first avatar should record created_by, use created_time as version time and leave the update columns empty" {
            repository.addOrUpdate(guest)

            repository.saveAvatar(guest.id, byteArrayOf(1, 2, 3), "ola") shouldBe PersistenceResult.ADDED

            val row = transaction { GuestAvatarTable.selectAll().single() }
            row[GuestAvatarTable.createdBy] shouldBe "ola"
            row[GuestAvatarTable.updatedTime].shouldBeNull()
            row[GuestAvatarTable.updatedBy].shouldBeNull()
            repository.avatarById(guest.id)!!.versionTime shouldBe row[GuestAvatarTable.createdTime]
            repository.allAvatarVersionTimes() shouldBe mapOf(guest.id to row[GuestAvatarTable.createdTime])
        }

        "replacing the avatar should record updated_by, move the version time and keep the guest row untouched" {
            repository.addOrUpdate(guest)
            repository.saveAvatar(guest.id, byteArrayOf(1, 2, 3), "ola")

            repository.saveAvatar(guest.id, byteArrayOf(4, 5, 6), "kari") shouldBe PersistenceResult.UPDATED

            val row = transaction { GuestAvatarTable.selectAll().single() }
            row[GuestAvatarTable.createdBy] shouldBe "ola"
            row[GuestAvatarTable.updatedBy] shouldBe "kari"
            repository.avatarById(guest.id)!!.versionTime shouldBe row[GuestAvatarTable.updatedTime]
            repository.avatarById(guest.id)!!.image shouldBe byteArrayOf(4, 5, 6)
            repository.guestById(guest.id)!!.version shouldBe 1
        }

        "avatar version should start at 1, be bumped by a replacement only and not touch the guest row" {
            repository.addOrUpdate(guest)
            repository.saveAvatar(guest.id, byteArrayOf(1), "ola")
            transaction { GuestAvatarEntity.findById(guest.id)!!.version } shouldBe 1

            repository.saveAvatar(guest.id, byteArrayOf(1), "ola")
            transaction { GuestAvatarEntity.findById(guest.id)!!.version } shouldBe 1

            repository.saveAvatar(guest.id, byteArrayOf(2), "kari")
            transaction { GuestAvatarEntity.findById(guest.id)!!.version } shouldBe 2
            repository.guestById(guest.id)!!.version shouldBe 1
        }

        "saving the same avatar again should do nothing" {
            repository.addOrUpdate(guest)
            repository.saveAvatar(guest.id, byteArrayOf(1, 2, 3), "ola")

            repository.saveAvatar(guest.id, byteArrayOf(1, 2, 3), "kari") shouldBe PersistenceResult.NO_ACTION

            transaction { GuestAvatarTable.selectAll().single() }[GuestAvatarTable.updatedBy].shouldBeNull()
        }

        "saving an avatar for an unknown guest should do nothing" {
            repository.saveAvatar("unknown", byteArrayOf(1, 2, 3), "ola") shouldBe PersistenceResult.NO_ACTION

            transaction { GuestAvatarTable.selectAll().count() } shouldBe 0
        }
    })
