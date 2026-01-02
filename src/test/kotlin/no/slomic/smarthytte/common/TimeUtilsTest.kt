package no.slomic.smarthytte.common

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class TimeUtilsTest :
    ShouldSpec({
        context("formatMinutes") {
            should("format minutes as HH:MM") {
                formatMinutes(90) shouldBe "01:30"
                formatMinutes(1500) shouldBe "25:00"
                formatMinutes(0) shouldBe "00:00"
            }

            should("handle null input") {
                formatMinutes(null) shouldBe null
            }

            should("handle negative minutes by using absolute value") {
                formatMinutes(-90) shouldBe "01:30"
            }
        }

        context("formatMinutes with showSign = true") {
            should("format positive minutes with + prefix") {
                formatMinutes(90, showSign = true) shouldBe "+01:30"
                formatMinutes(1500, showSign = true) shouldBe "+25:00"
            }

            should("format negative minutes with - prefix") {
                formatMinutes(-90, showSign = true) shouldBe "-01:30"
                formatMinutes(-1500, showSign = true) shouldBe "-25:00"
            }

            should("format zero minutes without prefix") {
                formatMinutes(0, showSign = true) shouldBe "00:00"
            }

            should("handle null input") {
                formatMinutes(null, showSign = true) shouldBe null
            }
        }

        context("formatClock") {
            should("format minutes of day as HH:MM with modulo 24") {
                formatClock(90) shouldBe "01:30"
                formatClock(1440) shouldBe "00:00"
                formatClock(1500) shouldBe "01:00"
            }

            should("handle null input") {
                formatClock(null) shouldBe null
            }
        }
    })
