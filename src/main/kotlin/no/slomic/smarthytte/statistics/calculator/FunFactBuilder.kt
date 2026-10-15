package no.slomic.smarthytte.statistics.calculator

import no.slomic.smarthytte.statistics.model.FactSegment
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.SegmentKind

/** Builds a [FunFact] from segments, so [FunFact.text] is always the segments joined together. */
fun funFact(guestId: String?, priority: Int, build: FunFactBuilder.() -> Unit): FunFact {
    val segments = FunFactBuilder().apply(build).segments
    return FunFact(guestId, segments.joinToString("") { it.text }, priority, segments)
}

class FunFactBuilder {
    internal val segments = mutableListOf<FactSegment>()

    /** Plain text. */
    fun text(value: String) = add(value, SegmentKind.TEXT)

    /** A number, duration, date or other fact to highlight. */
    fun stat(value: Any) = add(value.toString(), SegmentKind.STAT)

    /** The name of a guest. */
    fun guestName(name: String) = add(name, SegmentKind.GUEST)

    private fun add(value: String, kind: SegmentKind) {
        if (value.isNotEmpty()) segments += FactSegment(value, kind)
    }
}
