package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.guests.SqliteGuestRepository
import no.slomic.smarthytte.utils.TestDbSetup

class ReservationReadRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }
        afterTest { testDbSetup.teardownDb() }

        val repository = SqliteReservationRepository()

        suspend fun ids(guestId: String? = null, from: LocalDate? = null, to: LocalDate? = null) =
            repository.reservations(guestId, from, to).map { it.id }

        suspend fun given() = givenReservations(SqliteGuestRepository(), repository)

        "reservations without parameters should return all, sorted by start time and then id" {
            given()

            ids() shouldBe listOf("r1", "r2", "r0", "r3a", "r3b")
        }

        "reservations should include one that spans a month boundary in both months" {
            given()

            ids(from = LocalDate(2026, 3, 1), to = LocalDate(2026, 4, 1)) shouldBe listOf("r1")
            ids(from = LocalDate(2026, 4, 1), to = LocalDate(2026, 5, 1)) shouldBe listOf("r1", "r2")
        }

        "reservations should treat from as inclusive and to as exclusive on the UTC date" {
            given()

            // r1 ends 2026-04-02: on from it overlaps, a day later it does not
            ids(from = LocalDate(2026, 4, 2), to = LocalDate(2026, 4, 3)) shouldBe listOf("r1")
            ids(from = LocalDate(2026, 4, 3), to = LocalDate(2026, 4, 10)) shouldBe emptyList()
            // r1 starts 2026-03-30: to the same day excludes it, to the day after includes it
            ids(to = LocalDate(2026, 3, 30)) shouldBe emptyList()
            ids(to = LocalDate(2026, 3, 31)) shouldBe listOf("r1")
        }

        "reservations should support only from or only to" {
            given()

            ids(from = LocalDate(2026, 5, 5)) shouldBe listOf("r3a", "r3b")
            ids(to = LocalDate(2026, 4, 11)) shouldBe listOf("r1", "r2")
        }

        "reservations should show a 0-night reservation inside one UTC day for that day only" {
            given()

            ids(from = LocalDate(2026, 5, 4), to = LocalDate(2026, 5, 5)) shouldBe listOf("r0")
            ids(from = LocalDate(2026, 5, 3), to = LocalDate(2026, 5, 4)) shouldBe emptyList()
            ids(from = LocalDate(2026, 5, 5), to = LocalDate(2026, 5, 6)) shouldBe emptyList()
        }

        "reservations should return only the reservations that link the guest" {
            given()

            ids(guestId = "b") shouldBe listOf("r1", "r2")
            ids(guestId = "c") shouldBe listOf("r3a", "r3b")
            ids(guestId = "outsider") shouldBe emptyList()
        }

        "reservations should combine the guest filter with the window" {
            given()

            ids(guestId = "b", from = LocalDate(2026, 4, 5), to = LocalDate(2026, 4, 30)) shouldBe listOf("r2")
        }
    })
