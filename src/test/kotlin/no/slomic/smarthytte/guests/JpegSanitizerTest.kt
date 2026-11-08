package no.slomic.smarthytte.guests

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.slomic.smarthytte.guests.JpegTestFiles.adobe
import no.slomic.smarthytte.guests.JpegTestFiles.comment
import no.slomic.smarthytte.guests.JpegTestFiles.dht
import no.slomic.smarthytte.guests.JpegTestFiles.dqt
import no.slomic.smarthytte.guests.JpegTestFiles.dri
import no.slomic.smarthytte.guests.JpegTestFiles.eoi
import no.slomic.smarthytte.guests.JpegTestFiles.exif
import no.slomic.smarthytte.guests.JpegTestFiles.icc
import no.slomic.smarthytte.guests.JpegTestFiles.jfif
import no.slomic.smarthytte.guests.JpegTestFiles.jfxx
import no.slomic.smarthytte.guests.JpegTestFiles.jpeg
import no.slomic.smarthytte.guests.JpegTestFiles.mpf
import no.slomic.smarthytte.guests.JpegTestFiles.scanData
import no.slomic.smarthytte.guests.JpegTestFiles.segment
import no.slomic.smarthytte.guests.JpegTestFiles.sof
import no.slomic.smarthytte.guests.JpegTestFiles.sos
import no.slomic.smarthytte.guests.JpegTestFiles.xmp

private fun sanitized(input: ByteArray): ByteArray =
    sanitizeJpeg(input).shouldBeInstanceOf<JpegSanitizeResult.Sanitized>().bytes

private fun invalid(input: ByteArray): String =
    sanitizeJpeg(input).shouldBeInstanceOf<JpegSanitizeResult.Invalid>().reason

private infix fun ByteArray.shouldHaveBytes(expected: ByteArray) {
    toList() shouldBe expected.toList()
}

class JpegSanitizerTest :
    StringSpec({
        "a file without metadata is unchanged" {
            sanitized(JpegTestFiles.bare()) shouldHaveBytes JpegTestFiles.bare()
        }

        "Exif, XMP, comments and other application segments are removed, the pixel data is identical" {
            val input = JpegTestFiles.image(exif(), xmp(), comment(), JpegTestFiles.app(0xED, "Photoshop 3.0\u0000"))

            sanitized(input) shouldHaveBytes JpegTestFiles.bare()
        }

        "a plain JFIF, an ICC profile and Adobe APP14 are kept" {
            val input = JpegTestFiles.image(jfif(), exif(), icc(), adobe())

            sanitized(input) shouldHaveBytes JpegTestFiles.image(jfif(), icc(), adobe())
        }

        "a JFXX thumbnail and the MPF segment are removed" {
            sanitized(JpegTestFiles.image(jfxx(), mpf())) shouldHaveBytes JpegTestFiles.bare()
        }

        "an application segment shorter than its identifier is removed" {
            val short = segment(0xE2, byteArrayOf(1, 2))

            sanitized(JpegTestFiles.image(short)) shouldHaveBytes JpegTestFiles.bare()
        }

        "everything after the first end-of-image marker is cut off, such as the extra images of an iPhone" {
            val input = JpegTestFiles.image(exif()) + JpegTestFiles.image(exif())

            sanitized(input) shouldHaveBytes JpegTestFiles.bare()
        }

        "a progressive file with several scans is kept whole" {
            val input = jpeg(dqt(), sof(marker = 0xC2), dht(), sos(), dht(), sos(byteArrayOf(9, 8, 7)), eoi)

            sanitized(exifBefore(input)) shouldHaveBytes input
        }

        "restart markers, a restart interval and a stuffed byte stay in the scan" {
            val input = jpeg(dqt(), dri(), sof(), dht(), sos(scanData), eoi)

            sanitized(input) shouldHaveBytes input
        }

        "fill bytes before a marker are skipped" {
            val input = jpeg(dqt(), byteArrayOf(0xFF.toByte(), 0xFF.toByte()), sof(), dht(), sos(), eoi)

            sanitized(input) shouldHaveBytes JpegTestFiles.bare()
        }

        "fill bytes at the end of a scan are part of the scan" {
            val fill = byteArrayOf(5, 0xFF.toByte(), 0xFF.toByte())
            val input = jpeg(dqt(), sof(), dht(), sos(fill), eoi)

            sanitized(input) shouldHaveBytes input
        }

        "a frame of exactly 4000 x 4000 pixels is accepted" {
            val input = JpegTestFiles.image(width = 4000, height = 4000)

            sanitized(input) shouldHaveBytes input
        }

        "a frame wider or taller than 4000 pixels is rejected" {
            invalid(JpegTestFiles.image(width = 4001, height = 10)) shouldBe
                "The image is larger than 4000 x 4000 pixels"
            invalid(JpegTestFiles.image(width = 10, height = 4001)) shouldBe
                "The image is larger than 4000 x 4000 pixels"
        }

        "a frame with width 0 is rejected and height 0 is accepted (DNL gives it)" {
            invalid(JpegTestFiles.image(width = 0)) shouldBe "The image is larger than 4000 x 4000 pixels"
            sanitizeJpeg(JpegTestFiles.image(height = 0)).shouldBeInstanceOf<JpegSanitizeResult.Sanitized>()
        }

        "a too short frame header is rejected" {
            invalid(jpeg(dqt(), segment(0xC0, byteArrayOf(8, 0)), sos(), eoi)) shouldBe
                "The JPEG frame header is too short"
        }

        "bytes that are not a JPEG are rejected" {
            invalid(byteArrayOf()) shouldBe "The image is not a JPEG"
            invalid("GIF89a not a jpeg".encodeToByteArray()) shouldBe "The image is not a JPEG"
            invalid(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) shouldBe "The image is not a JPEG"
        }

        "a file without end-of-image marker is rejected" {
            invalid(JpegTestFiles.bare().dropLast(2).toByteArray()) shouldBe "The JPEG is cut off in the image data"
            invalid(jpeg(dqt(), sof(), dht())) shouldBe "The JPEG is cut off or malformed"
        }

        "a scan that ends on a lone FF is rejected" {
            invalid(jpeg(dqt(), sof(), sos(byteArrayOf(1, 0xFF.toByte())))) shouldBe
                "The JPEG is cut off in the image data"
        }

        "a segment that runs past the end is rejected" {
            val truncated = JpegTestFiles.image(exif()).take(10).toByteArray()

            invalid(truncated) shouldBe "A JPEG segment has an invalid length"
            invalid(jpeg(byteArrayOf(0xFF.toByte(), 0xDB.toByte(), 0))) shouldBe
                "The JPEG is cut off in a segment header"
        }

        "a segment with a length below 2 is rejected" {
            invalid(jpeg(byteArrayOf(0xFF.toByte(), 0xDB.toByte(), 0, 1, 0, 0))) shouldBe
                "A JPEG segment has an invalid length"
        }

        "an unknown marker is rejected" {
            invalid(jpeg(segment(0xC8, byteArrayOf(0)), sof(), sos(), eoi)) shouldBe
                "The JPEG has an unsupported segment"
            invalid(jpeg(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0))) shouldBe
                "The JPEG has an unsupported segment"
        }

        "a file with a frame but no scan, or a scan but no frame, is rejected" {
            invalid(jpeg(dqt(), sof(), eoi)) shouldBe "The JPEG has no image data"
            invalid(jpeg(dqt(), sos(), eoi)) shouldBe "The JPEG has no image data"
            invalid(jpeg(eoi)) shouldBe "The JPEG has no image data"
        }
    })

/** The same file with an Exif segment first, which must be removed. */
private fun exifBefore(input: ByteArray): ByteArray = jpeg(exif(), input.drop(2).toByteArray())
