package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.calendar.CalendarOperation
import no.slomic.smarthytte.calendar.CalendarOutboxTable
import no.slomic.smarthytte.checkinouts.CABIN_CITY_NAME
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckInOutSource
import no.slomic.smarthytte.checkinouts.CheckOut
import no.slomic.smarthytte.checkinouts.HOME_CITY_NAME
import no.slomic.smarthytte.common.PersistenceResult
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.SqliteVehicleTripRepository
import no.slomic.smarthytte.vehicletrips.createTrip
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val start = Instant.parse("2026-07-10T16:00:00Z")
private val end = Instant.parse("2026-07-12T13:00:00Z")

class ReservationWriteRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val guests = SqliteGuestRepository()
        val repository = SqliteReservationRepository()

        suspend fun givenGuests() {
            guests.addOrUpdate(testGuest("a"))
            guests.addOrUpdate(testGuest("b"))
        }

        suspend fun create(from: Instant = start, to: Instant = end, vararg guestIds: String) =
            repository.createReservation(from, to, guestIds.toList().ifEmpty { listOf("a") }, "admin")

        suspend fun created(from: Instant = start, to: Instant = end, vararg guestIds: String): Reservation =
            create(from, to, *guestIds).shouldBeInstanceOf<ReservationCreateOutcome.Created>().reservation

        fun outbox(): List<Pair<String, CalendarOperation>> = transaction {
            CalendarOutboxTable.selectAll().orderBy(CalendarOutboxTable.id).map {
                it[CalendarOutboxTable.reservationId] to it[CalendarOutboxTable.operation]
            }
        }

        "create should store the reservation with a generated id, version 1, the actor and the guests" {
            givenGuests()

            val reservation = created(guestIds = arrayOf("b", "a"))

            reservation.id shouldMatch Regex("[0-9a-f]{32}")
            reservation.startTime shouldBe start
            reservation.endTime shouldBe end
            reservation.guestIds shouldBe listOf("a", "b")
            reservation.version shouldBe 1
            reservation.createdBy shouldBe "admin"
            reservation.updatedTime.shouldBeNull()
            repository.reservationById(reservation.id)?.guestIds shouldBe listOf("a", "b")
        }

        "create should queue a calendar insert in the same transaction" {
            givenGuests()

            val reservation = created()

            outbox() shouldContainExactly listOf(reservation.id to CalendarOperation.INSERT)
        }

        "create should generate a different id every time" {
            givenGuests()

            val first = created()
            val second = created(from = end, to = end + 2.days)

            (first.id == second.id) shouldBe false
        }

        "create should truncate the times to milliseconds, which is what is stored" {
            givenGuests()
            val precise = Instant.parse("2026-07-10T16:00:00.123456789Z")

            created(from = precise).startTime shouldBe Instant.parse("2026-07-10T16:00:00.123Z")
        }

        "create should answer Overlap and write nothing when the times overlap another reservation" {
            givenGuests()
            created()

            create(from = Instant.parse("2026-07-11T10:00:00Z"), to = Instant.parse("2026-07-13T10:00:00Z")) shouldBe
                ReservationCreateOutcome.Overlap
            repository.allReservations() shouldHaveSize 1
            outbox() shouldHaveSize 1
        }

        "create should answer Overlap for a reservation inside, around or starting before another" {
            givenGuests()
            created()

            listOf(
                Instant.parse("2026-07-10T18:00:00Z") to Instant.parse("2026-07-11T10:00:00Z"),
                Instant.parse("2026-07-09T10:00:00Z") to Instant.parse("2026-07-13T10:00:00Z"),
                Instant.parse("2026-07-09T10:00:00Z") to Instant.parse("2026-07-10T17:00:00Z"),
            ).forEach { (from, to) -> create(from, to) shouldBe ReservationCreateOutcome.Overlap }
        }

        "create should allow a same-day handover, where one reservation ends when the next starts" {
            givenGuests()
            created()

            create(from = end, to = Instant.parse("2026-07-14T13:00:00Z"))
                .shouldBeInstanceOf<ReservationCreateOutcome.Created>()
            create(from = Instant.parse("2026-07-08T16:00:00Z"), to = start)
                .shouldBeInstanceOf<ReservationCreateOutcome.Created>()
        }

        "create should answer UnknownGuests and write nothing for a guest that does not exist" {
            givenGuests()

            create(guestIds = arrayOf("a", "ghost")) shouldBe ReservationCreateOutcome.UnknownGuests(listOf("ghost"))
            repository.allReservations().shouldBeEmpty()
            outbox().shouldBeEmpty()
        }

        "update should replace the times and guests, bump the version, set the actor and queue a calendar update" {
            givenGuests()
            val reservation = created()
            val newStart = Instant.parse("2026-07-11T16:00:00Z")
            val newEnd = Instant.parse("2026-07-13T13:00:00Z")

            val outcome = repository.updateReservation(reservation.id, 1, newStart, newEnd, listOf("b"), "family")

            val updated = outcome.shouldBeInstanceOf<ReservationUpdateOutcome.Updated>()
            updated.timesChanged shouldBe true
            updated.reservation.startTime shouldBe newStart
            updated.reservation.endTime shouldBe newEnd
            updated.reservation.guestIds shouldBe listOf("b")
            updated.reservation.version shouldBe 2
            updated.reservation.updatedBy shouldBe "family"
            updated.reservation.updatedTime.shouldNotBeNull()
            updated.reservation.createdBy shouldBe "admin"
            outbox() shouldContainExactly
                listOf(reservation.id to CalendarOperation.INSERT, reservation.id to CalendarOperation.UPDATE)
        }

        "update with a guest-only change should bump the version and queue no calendar operation" {
            givenGuests()
            val reservation = created()

            val outcome = repository.updateReservation(reservation.id, 1, start, end, listOf("a", "b"), "admin")

            val updated = outcome.shouldBeInstanceOf<ReservationUpdateOutcome.Updated>()
            updated.timesChanged shouldBe false
            updated.reservation.version shouldBe 2
            updated.reservation.guestIds shouldBe listOf("a", "b")
            outbox() shouldContainExactly listOf(reservation.id to CalendarOperation.INSERT)
        }

        "update with the stored times and guests should change nothing, also when the guests come in another order" {
            givenGuests()
            val reservation = created(guestIds = arrayOf("a", "b"))

            val outcome = repository.updateReservation(reservation.id, 1, start, end, listOf("b", "a"), "admin")

            outcome.shouldBeInstanceOf<ReservationUpdateOutcome.Unchanged>().reservation.version shouldBe 1
            repository.reservationById(reservation.id)?.updatedTime.shouldBeNull()
            outbox() shouldHaveSize 1
        }

        "update should answer NotFound for an unknown id and VersionMismatch for a stale version" {
            givenGuests()
            val reservation = created()

            repository.updateReservation("ghost", 1, start, end, listOf("a"), "admin") shouldBe
                ReservationUpdateOutcome.NotFound
            repository.updateReservation(reservation.id, 2, start, end, listOf("a", "b"), "admin") shouldBe
                ReservationUpdateOutcome.VersionMismatch
            repository.reservationById(reservation.id)?.version shouldBe 1
        }

        "update should answer Overlap and change nothing when the new times overlap another reservation" {
            givenGuests()
            val first = created()
            val second = created(from = end, to = Instant.parse("2026-07-14T13:00:00Z"))

            val outcome = repository.updateReservation(second.id, 1, end - 1.hours, end + 1.days, listOf("a"), "admin")

            outcome shouldBe ReservationUpdateOutcome.Overlap
            repository.reservationById(second.id)?.startTime shouldBe end
            repository.reservationById(first.id)?.version shouldBe 1
            outbox() shouldHaveSize 2
        }

        "update should not count the reservation itself as an overlap when it is moved a little" {
            givenGuests()
            val reservation = created()

            repository.updateReservation(
                reservation.id,
                1,
                start + 1.hours,
                end,
                listOf("a"),
                "admin",
            ).shouldBeInstanceOf<ReservationUpdateOutcome.Updated>()
        }

        "update should not check the overlap for a guest-only change of a reservation that overlaps another" {
            givenGuests()
            val first = created()
            // An old reservation that overlaps the first one (only possible through the fixture upsert)
            repository.addOrUpdate(
                Reservation("old", start + 1.hours, end + 1.hours, listOf(ReservationGuest("a"))),
            )

            repository.updateReservation(first.id, 1, start, end, listOf("a", "b"), "admin")
                .shouldBeInstanceOf<ReservationUpdateOutcome.Updated>()
        }

        "update should answer UnknownGuests for a guest that does not exist" {
            givenGuests()
            val reservation = created()

            repository.updateReservation(reservation.id, 1, start, end, listOf("a", "ghost"), "admin") shouldBe
                ReservationUpdateOutcome.UnknownGuests(listOf("ghost"))
            repository.reservationById(reservation.id)?.version shouldBe 1
        }

        "update that changes the times should clear the trip links and the check-in and check-out" {
            givenGuests()
            val reservation = created()
            val tripRepository = SqliteVehicleTripRepository()
            val trip =
                createTrip(
                    HOME_CITY_NAME,
                    CABIN_CITY_NAME,
                    "2026-07-10T17:00:00Z",
                    "2026-07-10T19:30:00Z",
                    id = "trip1",
                )
            tripRepository.addOrUpdate(trip, "system")
            repository.addVehicleTripLink(reservation.id, trip.id, ReservationVehicleTripType.TO_CABIN, start, end)
            repository.setCheckIn(CheckIn(start, CheckInOutSource.CHECK_IN_SENSOR, "s1"), reservation.id, start, end)
            repository.setCheckOut(CheckOut(end, CheckInOutSource.CHECK_IN_SENSOR, "s2"), reservation.id, start, end)

            val newEnd = end + 1.days
            val outcome = repository.updateReservation(reservation.id, 1, start, newEnd, listOf("a"), "admin")

            val updated = outcome.shouldBeInstanceOf<ReservationUpdateOutcome.Updated>().reservation
            updated.checkIn.shouldBeNull()
            updated.checkOut.shouldBeNull()
            updated.toCabinVehicleTrips.shouldBeEmpty()
            transaction { ReservationVehicleTripTable.selectAll().toList().shouldBeEmpty() }
        }

        "update that only changes the guests should keep the trip links and the check-in" {
            givenGuests()
            val reservation = created()
            val tripRepository = SqliteVehicleTripRepository()
            val trip =
                createTrip(
                    HOME_CITY_NAME,
                    CABIN_CITY_NAME,
                    "2026-07-10T17:00:00Z",
                    "2026-07-10T19:30:00Z",
                    id = "trip1",
                )
            tripRepository.addOrUpdate(trip, "system")
            repository.addVehicleTripLink(reservation.id, trip.id, ReservationVehicleTripType.TO_CABIN, start, end)
            repository.setCheckIn(CheckIn(start, CheckInOutSource.CHECK_IN_SENSOR, "s1"), reservation.id, start, end)

            val updated = repository.updateReservation(reservation.id, 1, start, end, listOf("a", "b"), "admin")
                .shouldBeInstanceOf<ReservationUpdateOutcome.Updated>().reservation

            updated.checkIn.shouldNotBeNull()
            updated.toCabinVehicleTrips shouldHaveSize 1
        }

        "delete should remove the reservation and its guest links, and queue a calendar delete" {
            givenGuests()
            val reservation = created(guestIds = arrayOf("a", "b"))

            repository.deleteReservation(reservation.id, 1, "admin") shouldBe ReservationDeleteOutcome.Deleted

            repository.reservationById(reservation.id).shouldBeNull()
            transaction { ReservationGuestTable.selectAll().toList().shouldBeEmpty() }
            outbox() shouldContainExactly
                listOf(reservation.id to CalendarOperation.INSERT, reservation.id to CalendarOperation.DELETE)
        }

        "delete should answer NotFound for an unknown id and VersionMismatch for a stale version, and delete nothing" {
            givenGuests()
            val reservation = created()

            repository.deleteReservation("ghost", 1, "admin") shouldBe ReservationDeleteOutcome.NotFound
            repository.deleteReservation(reservation.id, 5, "admin") shouldBe ReservationDeleteOutcome.VersionMismatch
            repository.reservationById(reservation.id).shouldNotBeNull()
            outbox() shouldHaveSize 1
        }

        "a version that was read before a change should not delete the reservation any more" {
            givenGuests()
            val reservation = created()
            repository.updateReservation(reservation.id, 1, start, end, listOf("a", "b"), "admin")

            repository.deleteReservation(reservation.id, 1, "admin") shouldBe ReservationDeleteOutcome.VersionMismatch
            repository.deleteReservation(reservation.id, 2, "admin") shouldBe ReservationDeleteOutcome.Deleted
        }

        "create should store the own stay of a guest and read it back, a guest without dates follows the reservation" {
            givenGuests()
            val stays = listOf(
                ReservationGuest("a"),
                ReservationGuest("b", fromDate = LocalDate(2026, 7, 11), toDate = LocalDate(2026, 7, 12)),
            )

            val created = repository.createReservation(start, end, stays, "admin")
                .shouldBeInstanceOf<ReservationCreateOutcome.Created>().reservation

            created.guests shouldBe stays
            repository.reservationById(created.id)!!.guests shouldBe stays
            repository.allReservations().single().guests shouldBe stays
        }

        "create should keep the two ends of a stay independent" {
            givenGuests()
            val stays = listOf(
                ReservationGuest("a", fromDate = LocalDate(2026, 7, 11)),
                ReservationGuest("b", toDate = LocalDate(2026, 7, 11)),
            )

            val created = repository.createReservation(start, end, stays, "admin")
                .shouldBeInstanceOf<ReservationCreateOutcome.Created>().reservation

            repository.reservationById(created.id)!!.guests shouldBe stays
        }

        "update should count a changed stay as a change: bump the version, keep the derived data and queue nothing" {
            givenGuests()
            val reservation = created(guestIds = arrayOf("a", "b"))
            val stays = listOf(ReservationGuest("a"), ReservationGuest("b", fromDate = LocalDate(2026, 7, 11)))

            val outcome = repository.updateReservation(reservation.id, 1, start, end, stays, "admin")
                .shouldBeInstanceOf<ReservationUpdateOutcome.Updated>()

            outcome.timesChanged shouldBe false
            outcome.reservation.guests shouldBe stays
            outcome.reservation.version shouldBe 2
            outbox() shouldContainExactly listOf(reservation.id to CalendarOperation.INSERT)
        }

        "update should answer Unchanged when the stays are the same and reset a stay that is left out" {
            givenGuests()
            val stays = listOf(ReservationGuest("a"), ReservationGuest("b", toDate = LocalDate(2026, 7, 11)))
            val reservation = repository.createReservation(start, end, stays, "admin")
                .shouldBeInstanceOf<ReservationCreateOutcome.Created>().reservation

            repository.updateReservation(reservation.id, 1, start, end, stays.reversed(), "admin")
                .shouldBeInstanceOf<ReservationUpdateOutcome.Unchanged>()

            val reset = repository.updateReservation(reservation.id, 1, start, end, listOf("a", "b"), "admin")
                .shouldBeInstanceOf<ReservationUpdateOutcome.Updated>().reservation

            reset.guests shouldBe listOf(ReservationGuest("a"), ReservationGuest("b"))
        }

        "addOrUpdate should write and change the stays" {
            givenGuests()
            val stays = listOf(ReservationGuest("a", fromDate = LocalDate(2026, 7, 11)))
            val reservation = Reservation("old", start, end, stays)

            repository.addOrUpdate(reservation) shouldBe PersistenceResult.ADDED
            repository.reservationById("old")!!.guests shouldBe stays

            val changed = reservation.copy(guests = listOf(ReservationGuest("a", toDate = LocalDate(2026, 7, 11))))
            repository.addOrUpdate(changed) shouldBe PersistenceResult.UPDATED
            repository.reservationById("old")!!.guests shouldBe changed.guests
        }
    })
