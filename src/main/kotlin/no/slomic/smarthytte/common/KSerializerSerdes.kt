package no.slomic.smarthytte.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object StringToDoubleSerde : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringToDouble", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Double {
        val stringValue = decoder.decodeString().replace(oldValue = ",", newValue = ".")
        return stringValue.toDouble()
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeString(value.toString())
    }
}

object StringToLocalDateSerde : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringToLocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate {
        val dateString = decoder.decodeString()

        val customFormat = LocalDate.Format {
            day(padding = Padding.ZERO)
            char('.')
            monthNumber(padding = Padding.ZERO)
            char('.')
            year(padding = Padding.ZERO)
        }

        return customFormat.parse(dateString)
    }

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }
}

object StringToLocalTimeSerde : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringToLocalTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalTime {
        val timeString = decoder.decodeString()
        return LocalTime.parse(timeString)
    }

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())
    }
}
