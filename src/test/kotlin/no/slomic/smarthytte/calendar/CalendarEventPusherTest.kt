package no.slomic.smarthytte.calendar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationGuest
import no.slomic.smarthytte.reservations.ReservationRepository
import no.slomic.smarthytte.reservations.ReservationSystemService
import kotlin.time.Instant

private const val ID = "a3f9c2e17b4d4e5f8a1b2c3d4e5f6a7b"
private const val LEGACY_ID = "_abc123def456"
private val start = Instant.parse("2026-07-10T16:00:00Z")
private val end = Instant.parse("2026-07-12T13:00:00Z")

class CalendarEventPusherTest :
    StringSpec({
        val gateway = FakeCalendarGateway()
        val repository = mockk<ReservationRepository>()

        fun pusher(): CalendarEventPusher {
            listOf(ID, LEGACY_ID).forEach {
                coEvery { repository.reservationById(it) } returns
                    Reservation(it, start, end, listOf(ReservationGuest("a")))
            }
            coEvery { repository.reservationById("gone") } returns null
            return CalendarEventPusher(gateway, ReservationSystemService(repository))
        }

        beforeEach {
            gateway.calls.clear()
            gateway.onInsert = { _, _ -> CalendarCallResult.Success }
            gateway.onPatch = { _, _, _ -> CalendarCallResult.Success }
            gateway.onDelete = { CalendarCallResult.Success }
        }

        "delete should be done for success, for a missing event and for an event that is already gone" {
            pusher().delete(ID) shouldBe PushOutcome.Done
            gateway.onDelete = { failure(404) }
            pusher().delete(ID) shouldBe PushOutcome.Done
            gateway.onDelete = { failure(410) }
            pusher().delete(ID) shouldBe PushOutcome.Done
            gateway.calls shouldBe listOf("delete $ID", "delete $ID", "delete $ID")
        }

        "delete should be retried for any other failure" {
            gateway.onDelete = { failure(503) }

            pusher().delete(ID).shouldBeInstanceOf<PushOutcome.Retry>().failure.status shouldBe 503
        }

        "upsert should push nothing for a reservation that is gone" {
            pusher().upsert("gone", CalendarOperation.INSERT) shouldBe PushOutcome.Done

            gateway.calls shouldBe emptyList()
        }

        "an insert should create the event with the times of the reservation" {
            pusher().upsert(ID, CalendarOperation.INSERT) shouldBe PushOutcome.Done

            gateway.calls shouldBe listOf("insert $ID")
            gateway.insertedTimes[ID] shouldBe CalendarEventTimes(start, end)
        }

        "an insert that answers 409 should patch and restore the event that exists" {
            gateway.onInsert = { _, _ -> failure(409) }

            pusher().upsert(ID, CalendarOperation.INSERT) shouldBe PushOutcome.Done

            gateway.calls shouldBe listOf("insert $ID", "restore $ID")
        }

        "an insert that answers 409 should be retried when the patch fails" {
            gateway.onInsert = { _, _ -> failure(409) }
            gateway.onPatch = { _, _, _ -> failure(500) }

            pusher().upsert(ID, CalendarOperation.INSERT).shouldBeInstanceOf<PushOutcome.Retry>()
        }

        "an insert that answers 400 is terminal and a temporary failure is retried" {
            gateway.onInsert = { _, _ -> failure(400) }
            pusher().upsert(ID, CalendarOperation.INSERT) shouldBe PushOutcome.Done

            gateway.onInsert = { _, _ -> failure(503) }
            pusher().upsert(ID, CalendarOperation.INSERT).shouldBeInstanceOf<PushOutcome.Retry>()
        }

        "an insert of a legacy id is never sent to Google" {
            pusher().upsert(LEGACY_ID, CalendarOperation.INSERT) shouldBe PushOutcome.Done

            gateway.calls shouldBe emptyList()
        }

        "an update should patch the event, also a legacy one" {
            pusher().upsert(ID, CalendarOperation.UPDATE) shouldBe PushOutcome.Done
            pusher().upsert(LEGACY_ID, CalendarOperation.UPDATE) shouldBe PushOutcome.Done

            gateway.calls shouldBe listOf("patch $ID", "patch $LEGACY_ID")
        }

        "an update should be retried when the patch fails for another reason than a missing event" {
            gateway.onPatch = { _, _, _ -> failure(500) }

            pusher().upsert(ID, CalendarOperation.UPDATE).shouldBeInstanceOf<PushOutcome.Retry>()
            gateway.calls shouldBe listOf("patch $ID")
        }

        "an update of a missing or cancelled event should restore it with a second patch" {
            gateway.onPatch = { _, _, restore -> if (restore) CalendarCallResult.Success else failure(404) }
            pusher().upsert(ID, CalendarOperation.UPDATE) shouldBe PushOutcome.Done
            gateway.onPatch = { _, _, restore -> if (restore) CalendarCallResult.Success else failure(410) }
            pusher().upsert(ID, CalendarOperation.UPDATE) shouldBe PushOutcome.Done

            gateway.calls shouldBe listOf("patch $ID", "restore $ID", "patch $ID", "restore $ID")
        }

        "a failing restore for a temporary reason should be retried and not fall through to an insert" {
            gateway.onPatch = { _, _, restore -> failure(if (restore) 503 else 404) }

            pusher().upsert(ID, CalendarOperation.UPDATE).shouldBeInstanceOf<PushOutcome.Retry>()
            gateway.calls shouldBe listOf("patch $ID", "restore $ID")
        }

        "a rejected restore should create the event, and terminal answers are done" {
            gateway.onPatch = { _, _, restore -> failure(if (restore) 400 else 404) }
            pusher().upsert(ID, CalendarOperation.UPDATE) shouldBe PushOutcome.Done
            gateway.calls shouldBe listOf("patch $ID", "restore $ID", "insert $ID")

            gateway.calls.clear()
            gateway.onPatch = { _, _, _ -> failure(404) }
            gateway.onInsert = { _, _ -> failure(409) }
            pusher().upsert(ID, CalendarOperation.UPDATE) shouldBe PushOutcome.Done

            gateway.onInsert = { _, _ -> failure(400) }
            pusher().upsert(ID, CalendarOperation.UPDATE) shouldBe PushOutcome.Done
        }

        "an insert after a failed restore should be retried for a temporary failure" {
            gateway.onPatch = { _, _, _ -> failure(404) }
            gateway.onInsert = { _, _ -> failure(500) }

            pusher().upsert(ID, CalendarOperation.UPDATE).shouldBeInstanceOf<PushOutcome.Retry>()
        }

        "a legacy event that is gone is not created again" {
            gateway.onPatch = { _, _, _ -> failure(404) }

            pusher().upsert(LEGACY_ID, CalendarOperation.UPDATE) shouldBe PushOutcome.Done

            gateway.calls shouldBe listOf("patch $LEGACY_ID", "restore $LEGACY_ID")
        }
    })
