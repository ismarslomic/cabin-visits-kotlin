package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import no.slomic.smarthytte.statistics.model.FactSegment
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.SegmentKind

class FunFactBuilderTest :
    ShouldSpec({
        context("funFact") {
            val fact = funFact("dina", 70) {
                text("Det er ")
                stat("12 måneder")
                text(" siden sist, ")
                guestName("Dina")
                text("!")
            }

            should("join the segments into the text") {
                fact.text shouldBe "Det er 12 måneder siden sist, Dina!"
            }

            should("keep the kind of each segment") {
                fact.segments shouldBe listOf(
                    FactSegment("Det er "),
                    FactSegment("12 måneder", SegmentKind.STAT),
                    FactSegment(" siden sist, "),
                    FactSegment("Dina", SegmentKind.GUEST),
                    FactSegment("!"),
                )
            }

            should("skip empty segments") {
                funFact(null, 10) {
                    text("")
                    stat(5)
                }.segments shouldBe listOf(FactSegment("5", SegmentKind.STAT))
            }

            should("serialize kinds in lowercase and omit the plain text kind") {
                Json.encodeToString(
                    FunFact.serializer(),
                    funFact(null, 10) {
                        text("a ")
                        stat("b")
                    },
                ) shouldBe
                    """{"guestId":null,"text":"a b","priority":10,"segments":""" +
                    """[{"text":"a "},{"text":"b","kind":"stat"}]}"""
            }
        }
    })
