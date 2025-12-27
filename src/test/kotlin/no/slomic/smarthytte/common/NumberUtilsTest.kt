package no.slomic.smarthytte.common

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class NumberUtilsTest :
    ShouldSpec({
        context("round1") {
            should("round positive numbers to one decimal place") {
                1.23.round1() shouldBe 1.2
                1.25.round1() shouldBe 1.3
                1.250000000001.round1() shouldBe 1.3
                1.27.round1() shouldBe 1.3
                1.35.round1() shouldBe 1.4
                1.45.round1() shouldBe 1.5
                1.0.round1() shouldBe 1.0
            }

            should("round negative numbers to one decimal place") {
                (-1.23).round1() shouldBe -1.2
                (-1.25).round1() shouldBe -1.3
                (-1.26).round1() shouldBe -1.3
                (-1.35).round1() shouldBe -1.4
            }

            should("round zero") {
                0.0.round1() shouldBe 0.0
            }
        }
    })
