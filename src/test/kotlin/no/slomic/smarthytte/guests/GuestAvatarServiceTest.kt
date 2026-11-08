package no.slomic.smarthytte.guests

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.utils.TestDbSetup

class GuestAvatarServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository = SqliteGuestRepository()
        val service = GuestAvatarService(repository)

        "upload should store the image without metadata, with the actor as creator" {
            repository.addOrUpdate(testGuest("ola"))

            val result = service.upload("ola", JpegTestFiles.image(JpegTestFiles.exif()), "admin")

            result shouldBe AvatarUploadResult.Saved
            repository.avatarById("ola")!!.image.toList() shouldBe JpegTestFiles.bare().toList()
        }

        "upload should replace an avatar and keep the version of the guest" {
            repository.addOrUpdate(testGuest("ola"))
            service.upload("ola", JpegTestFiles.bare(), "ola")

            service.upload("ola", JpegTestFiles.image(width = 9), "ola") shouldBe AvatarUploadResult.Saved

            repository.avatarById("ola")!!.image.toList() shouldBe JpegTestFiles.image(width = 9).toList()
            repository.guestById("ola")!!.version shouldBe 1
        }

        "upload should answer GuestNotFound for an unknown guest and store nothing" {
            service.upload("nobody", JpegTestFiles.bare(), "admin") shouldBe AvatarUploadResult.GuestNotFound

            repository.avatarById("nobody") shouldBe null
        }

        "upload should answer InvalidImage for bytes that are not a JPEG and store nothing" {
            repository.addOrUpdate(testGuest("ola"))

            val result = service.upload("ola", "not an image".encodeToByteArray(), "ola")

            result.shouldBeInstanceOf<AvatarUploadResult.InvalidImage>().reason shouldBe "The image is not a JPEG"
            repository.avatarById("ola") shouldBe null
        }
    })
