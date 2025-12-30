package no.slomic.smarthytte.schema.reservations.stats

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.averageOrNullInt
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.averageRounded1OrNull
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.countByMonth
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.countOccupiedDaysInWindow
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.formatClock
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.formatMinutes
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.formatSignedMinutes
import no.slomic.smarthytte.schema.reservations.stats.ReservationStatsUtils.guestStatsComparator

class ReservationStatsUtilsTest :
    ShouldSpec({
        fun createReservation(id: String, start: LocalDate, end: LocalDate): Reservation = Reservation(
            id = id,
            startTime = start.atTime(0, 0).toInstant(TimeZone.UTC),
            endTime = end.atTime(0, 0).toInstant(TimeZone.UTC),
            guestIds = emptyList(),
        )

        context("countByMonth") {
            should("count reservations by their start month") {
                val reservations = listOf(
                    createReservation(
                        id = "1",
                        start = LocalDate(2024, Month.JANUARY, 1),
                        end = LocalDate(2024, Month.JANUARY, 5),
                    ),
                    createReservation(
                        id = "2",
                        start = LocalDate(2024, Month.JANUARY, 10),
                        end = LocalDate(2024, Month.JANUARY, 15),
                    ),
                    createReservation(
                        id = "3",
                        start = LocalDate(2024, Month.FEBRUARY, 1),
                        end = LocalDate(2024, Month.FEBRUARY, 5),
                    ),
                )

                val result = reservations.countByMonth()

                result[Month.JANUARY] shouldBe 2
                result[Month.FEBRUARY] shouldBe 1
                result[Month.MARCH] shouldBe 0
                result.size shouldBe 12
            }

            should("return zero for all months when list is empty") {
                val result = emptyList<Reservation>().countByMonth()
                result.values.all { it == 0 } shouldBe true
                result.size shouldBe 12
            }
        }

        context("countOccupiedDaysInWindow") {
            val reservations = listOf(
                createReservation(
                    id = "1",
                    start = LocalDate(2024, Month.JANUARY, 1),
                    end = LocalDate(2024, Month.JANUARY, 5),
                ), // Jan 1, 2, 3, 4 (4 days)
                createReservation(
                    id = "2",
                    start = LocalDate(2024, Month.JANUARY, 4),
                    end = LocalDate(2024, Month.JANUARY, 7),
                ), // Jan 4, 5, 6 (3 days)
            )

            should("count unique occupied days within the window") {
                // Jan 1, 2, 3, 4, 5, 6 = 6 days
                val start = LocalDate(2024, Month.JANUARY, 1)
                val end = LocalDate(2024, Month.JANUARY, 10)
                reservations.countOccupiedDaysInWindow(start, end) shouldBe 6
            }

            should("respect window boundaries") {
                // Window Jan 2 to Jan 5.
                // R1 overlaps on Jan 2, 3, 4.
                // R2 overlaps on Jan 4.
                // Unique: Jan 2, 3, 4 = 3 days
                val start = LocalDate(2024, Month.JANUARY, 2)
                val end = LocalDate(2024, Month.JANUARY, 5)
                reservations.countOccupiedDaysInWindow(start, end) shouldBe 3
            }

            should("return 0 if start is after or equal to end") {
                val start = LocalDate(2024, Month.JANUARY, 10)
                val end = LocalDate(2024, Month.JANUARY, 1)
                reservations.countOccupiedDaysInWindow(start, end) shouldBe 0
            }

            should("return 0 if no reservations overlap the window") {
                val start = LocalDate(2024, Month.FEBRUARY, 1)
                val end = LocalDate(2024, Month.FEBRUARY, 10)
                reservations.countOccupiedDaysInWindow(start, end) shouldBe 0
            }
        }

        context("formatMinutes") {
            should("format minutes as HH:MM") {
                formatMinutes(90) shouldBe "01:30"
                formatMinutes(1500) shouldBe "25:00"
                formatMinutes(0) shouldBe "00:00"
            }

            should("handle null input") {
                formatMinutes(null) shouldBe null
            }

            should("handle negative minutes by using absolute value") {
                formatMinutes(-90) shouldBe "01:30"
            }
        }

        context("formatSignedMinutes") {
            should("format positive minutes with + prefix") {
                formatSignedMinutes(90) shouldBe "+01:30"
                formatSignedMinutes(1500) shouldBe "+25:00"
            }

            should("format negative minutes with - prefix") {
                formatSignedMinutes(-90) shouldBe "-01:30"
                formatSignedMinutes(-1500) shouldBe "-25:00"
            }

            should("format zero minutes without prefix") {
                formatSignedMinutes(0) shouldBe "00:00"
            }

            should("handle null input") {
                formatSignedMinutes(null) shouldBe null
            }
        }

        context("formatClock") {
            should("format minutes of day as HH:MM with modulo 24") {
                formatClock(90) shouldBe "01:30"
                formatClock(1440) shouldBe "00:00"
                formatClock(1500) shouldBe "01:00"
            }

            should("handle null input") {
                formatClock(null) shouldBe null
            }
        }

        context("averageRounded1OrNull") {
            should("return null for empty list") {
                emptyList<Int>().averageRounded1OrNull() shouldBe null
            }

            should("calculate rounded average for list of integers") {
                listOf(1, 2).averageRounded1OrNull() shouldBe 1.5
                listOf(1, 2, 4).averageRounded1OrNull() shouldBe 2.3
                listOf(10).averageRounded1OrNull() shouldBe 10.0
            }
        }

        context("averageOrNullInt") {
            should("return null for empty list") {
                emptyList<Int>().averageOrNullInt() shouldBe null
            }

            should("calculate truncated integer average for list of integers") {
                listOf(1, 2).averageOrNullInt() shouldBe 1 // 1.5 -> 1
                listOf(1, 2, 4).averageOrNullInt() shouldBe 2 // 2.33 -> 2
                listOf(10).averageOrNullInt() shouldBe 10
            }
        }

        context("guestStatsComparator") {
            val guest1 = GuestVisitStats("1", "A", "Z", 30, 5, 10)
            val guest2 = GuestVisitStats("2", "B", "Y", 30, 5, 15) // More stay days
            val guest3 = GuestVisitStats("3", "C", "X", 30, 10, 10) // More visits, same stay days as guest 1
            val guest4 =
                GuestVisitStats("4", "A", "X", 30, 5, 10) // Same visits/stay days as guest 1, but earlier last name
            val guest5 = GuestVisitStats("5", "B", "X", 30, 5, 10) // Same everything as guest 4, but later first name

            should("sort by totalStayDays descending") {
                val list = listOf(guest1, guest2)
                list.sortedWith(guestStatsComparator()) shouldBe listOf(guest2, guest1)
            }

            should("sort by totalVisits descending when stay days are equal") {
                val list = listOf(guest1, guest3)
                list.sortedWith(guestStatsComparator()) shouldBe listOf(guest3, guest1)
            }

            should("sort by lastName ascending when stay days and visits are equal") {
                val list = listOf(guest1, guest4)
                list.sortedWith(guestStatsComparator()) shouldBe listOf(guest4, guest1)
            }

            should("sort by firstName ascending when stay days, visits, and last name are equal") {
                val list = listOf(guest4, guest5)
                list.sortedWith(guestStatsComparator()) shouldBe listOf(guest4, guest5)
            }

            should("correctly sort a complex list") {
                val list = listOf(guest1, guest2, guest3, guest4, guest5)
                val expectedOrder = listOf(
                    guest2, // 15 days
                    guest3, // 10 days, 10 visits
                    guest4, // 10 days, 5 visits, lastName "X", firstName "A"
                    guest5, // 10 days, 5 visits, lastName "X", firstName "B"
                    guest1, // 10 days, 5 visits, lastName "Z"
                )
                list.sortedWith(guestStatsComparator()) shouldBe expectedOrder
            }
        }
    })
