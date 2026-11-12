package no.slomic.smarthytte.calendar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.common.suspendReadTransaction
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.properties.CalendarOutboxProperties
import no.slomic.smarthytte.reservations.ReservationCreateOutcome
import no.slomic.smarthytte.reservations.ReservationSystemService
import no.slomic.smarthytte.reservations.SqliteReservationRepository
import no.slomic.smarthytte.reservations.createReservation
import no.slomic.smarthytte.reservations.updateReservation
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val properties = CalendarOutboxProperties(
    eventTitle = "Hyttebesøk",
    batchSize = 50,
    drainIntervalSeconds = 30,
    backoffStartSeconds = 30,
    backoffFactor = 2.0,
    backoffCapSeconds = 100,
    stuckThresholdMinutes = 60,
)

class CalendarOutboxDrainTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val reservations = SqliteReservationRepository()
        val outbox = CalendarOutbox()
        val gateway = FakeCalendarGateway()

        // A bit after the rows were written, so they are due
        // The database keeps milliseconds
        @Suppress("CanBeVal") // written from the code below
        var now = Clock.System.now().truncatedToMillis()
        val clock = object : Clock {
            override fun now(): Instant = now
        }

        fun drain(
            props: CalendarOutboxProperties = properties,
            metrics: CalendarOutboxMetrics = CalendarOutboxMetrics(),
        ) = CalendarOutboxDrain(
            outbox,
            CalendarEventPusher(gateway, ReservationSystemService(reservations)),
            props,
            metrics,
            clock,
        )

        suspend fun create(day: Int): String {
            SqliteGuestRepository().addOrUpdate(testGuest("a"))
            val created = reservations.createReservation(
                Instant.parse("2026-07-${day}T16:00:00Z"),
                Instant.parse("2026-07-${day + 1}T13:00:00Z"),
                listOf("a"),
                "a",
            )
            return (created as ReservationCreateOutcome.Created).reservation.id
        }

        suspend fun rows() = suspendReadTransaction {
            CalendarOutboxTable.selectAll().map {
                Triple(
                    it[CalendarOutboxTable.operation],
                    it[CalendarOutboxTable.attempts],
                    it[CalendarOutboxTable.nextAttemptTime],
                )
            }
        }

        beforeEach {
            gateway.calls.clear()
            gateway.insertedTimes.clear()
            gateway.onInsert = { _, _ -> CalendarCallResult.Success }
            gateway.onPatch = { _, _, _ -> CalendarCallResult.Success }
            gateway.onDelete = { CalendarCallResult.Success }
            now = Clock.System.now().truncatedToMillis() + 1.seconds
        }

        "a created reservation should be inserted in the calendar and its row deleted" {
            val id = create(10)

            drain().drainOnce()

            gateway.calls shouldBe listOf("insert $id")
            rows() shouldBe emptyList()
        }

        "several changes of a reservation should collapse into one push of its current times" {
            val id = create(10)
            reservations.updateReservation(
                id,
                1,
                Instant.parse("2026-07-20T16:00:00Z"),
                Instant.parse("2026-07-22T13:00:00Z"),
                listOf("a"),
                "a",
            )
            rows().size shouldBe 2

            drain().drainOnce()

            gateway.calls shouldBe listOf("insert $id")
            gateway.insertedTimes[id]?.start shouldBe Instant.parse("2026-07-20T16:00:00Z")
            rows() shouldBe emptyList()
        }

        "a time change of a reservation that is already in the calendar should patch it" {
            val id = create(10)
            drain().drainOnce()
            gateway.calls.clear()
            reservations.updateReservation(
                id,
                1,
                Instant.parse("2026-07-20T16:00:00Z"),
                Instant.parse("2026-07-22T13:00:00Z"),
                listOf("a"),
                "a",
            )

            drain().drainOnce()

            gateway.calls shouldBe listOf("patch $id")
            rows() shouldBe emptyList()
        }

        "a deleted reservation should delete its event, also when it was never inserted" {
            val pushed = create(10)
            drain().drainOnce()
            val neverPushed = create(20)
            gateway.calls.clear()
            reservations.deleteReservation(pushed, 1, "a")
            reservations.deleteReservation(neverPushed, 1, "a")

            drain().drainOnce()

            gateway.calls.toSet() shouldBe setOf("delete $pushed", "delete $neverPushed")
            rows() shouldBe emptyList()
        }

        "a failed push should keep the row, count the attempt and wait with a growing backoff up to the cap" {
            val id = create(10)
            gateway.onInsert = { _, _ -> failure(503) }

            drain().drainOnce()
            rows().single().let {
                it.second shouldBe 1
                it.third shouldBe now + 30.seconds
            }

            // Not due yet: nothing is pushed
            gateway.calls.clear()
            drain().drainOnce()
            gateway.calls shouldBe emptyList()

            now += 31.seconds
            drain().drainOnce()
            gateway.calls shouldBe listOf("insert $id")
            rows().single().let {
                it.second shouldBe 2
                it.third shouldBe now + 60.seconds
            }

            // 30 * 2^2 = 120 s, but the cap is 100 s
            now += 61.seconds
            drain().drainOnce()
            rows().single().third shouldBe now + 100.seconds
        }

        "the error of a failed push should be stored, at most 500 characters" {
            create(10)
            gateway.onInsert = { _, _ -> CalendarCallResult.Failure(500, "backendError", "x".repeat(2000)) }

            drain().drainOnce()

            val error =
                suspendReadTransaction { CalendarOutboxTable.selectAll().single()[CalendarOutboxTable.lastError] }
            error?.length shouldBe MAX_LAST_ERROR_LENGTH
            error?.shouldStartWith("500 backendError: ")
        }

        "a quota error should wait for the cap and a configuration error should keep the row" {
            create(10)
            gateway.onInsert = { _, _ -> failure(403, "quotaExceeded") }
            drain().drainOnce()
            rows().single().third shouldBe now + 100.seconds

            now += 101.seconds
            gateway.onInsert = { _, _ -> failure(403, "forbiddenForNonOrganizer") }
            drain().drainOnce()
            rows().single().second shouldBe 2
        }

        "a change written while the push runs should keep its own row" {
            val id = create(10)
            gateway.onInsert = { _, _ ->
                suspendTransaction { with(this) { enqueueCalendarEventMoved(id) } }
                CalendarCallResult.Success
            }

            drain().drainOnce()

            rows().map { it.first } shouldBe listOf(CalendarOperation.UPDATE)
        }

        "a drain should read at most the batch size of rows" {
            create(10)
            val second = create(20)
            val first = reservations.allReservations().first { it.id != second }.id

            drain(properties.copy(batchSize = 1)).drainOnce()

            gateway.calls shouldBe listOf("insert $first")
            rows().size shouldBe 1
        }

        "a failing reservation should not stop the others" {
            val broken = create(10)
            val fine = create(20)
            gateway.onInsert = { id, _ -> if (id == broken) error("boom") else CalendarCallResult.Success }

            drain().drainOnce()

            gateway.calls.toSet() shouldBe setOf("insert $broken", "insert $fine")
            rows().size shouldBe 1
        }

        "a second drain while one runs should do nothing" {
            create(10)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            gateway.onInsert = { _, _ ->
                started.complete(Unit)
                release.await()
                CalendarCallResult.Success
            }
            val sut = drain()

            val first = async { sut.drainOnce() }
            started.await()
            sut.drainOnce()
            yield()
            release.complete(Unit)
            first.await()

            gateway.calls.size shouldBe 1
        }

        "the drain should report the pending rows and the age of the oldest as gauges" {
            create(10)
            gateway.onInsert = { _, _ -> failure(503) }
            val metrics = CalendarOutboxMetrics()
            val registry = SimpleMeterRegistry()
            metrics.bind(registry)

            now += 2.hours
            drain(metrics = metrics).drainOnce()

            registry.get("calendar_outbox_pending").gauge().value() shouldBe 1.0
            (registry.get("calendar_outbox_oldest_age_seconds").gauge().value() > 1.hours.inWholeSeconds) shouldBe true

            gateway.onInsert = { _, _ -> CalendarCallResult.Success }
            now += 3.minutes
            drain(metrics = metrics).drainOnce()
            registry.get("calendar_outbox_pending").gauge().value() shouldBe 0.0
            registry.get("calendar_outbox_oldest_age_seconds").gauge().value() shouldBe 0.0
        }

        "the outbox status should be empty without rows" {
            outbox.status() shouldBe OutboxStatus(0, null)
            "calendar_outbox" shouldContain "outbox"
        }
    })
