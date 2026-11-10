package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import no.slomic.smarthytte.auth.UserPrincipal
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.auth.toUserPrincipal
import no.slomic.smarthytte.common.ApiError
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.utils.TestDbSetup
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import java.sql.SQLException
import kotlin.time.Instant

private const val START = "2026-07-10T16:00:00Z"
private const val END = "2026-07-12T13:00:00Z"

class ReservationWriteServiceTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guests = SqliteGuestRepository()
        val reservations = SqliteReservationRepository()
        val derived = mutableListOf<String>()

        fun service(
            pending: Long = 0,
            max: Long = 200,
            repository: ReservationRepository = reservations,
            onTimesChanged: suspend (String) -> Unit = { derived.add(it) },
        ) = ReservationService(repository, guests, fixedClock, { pending }, max, onTimesChanged)

        suspend fun actor(): UserPrincipal = checkNotNull(guests.guestById("a")?.toUserPrincipal())

        fun create(start: String = START, end: String = END, vararg ids: String = arrayOf("a")) =
            ReservationCreate(Instant.parse(start), Instant.parse(end), ids.map { ReservationGuestRef(it) })

        fun update(version: Short, start: String = START, end: String = END, vararg ids: String = arrayOf("a")) =
            ReservationUpdate(version, Instant.parse(start), Instant.parse(end), ids.map { ReservationGuestRef(it) })

        fun ReservationWriteResult.code() = (this as ReservationWriteResult.Invalid).code

        beforeEach { derived.clear() }

        "create should store the reservation, derive its data after the commit and answer with the response" {
            givenReservations(guests, reservations)

            val result = service().create(actor(), create(ids = arrayOf("a", "b")))

            val created = result.shouldBeInstanceOf<ReservationWriteResult.Created>().reservation
            created.id.length shouldBe 32
            created.guests.map { it.guestId } shouldBe listOf("a", "b")
            created.version shouldBe 1
            created.createdBy shouldBe "a"
            derived shouldBe listOf(created.id)
        }

        "create should reject every broken rule with its code" {
            givenReservations(guests, reservations)
            val sut = service()
            val a = actor()

            sut.create(a, create(START, START)).code() shouldBe ApiError.END_BEFORE_START
            sut.create(a, create(END, START)).code() shouldBe ApiError.END_BEFORE_START
            // 61 nights by UTC date
            sut.create(a, create("2026-07-01T10:00:00Z", "2026-08-31T10:00:00Z")).code() shouldBe ApiError.TOO_LONG
            sut.create(a, create("2028-04-12T10:00:00Z", "2028-04-14T10:00:00Z")).code() shouldBe
                ApiError.TOO_FAR_AHEAD
            sut.create(a, create(ids = emptyArray())).code() shouldBe ApiError.NO_GUESTS
            sut.create(a, create(ids = Array(51) { "g$it" })).code() shouldBe ApiError.TOO_MANY_GUESTS
            sut.create(a, create(ids = arrayOf("a", "a"))).code() shouldBe ApiError.DUPLICATE_GUEST
            sut.create(a, create(ids = arrayOf("a", "nobody"))).code() shouldBe ApiError.UNKNOWN_GUEST
            derived shouldBe emptyList()
        }

        "create should accept 60 nights, a start two years ahead, a 0-night day stay and a start in the past" {
            givenReservations(guests, reservations)
            val sut = service()
            val a = actor()

            sut.create(a, create("2026-07-01T10:00:00Z", "2026-08-30T10:00:00Z"))
                .shouldBeInstanceOf<ReservationWriteResult.Created>()
            sut.create(a, create("2028-04-11T10:00:00Z", "2028-04-12T10:00:00Z"))
                .shouldBeInstanceOf<ReservationWriteResult.Created>()
            sut.create(a, create("2026-10-04T10:00:00Z", "2026-10-04T12:00:00Z"))
                .shouldBeInstanceOf<ReservationWriteResult.Created>()
            sut.create(a, create("2019-01-04T10:00:00Z", "2019-01-06T12:00:00Z"))
                .shouldBeInstanceOf<ReservationWriteResult.Created>()
        }

        "create should answer conflict for an overlap and allow a handover on the same instant" {
            givenReservations(guests, reservations)
            val sut = service()
            val a = actor()

            sut.create(a, create("2026-04-01T10:00:00Z", "2026-04-03T10:00:00Z")) shouldBe
                ReservationWriteResult.Conflict
            sut.create(a, create("2026-04-02T13:00:00Z", "2026-04-04T10:00:00Z"))
                .shouldBeInstanceOf<ReservationWriteResult.Created>()
        }

        "a write should be unavailable when the calendar queue is full, and nothing is written" {
            givenReservations(guests, reservations)
            val before = reservations.allReservations().size
            val sut = service(pending = 200)
            val a = actor()

            sut.create(a, create()).shouldBeInstanceOf<ReservationWriteResult.Unavailable>()
            sut.update(a, "r1", update(1)).shouldBeInstanceOf<ReservationWriteResult.Unavailable>()
            sut.delete(a, "r1", 1).shouldBeInstanceOf<ReservationWriteResult.Unavailable>()

            reservations.allReservations().size shouldBe before
            service(pending = 199).create(a, create()).shouldBeInstanceOf<ReservationWriteResult.Created>()
        }

        "a failing derivation should be logged and not fail the write" {
            givenReservations(guests, reservations)

            service(onTimesChanged = { error("boom") }).create(actor(), create())
                .shouldBeInstanceOf<ReservationWriteResult.Created>()
        }

        "update should check existence, then version, then the rules, in that order" {
            givenReservations(guests, reservations)
            val sut = service()
            val a = actor()

            // Invalid body and stale version: the version wins; unknown id: not found wins
            sut.update(a, "r1", update(9, START, START)) shouldBe ReservationWriteResult.VersionMismatch
            sut.update(a, "nope", update(1, START, START)) shouldBe ReservationWriteResult.NotFound
            sut.update(a, "r1", update(1, START, START)).code() shouldBe ApiError.END_BEFORE_START
        }

        "update should change the times, derive the data and bump the version" {
            givenReservations(guests, reservations)

            val result = service().update(actor(), "r3a", update(1, "2026-06-05T10:00:00Z", "2026-06-07T10:00:00Z"))

            val updated = result.shouldBeInstanceOf<ReservationWriteResult.Updated>().reservation
            updated.version shouldBe 2
            updated.updatedBy shouldBe "a"
            derived shouldBe listOf("r3a")
        }

        "update should not derive anything for a guest-only change or for no change" {
            givenReservations(guests, reservations)
            val r1 = reservations.reservationById("r1")!!
            val sut = service()
            val a = actor()

            val guestOnly = sut.update(a, "r1", update(1, r1.startTime.toString(), r1.endTime.toString(), "a"))
            guestOnly.shouldBeInstanceOf<ReservationWriteResult.Updated>().reservation.version shouldBe 2

            val unchanged = sut.update(a, "r1", update(2, r1.startTime.toString(), r1.endTime.toString(), "a"))
            unchanged.shouldBeInstanceOf<ReservationWriteResult.Updated>().reservation.version shouldBe 2
            derived shouldBe emptyList()
        }

        "update should skip the range rules when only the guests change" {
            givenReservations(guests, reservations)
            // Stored 2 years ahead and 70 nights: allowed to exist before the rules, only the guests change
            reservations.addOrUpdate(
                Reservation(
                    id = "old",
                    startTime = Instant.parse("2029-01-01T10:00:00Z"),
                    endTime = Instant.parse("2029-03-12T10:00:00Z"),
                    guestIds = listOf("a"),
                ),
            )

            service().update(actor(), "old", update(1, "2029-01-01T10:00:00Z", "2029-03-12T10:00:00Z", "a", "b"))
                .shouldBeInstanceOf<ReservationWriteResult.Updated>()
            service().update(actor(), "old", update(2, "2029-01-01T10:00:00Z", "2029-03-13T10:00:00Z", "a", "b"))
                .code() shouldBe ApiError.TOO_LONG
        }

        "update should answer conflict for an overlap and unknown guest for a missing guest" {
            givenReservations(guests, reservations)
            val sut = service()
            val a = actor()

            sut.update(a, "r3a", update(1, "2026-04-01T10:00:00Z", "2026-04-03T10:00:00Z")) shouldBe
                ReservationWriteResult.Conflict
            sut.update(a, "r3a", update(1, "2026-06-01T16:00:00Z", "2026-06-03T13:00:00Z", "nobody")).code() shouldBe
                ApiError.UNKNOWN_GUEST
        }

        "update should answer version mismatch when the version changes between the read and the write" {
            givenReservations(guests, reservations)
            val racing = mockk<ReservationRepository>()
            coEvery { racing.reservationById("r1") } returns reservations.reservationById("r1")
            coEvery { racing.updateReservation(any(), any(), any(), any(), any(), any()) } returns
                ReservationUpdateOutcome.VersionMismatch

            service(
                repository = racing,
            ).update(actor(), "r1", update(1, "2026-03-30T16:00:00Z", "2026-04-03T13:00:00Z")) shouldBe
                ReservationWriteResult.VersionMismatch
        }

        "update should answer not found when the reservation is deleted between the read and the write" {
            givenReservations(guests, reservations)
            val racing = mockk<ReservationRepository>()
            coEvery { racing.reservationById("r1") } returns reservations.reservationById("r1")
            coEvery { racing.updateReservation(any(), any(), any(), any(), any(), any()) } returns
                ReservationUpdateOutcome.NotFound

            service(
                repository = racing,
            ).update(actor(), "r1", update(1, "2026-03-30T16:00:00Z", "2026-04-03T13:00:00Z")) shouldBe
                ReservationWriteResult.NotFound
        }

        "delete should remove the reservation, and answer not found and version mismatch" {
            givenReservations(guests, reservations)
            val sut = service()
            val a = actor()

            sut.delete(a, "r1", 5) shouldBe ReservationWriteResult.VersionMismatch
            sut.delete(a, "nope", 1) shouldBe ReservationWriteResult.NotFound
            sut.delete(a, "r1", 1) shouldBe ReservationWriteResult.Deleted
            reservations.reservationById("r1") shouldBe null
        }

        "a busy database should be unavailable, any other database error should be rethrown" {
            givenReservations(guests, reservations)
            val failing = mockk<ReservationRepository>()
            val busy = SQLException("wrapper", SQLiteException("busy", SQLiteErrorCode.SQLITE_BUSY))
            coEvery { failing.createReservation(any(), any(), any(), any()) } throws busy
            coEvery { failing.deleteReservation(any(), any(), any()) } throws
                SQLiteException("locked", SQLiteErrorCode.SQLITE_LOCKED_SHAREDCACHE)
            val sut = service(repository = failing)

            sut.create(actor(), create()).shouldBeInstanceOf<ReservationWriteResult.Unavailable>()
            sut.delete(actor(), "r1", 1).shouldBeInstanceOf<ReservationWriteResult.Unavailable>()

            coEvery { failing.createReservation(any(), any(), any(), any()) } throws
                SQLiteException("constraint", SQLiteErrorCode.SQLITE_CONSTRAINT)
            runCatching { sut.create(actor(), create()) }.exceptionOrNull().shouldBeInstanceOf<SQLException>()
        }

        "isBusy should recognise busy and locked codes, also extended and wrapped, and nothing else" {
            SQLiteException("x", SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT).isBusy() shouldBe true
            SQLException("x", SQLiteException("x", SQLiteErrorCode.SQLITE_LOCKED)).isBusy() shouldBe true
            SQLiteException("x", SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE).isBusy() shouldBe false
            SQLException("no sqlite cause").isBusy() shouldBe false
        }

        "a guest user principal is never needed: the role check is the route's job" {
            guests.addOrUpdate(testGuest("viewer", role = AppRole.GUEST_USER))
            guests.guestById("viewer")?.toUserPrincipal()?.role shouldBe AppRole.GUEST_USER
        }
    })
