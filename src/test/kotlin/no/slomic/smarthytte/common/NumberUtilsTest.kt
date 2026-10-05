package no.slomic.smarthytte.common

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class NumberUtilsTest :
    ShouldSpec({
        context("round1") {
            should("round halves up") { 1.25.round1() shouldBe 1.3 }
            should("round down below a half") { 1.24.round1() shouldBe 1.2 }
            should("keep a value that already has one decimal") { 2.0.round1() shouldBe 2.0 }
            should("round negative halves away from zero") { (-1.25).round1() shouldBe -1.3 }
        }

        context("averageOrNullInt") {
            should("return null for an empty list") { emptyList<Int>().averageOrNullInt() shouldBe null }
            should("return the average") { listOf(10, 20, 30).averageOrNullInt() shouldBe 20 }
            should("truncate the decimals") { listOf(1, 2).averageOrNullInt() shouldBe 1 }
        }

        context("toNorwegianDecimal") {
            should("use a decimal comma") { 1.5.toNorwegianDecimal() shouldBe "1,5" }
            should("keep the trailing zero") { 2.0.toNorwegianDecimal() shouldBe "2,0" }
        }

        context("groupThousands") {
            should("not group three digits or fewer") { 999.groupThousands() shouldBe "999" }
            should("group thousands") { 1000.groupThousands() shouldBe "1 000" }
            should("group several thousands") { 1_234_567.groupThousands() shouldBe "1 234 567" }
        }
    })
