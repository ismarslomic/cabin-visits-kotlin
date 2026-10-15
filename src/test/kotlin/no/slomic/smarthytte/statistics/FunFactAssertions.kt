package no.slomic.smarthytte.statistics

import io.kotest.matchers.collections.shouldContain
import no.slomic.smarthytte.statistics.model.FunFact

/** Asserts guest, text and priority only. Segment styling is covered by [FunFactBuilderTest]. */
infix fun Collection<FunFact>.shouldContainFact(expected: FunFact) {
    map { it.withoutSegments() } shouldContain expected.withoutSegments()
}

private fun FunFact.withoutSegments() = copy(segments = emptyList())
