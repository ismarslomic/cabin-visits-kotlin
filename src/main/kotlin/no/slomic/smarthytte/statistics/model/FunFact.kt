@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FunFact(
    @JsonSchema.Description("Guest the fact is about. Null for facts about the whole group or the cabin.")
    @JsonSchema.Example("\"ola.nordmann\"")
    val guestId: String?,
    @JsonSchema.Description("Ready-to-display text in Norwegian. Always equal to the segments joined together.")
    @JsonSchema.Example("\"Ola har vært på hytta 12 ganger\"")
    val text: String,
    @JsonSchema.Description(
        "Importance from 0 to 100, higher is more important. Lists are sorted by priority, descending.",
    )
    @JsonSchema.Example("80")
    val priority: Int,
    @JsonSchema.Description(
        "The text split into parts for highlighting. Joined together they give the text. " +
            "Clients should treat an unknown kind as plain text.",
    )
    val segments: List<FactSegment> = emptyList(),
)

@Serializable
data class FactSegment(
    @JsonSchema.Description("Part of the text.")
    @JsonSchema.Example("\"12 ganger\"")
    val text: String,
    @JsonSchema.Description("What the part is, so the client can style it. Omitted for plain text.")
    @JsonSchema.Example("\"stat\"")
    val kind: SegmentKind = SegmentKind.TEXT,
)

@Serializable
enum class SegmentKind {
    @SerialName("text")
    TEXT,

    @SerialName("stat")
    STAT,

    @SerialName("guest")
    GUEST,
}
