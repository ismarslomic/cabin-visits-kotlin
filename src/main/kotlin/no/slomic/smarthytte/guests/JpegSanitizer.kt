@file:Suppress("MagicNumber")

package no.slomic.smarthytte.guests

import java.io.ByteArrayOutputStream

/** The largest width and height, in pixels, of an uploaded avatar. */
const val MAX_JPEG_DIMENSION = 4000

sealed interface JpegSanitizeResult {
    /** The JPEG without metadata. Not a data class because of the [ByteArray] property. */
    class Sanitized(val bytes: ByteArray) : JpegSanitizeResult

    /** The input is not a JPEG that can be stored, [reason] says why. */
    data class Invalid(val reason: String) : JpegSanitizeResult
}

/**
 * Removes the metadata (Exif with GPS and orientation, XMP, comments, maker notes, embedded thumbnails and the extra
 * images that iPhones append) from a JPEG without re-encoding it, so the pixels are byte-identical.
 *
 * An **allowlist** is used: the segments that decode the image (quantisation and Huffman tables, frame header, scans,
 * restart interval, DNL), a plain `JFIF` APP0, an ICC colour profile (APP2) and Adobe's APP14 are kept, every other
 * `APPn` and comment is dropped, and everything after the first real end-of-image marker is cut off. Unknown markers,
 * segments that run past the end, a missing end-of-image marker and a frame larger than [MAX_JPEG_DIMENSION] pixels
 * in either direction are [JpegSanitizeResult.Invalid].
 */
fun sanitizeJpeg(input: ByteArray): JpegSanitizeResult = try {
    JpegSanitizeResult.Sanitized(JpegSanitizer(input).sanitize())
} catch (e: InvalidJpegException) {
    JpegSanitizeResult.Invalid(e.reason)
}

private class InvalidJpegException(val reason: String) : Exception(reason)

private const val MARKER_PREFIX = 0xFF
private const val SOI = 0xD8
private const val EOI = 0xD9
private const val SOS = 0xDA
private const val DQT = 0xDB
private const val DNL = 0xDC
private const val DRI = 0xDD
private const val DHT = 0xC4
private const val DAC = 0xCC
private const val APP0 = 0xE0
private const val APP2 = 0xE2
private const val APP14 = 0xEE
private const val APP15 = 0xEF
private const val COM = 0xFE
private const val RESTART_FIRST = 0xD0
private const val RESTART_LAST = 0xD7
private const val SOF_FIRST = 0xC0
private const val SOF_LAST = 0xCF
private const val JPG_RESERVED = 0xC8

/** Between SOF0 and SOF15: the Huffman table (DHT), the arithmetic table (DAC) and the reserved JPG marker. */
private val NO_FRAME_MARKERS = setOf(DHT, DAC, JPG_RESERVED)

private val JFIF_IDENTIFIER = "JFIF\u0000".encodeToByteArray()
private val ICC_IDENTIFIER = "ICC_PROFILE\u0000".encodeToByteArray()
private val ADOBE_IDENTIFIER = "Adobe".encodeToByteArray()

/** Marker (2 bytes), length (2 bytes), then the payload. */
private const val SEGMENT_HEADER_SIZE = 4

/** Precision (1 byte), height (2 bytes) and width (2 bytes) of a frame header, plus the length field and components. */
private const val MIN_FRAME_LENGTH = 8

private class JpegSanitizer(private val input: ByteArray) {
    private val out = ByteArrayOutputStream(input.size)
    private var pos = 0
    private var sawFrame = false
    private var sawScan = false

    fun sanitize(): ByteArray {
        invalidUnless(input.size >= 4 && input.u8(0) == MARKER_PREFIX && input.u8(1) == SOI && input.u8(2) == 0xFF) {
            "The image is not a JPEG"
        }
        out.write(MARKER_PREFIX)
        out.write(SOI)
        pos = 2

        while (!copyNextSegment()) {
            // One segment per round, until the end-of-image marker
        }

        invalidUnless(sawFrame && sawScan) { "The JPEG has no image data" }
        return out.toByteArray()
    }

    /** Copies or drops the segment at [pos]. Returns true after the end-of-image marker, which ends the image. */
    private fun copyNextSegment(): Boolean {
        skipFillBytes()
        invalidUnless(pos + 1 < input.size && input.u8(pos) == MARKER_PREFIX) { "The JPEG is cut off or malformed" }

        val marker = input.u8(pos + 1)
        val isEnd = marker == EOI
        if (isEnd) {
            out.write(MARKER_PREFIX)
            out.write(EOI)
        } else {
            copySegment(marker)
        }
        return isEnd
    }

    private fun copySegment(marker: Int) {
        invalidUnless(isKnownMarker(marker)) { "The JPEG has an unsupported segment" }
        val length = segmentLength()
        val keep = when {
            isApplicationOrComment(marker) -> keepApplicationSegment(marker)
            else -> true
        }

        if (isFrameHeader(marker)) {
            checkFrameSize(length)
            sawFrame = true
        }

        val end = pos + 2 + length
        if (keep) out.write(input, pos, end - pos)
        pos = end

        if (marker == SOS) copyScanData()
    }

    /** The length field of the segment at [pos], which counts itself and the payload but not the marker. */
    private fun segmentLength(): Int {
        invalidUnless(pos + SEGMENT_HEADER_SIZE <= input.size) { "The JPEG is cut off in a segment header" }
        val length = input.u16(pos + 2)
        invalidUnless(length >= 2 && pos + 2 + length <= input.size) { "A JPEG segment has an invalid length" }
        return length
    }

    private fun keepApplicationSegment(marker: Int): Boolean = when (marker) {
        APP0 -> payloadStartsWith(JFIF_IDENTIFIER)
        APP2 -> payloadStartsWith(ICC_IDENTIFIER)
        APP14 -> payloadStartsWith(ADOBE_IDENTIFIER)
        else -> false
    }

    private fun payloadStartsWith(identifier: ByteArray): Boolean {
        val payloadEnd = pos + 2 + input.u16(pos + 2)
        val start = pos + SEGMENT_HEADER_SIZE
        return start + identifier.size <= payloadEnd &&
            identifier.indices.all { input[start + it] == identifier[it] }
    }

    private fun checkFrameSize(length: Int) {
        invalidUnless(length >= MIN_FRAME_LENGTH) { "The JPEG frame header is too short" }
        val height = input.u16(pos + 5)
        val width = input.u16(pos + 7)
        // Height 0 means that a DNL segment gives it, which is allowed
        invalidUnless(width in 1..MAX_JPEG_DIMENSION && height in 0..MAX_JPEG_DIMENSION) {
            "The image is larger than $MAX_JPEG_DIMENSION x $MAX_JPEG_DIMENSION pixels"
        }
    }

    /** Copies the entropy-coded data after a scan header, up to the next real marker. */
    private fun copyScanData() {
        sawScan = true
        val start = pos
        var p = pos
        while (p < input.size && !input.isRealMarkerAt(p)) {
            // FF00 is a stuffed byte, FFD0-FFD7 a restart marker and FFFF fill: all part of the scan
            p += if (input.u8(p) == MARKER_PREFIX && p + 1 < input.size && input.u8(p + 1) != MARKER_PREFIX) 2 else 1
        }
        invalidUnless(p < input.size) { "The JPEG is cut off in the image data" }
        out.write(input, start, p - start)
        pos = p
    }

    private fun skipFillBytes() {
        while (pos + 1 < input.size && input.u8(pos) == MARKER_PREFIX && input.u8(pos + 1) == MARKER_PREFIX) pos++
    }
}

private fun isKnownMarker(marker: Int): Boolean = when (marker) {
    DQT, DHT, DAC, DRI, DNL, SOS -> true
    in SOF_FIRST..SOF_LAST -> marker != JPG_RESERVED
    else -> isApplicationOrComment(marker)
}

private fun isApplicationOrComment(marker: Int): Boolean = marker in APP0..APP15 || marker == COM

/** The start-of-frame markers (SOF0 to SOF15), which are among the markers between them that are no frame header. */
private fun isFrameHeader(marker: Int): Boolean = marker in SOF_FIRST..SOF_LAST && marker !in NO_FRAME_MARKERS

private fun ByteArray.isRealMarkerAt(p: Int): Boolean {
    if (u8(p) != MARKER_PREFIX) return false
    invalidUnless(p + 1 < size) { "The JPEG is cut off in the image data" }

    val next = u8(p + 1)
    return next != 0 && next != MARKER_PREFIX && next !in RESTART_FIRST..RESTART_LAST
}

private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.u16(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

private inline fun invalidUnless(condition: Boolean, reason: () -> String) {
    if (!condition) throw InvalidJpegException(reason())
}
