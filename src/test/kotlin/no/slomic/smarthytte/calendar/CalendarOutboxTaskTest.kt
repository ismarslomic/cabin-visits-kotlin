package no.slomic.smarthytte.calendar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import no.slomic.smarthytte.issues.IssueCode
import no.slomic.smarthytte.issues.RecordingIssueReporter
import no.slomic.smarthytte.reservations.ReservationSystemService
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.utils.REQUIRED_APP_ENV
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.utils.withTestEnvironment
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val STUCK = IssueCode.CALENDAR_OUTBOX_STUCK to "outbox"

// Whole seconds are the smallest interval
private val DRAIN_EVERY_SECOND = mapOf("CALENDAR_OUTBOX_DRAIN_INTERVAL_SECONDS" to "1")

class CalendarOutboxTaskTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        "the task drains at once and again after the interval, publishes the metrics and stops with the application" {
            val reporter = RecordingIssueReporter()
            val registry = SimpleMeterRegistry()

            // The properties are read when the task starts, the Google client only on the first push
            withTestEnvironment(REQUIRED_APP_ENV + DRAIN_EVERY_SECOND) {
                testApplication {
                    application {
                        startCalendarOutboxDrain(
                            ReservationSystemService(SqliteReservationRepository()),
                            registry,
                            reporter,
                        )
                    }
                    startApplication()

                    // An empty outbox is healthy, so every run resolves the stuck issue: the second run proves the loop
                    withTimeout(10.seconds) {
                        while (reporter.resolved.count { it == STUCK } < 2) delay(10.milliseconds)
                    }
                    reporter.resolved shouldContain STUCK
                    registry.find("calendar_outbox_pending").gauge()?.value() shouldBe 0.0
                }
            }

            // A loop that was not cancelled would run again within the 1 s interval
            delay(200.milliseconds)
            val afterStop = reporter.resolved.size
            delay(1500.milliseconds)
            reporter.resolved.size shouldBe afterStop
        }
    })
