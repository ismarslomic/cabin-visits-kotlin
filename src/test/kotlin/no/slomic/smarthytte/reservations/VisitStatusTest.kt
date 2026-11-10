package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

class VisitStatusTest :
    StringSpec({
        // 2026-07-10 to 2026-07-12 (UTC dates)
        val reservation = Reservation(
            id = "r",
            startTime = Instant.parse("2026-07-10T16:00:00Z"),
            endTime = Instant.parse("2026-07-12T13:00:00Z"),
            guestIds = listOf("a"),
        )

        "visit status should be upcoming before the start date" {
            reservation.visitStatus(LocalDate(2026, 7, 9)) shouldBe VisitStatus.UPCOMING
        }

        "visit status should be ongoing from the start date until the day before the end date" {
            reservation.visitStatus(LocalDate(2026, 7, 10)) shouldBe VisitStatus.ONGOING
            reservation.visitStatus(LocalDate(2026, 7, 11)) shouldBe VisitStatus.ONGOING
        }

        "visit status should be completed from the end date itself on" {
            reservation.visitStatus(LocalDate(2026, 7, 12)) shouldBe VisitStatus.COMPLETED
            reservation.visitStatus(LocalDate(2026, 7, 13)) shouldBe VisitStatus.COMPLETED
        }
    })
