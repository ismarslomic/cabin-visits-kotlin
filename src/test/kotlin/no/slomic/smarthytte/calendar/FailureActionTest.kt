package no.slomic.smarthytte.calendar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FailureActionTest :
    StringSpec({
        "network and server errors and rate limits are retried" {
            failure(null).action() shouldBe FailureAction.RETRY
            failure(429).action() shouldBe FailureAction.RETRY
            failure(500).action() shouldBe FailureAction.RETRY
            failure(503).action() shouldBe FailureAction.RETRY
            failure(403, "rateLimitExceeded").action() shouldBe FailureAction.RETRY
            failure(403, "userRateLimitExceeded").action() shouldBe FailureAction.RETRY
        }

        "a quota error is retried slowly" {
            failure(403, "quotaExceeded").action() shouldBe FailureAction.RETRY_SLOWLY
        }

        "any other 403, an auth error and other client errors are kept and alerted" {
            failure(403, "forbiddenForNonOrganizer").action() shouldBe FailureAction.KEEP_AND_ALERT
            failure(403).action() shouldBe FailureAction.KEEP_AND_ALERT
            failure(401).action() shouldBe FailureAction.KEEP_AND_ALERT
            failure(404).action() shouldBe FailureAction.KEEP_AND_ALERT
        }

        "only the characters a-v and 0-9, 5 to 1024 long, can be inserted as an event id" {
            isInsertableEventId("a3f9c2e17b4d4e5f8a1b2c3d4e5f6a7b") shouldBe true
            isInsertableEventId("abcde") shouldBe true
            isInsertableEventId("abcd") shouldBe false
            isInsertableEventId("_abc123def") shouldBe false
            isInsertableEventId("ABCDE") shouldBe false
            isInsertableEventId("abcdw") shouldBe false
            isInsertableEventId("a".repeat(1024)) shouldBe true
            isInsertableEventId("a".repeat(1025)) shouldBe false
        }
    })
