@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.statistics.model

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@Serializable
data class FunFact(
    @JsonSchema.Description("Guest the fact is about. Null for facts about the whole group or the cabin.")
    val guestId: String?,
    @JsonSchema.Description("Ready-to-display text in Norwegian.")
    val text: String,
    @JsonSchema.Description(
        "Importance from 0 to 100, higher is more important. Lists are sorted by priority, descending.",
    )
    val priority: Int,
)
