package no.slomic.smarthytte.common

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class TimeUtilsTest :
    ShouldSpec({
        context("formatMinutes") {
            should("return null for null") { formatMinutes(null) shouldBe null }
            should("format hours and minutes") { formatMinutes(90) shouldBe "01:30" }
            should("not cap hours at 24") { formatMinutes(1500) shouldBe "25:00" }
            should("add a plus sign for positive values when showSign") {
                formatMinutes(90, showSign = true) shouldBe "+01:30"
            }
            should("add a minus sign for negative values when showSign") {
                formatMinutes(-90, showSign = true) shouldBe "-01:30"
            }
            should("not add a sign for zero when showSign") { formatMinutes(0, showSign = true) shouldBe "00:00" }
            should("drop the sign of negative values by default") { formatMinutes(-90) shouldBe "01:30" }
        }

        context("formatClock") {
            should("return null for null") { formatClock(null) shouldBe null }
            should("format midnight") { formatClock(0) shouldBe "00:00" }
            should("format the last minute of the day") { formatClock(1439) shouldBe "23:59" }
            should("wrap around after 24 hours") { formatClock(1500) shouldBe "01:00" }
        }

        context("norwegianDuration") {
            should("show only minutes below one hour") { norwegianDuration(45) shouldBe "45 min" }
            should("show hours and zero minutes for a full hour") { norwegianDuration(60) shouldBe "1 t 0 min" }
            should("show hours and minutes") { norwegianDuration(135) shouldBe "2 t 15 min" }
        }
    })
