package no.slomic.smarthytte.guests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.slomic.smarthytte.properties.GuestProperties
import no.slomic.smarthytte.properties.GuestPropertiesHolder
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.getResourceFilePath
import java.io.File
import java.nio.file.Files

class GuestServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository = SqliteGuestRepository()
        val avatarsDirectory: String = Files.createTempDirectory("avatars").toString()

        fun serviceFor(filePath: String) =
            GuestService(repository, GuestPropertiesHolder(GuestProperties(filePath, avatarsDirectory)))

        fun guestsFile(vararg emails: String?): String {
            val guests = emails.mapIndexed { index, email ->
                val emailJson = email?.let { ""","email":"$it"""" }.orEmpty()
                """{"id":"g$index","firstName":"First","lastName":"Last","birthYear":1980,"gender":"MALE"$emailJson}"""
            }
            return Files.createTempFile("guests", ".json").also {
                it.toFile().writeText(guests.joinToString(",", "[", "]"))
            }
                .toString()
        }

        "guests from the file should be stored, with or without an email" {
            serviceFor(getResourceFilePath("guests.json")).insertGuestsFromFile()

            repository.allGuests().map { it.id } shouldContainExactlyInAnyOrder listOf("john", "john2")
        }

        "the file should be read again without changes when nothing has changed" {
            val service = serviceFor(getResourceFilePath("guests.json"))

            service.insertGuestsFromFile()
            service.insertGuestsFromFile()

            repository.allGuests().size shouldBe 2
        }

        "guests that share an email, ignoring case and whitespace, should stop the sync before anything is written" {
            val service = serviceFor(guestsFile("new@example.no", "Shared@Example.no", " shared@example.NO "))

            val error = shouldThrow<IllegalStateException> { service.insertGuestsFromFile() }

            error.message shouldContain "shared@example.no"
            repository.allGuests().shouldBeEmpty()
        }

        "many guests without an email should not be treated as sharing an email" {
            serviceFor(guestsFile(null, null, "a@example.no")).insertGuestsFromFile()

            repository.allGuests().size shouldBe 3
        }

        "a guest with an avatar file should get the avatar stored" {
            val avatar = byteArrayOf(1, 2, 3)
            File(avatarsDirectory, "john.jpg").writeBytes(avatar)

            serviceFor(getResourceFilePath("guests.json")).insertGuestsFromFile()

            repository.avatarById("john")!!.image shouldBe avatar
            repository.avatarById("john2") shouldBe null
        }
    })
