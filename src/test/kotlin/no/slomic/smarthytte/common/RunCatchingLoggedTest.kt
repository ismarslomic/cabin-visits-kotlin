package no.slomic.smarthytte.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException

class RunCatchingLoggedTest :
    StringSpec({
        "should return true when the block completes" {
            var ran = false

            val completed = runCatchingLogged("test step") { ran = true }

            completed shouldBe true
            ran shouldBe true
        }

        "should return false and not throw when the block throws" {
            val completed = runCatchingLogged("test step") { error("boom") }

            completed shouldBe false
        }

        "should rethrow CancellationException" {
            shouldThrow<CancellationException> {
                runCatchingLogged("test step") { throw CancellationException("cancelled") }
            }
        }
    })
