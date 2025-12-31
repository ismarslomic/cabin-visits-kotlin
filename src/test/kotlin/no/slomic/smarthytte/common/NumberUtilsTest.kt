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

        context("averageRounded1OrNull") {
            should("return null for empty list") {
                emptyList<Int>().averageRounded1OrNull() shouldBe null
            }

            should("calculate rounded average for list of integers using HALF_UP") {
                listOf(1, 2).averageRounded1OrNull() shouldBe 1.5
                listOf(1, 2, 4).averageRounded1OrNull() shouldBe 2.3 // 2.333... -> 2.3
                listOf(10).averageRounded1OrNull() shouldBe 10.0
                listOf(1, 2, 2).averageRounded1OrNull() shouldBe 1.7 // 1.666... -> 1.7
                listOf(1, 4).averageRounded1OrNull() shouldBe 2.5 // 2.5 -> 2.5
                listOf(1, 1, 3).averageRounded1OrNull() shouldBe 1.7 // 1.666... -> 1.7
            }

            should("correctly round up at the .x5 boundary (HALF_UP)") {
                // To get exactly .25, sum / size must be .25
                // e.g., 5 / 4 = 1.25 -> 1.3
                listOf(1, 1, 1, 2).averageRounded1OrNull() shouldBe 1.3

                // e.g., 7 / 4 = 1.75 -> 1.8
                listOf(1, 2, 2, 2).averageRounded1OrNull() shouldBe 1.8
            }
        }

        context("averageOrNullInt") {
            should("return null for empty list") {
                emptyList<Int>().averageOrNullInt() shouldBe null
            }

            should("calculate truncated integer average for list of integers") {
                listOf(1, 2).averageOrNullInt() shouldBe 1 // 1.5 -> 1
                listOf(1, 2, 4).averageOrNullInt() shouldBe 2 // 2.33 -> 2
                listOf(10).averageOrNullInt() shouldBe 10
            }
        }
    })
