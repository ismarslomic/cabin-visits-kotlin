@file:Suppress("MagicNumber")

package no.slomic.smarthytte.guests

/** Builds JPEG files byte by byte: there are no image files in the test resources. */
object JpegTestFiles {
    val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    /** Entropy-coded data with a stuffed byte (FF00) and a restart marker (FFD0). */
    val scanData = byteArrayOf(0x12, 0xFF.toByte(), 0x00, 0x34, 0xFF.toByte(), 0xD0.toByte(), 0x56)

    fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    fun app(marker: Int, identifier: String, extra: Int = 4): ByteArray =
        segment(marker, identifier.encodeToByteArray() + ByteArray(extra) { 0x7 })

    fun jfif(): ByteArray = app(0xE0, "JFIF\u0000")
    fun jfxx(): ByteArray = app(0xE0, "JFXX\u0000")
    fun exif(): ByteArray = app(0xE1, "Exif\u0000\u0000GPS")
    fun xmp(): ByteArray = app(0xE1, "http://ns.adobe.com/xap/1.0/\u0000")
    fun icc(): ByteArray = app(0xE2, "ICC_PROFILE\u0000")
    fun mpf(): ByteArray = app(0xE2, "MPF\u0000")
    fun adobe(): ByteArray = app(0xEE, "Adobe")
    fun comment(): ByteArray = segment(0xFE, "hello".encodeToByteArray())
    fun dqt(): ByteArray = segment(0xDB, ByteArray(65) { 1 })
    fun dht(): ByteArray = segment(0xC4, ByteArray(20) { 2 })
    fun dri(): ByteArray = segment(0xDD, byteArrayOf(0, 4))

    fun sof(marker: Int = 0xC0, width: Int = 8, height: Int = 8): ByteArray = segment(
        marker,
        byteArrayOf(
            8,
            (height shr 8).toByte(),
            height.toByte(),
            (width shr 8).toByte(),
            width.toByte(),
            1,
            1,
            0x11,
            0,
        ),
    )

    fun sos(data: ByteArray = scanData): ByteArray = segment(0xDA, byteArrayOf(1, 1, 0, 0, 63, 0)) + data

    fun jpeg(vararg parts: ByteArray): ByteArray = parts.fold(soi) { acc, part -> acc + part }

    /** A baseline file: the segments that decode the image, in the order a camera writes them. */
    fun image(vararg metadata: ByteArray, width: Int = 8, height: Int = 8): ByteArray =
        jpeg(*metadata, dqt(), sof(width = width, height = height), dht(), sos(), eoi)

    /** The same file without any metadata. */
    fun bare(): ByteArray = image()
}
