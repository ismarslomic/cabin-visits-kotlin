package no.slomic.smarthytte.vehicletrips

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-11-15T12:00:00Z")

class BackgroundSyncModeTest :
    ShouldSpec({
        context("isFetchDue") {
            should("be due when no fetch has succeeded yet") {
                isFetchDue(lastSuccess = null, now = NOW, interval = 12.hours) shouldBe true
            }

            should("not be due before the interval has passed") {
                isFetchDue(NOW - 11.hours - 59.minutes, NOW, 12.hours) shouldBe false
            }

            should("be due when the last success is exactly the interval old, or older") {
                isFetchDue(NOW - 12.hours, NOW, 12.hours) shouldBe true
                isFetchDue(NOW - 30.hours, NOW, 12.hours) shouldBe true
            }
        }

        context("backgroundSyncMode") {
            should("do nothing at night, whatever else is true") {
                backgroundSyncMode(false, true, true) shouldBe BackgroundSyncMode.NONE
                backgroundSyncMode(false, false, true) shouldBe BackgroundSyncMode.NONE
            }

            should("run everything within the reservation window, also when no trip fetch is due") {
                backgroundSyncMode(true, true, false) shouldBe BackgroundSyncMode.ALL
                backgroundSyncMode(true, true, true) shouldBe BackgroundSyncMode.ALL
            }

            should("fetch only the trips outside the window when a fetch is due") {
                backgroundSyncMode(true, false, true) shouldBe BackgroundSyncMode.TRIPS_ONLY
            }

            should("do nothing outside the window when no fetch is due") {
                backgroundSyncMode(true, false, false) shouldBe BackgroundSyncMode.NONE
            }
        }
    })
