package no.slomic.smarthytte.reservations

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

class ReservationTest :
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

        context("countInInterval") {
            val reservations = listOf(
                createReservation(
                    id = "1",
                    start = LocalDate(2024, Month.JANUARY, 1),
                    end = LocalDate(2024, Month.JANUARY, 5),
                ),
                createReservation(
                    id = "2",
                    start = LocalDate(2024, Month.JUNE, 1),
                    end = LocalDate(2024, Month.JUNE, 5),
                ),
                createReservation(
                    id = "3",
                    start = LocalDate(2024, Month.DECEMBER, 31),
                    end = LocalDate(2025, Month.JANUARY, 5),
                ),
            )

            should("count reservations starting within the inclusive interval") {
                val start = LocalDate(2024, Month.JANUARY, 1)
                val end = LocalDate(2024, Month.DECEMBER, 31)
                reservations.countInInterval(start, end) shouldBe 3
            }

            should("exclude reservations starting before the interval") {
                val start = LocalDate(2024, Month.FEBRUARY, 1)
                val end = LocalDate(2024, Month.DECEMBER, 31)
                reservations.countInInterval(start, end) shouldBe 2
            }

            should("exclude reservations starting after the interval") {
                val start = LocalDate(2024, Month.JANUARY, 1)
                val end = LocalDate(2024, Month.NOVEMBER, 30)
                reservations.countInInterval(start, end) shouldBe 2
            }

            should("return 0 for an empty list") {
                emptyList<Reservation>().countInInterval(
                    LocalDate(2024, Month.JANUARY, 1),
                    LocalDate(2024, Month.DECEMBER, 31),
                ) shouldBe 0
            }
        }

        context("diffVisitsCurrentYearWithLast12Months") {
            // lastYearInterval(2024) returns 2023-01-01 to 2023-12-31
            val reservations = listOf(
                createReservation(
                    id = "lastYear1",
                    start = LocalDate(2023, Month.JANUARY, 1),
                    end = LocalDate(2023, Month.JANUARY, 5),
                ),
                createReservation(
                    id = "lastYear2",
                    start = LocalDate(2023, Month.JUNE, 1),
                    end = LocalDate(2023, Month.JUNE, 5),
                ),
                createReservation(
                    id = "thisYear1",
                    start = LocalDate(2024, Month.JANUARY, 1),
                    end = LocalDate(2024, Month.JANUARY, 5),
                ),
            )

            should("return positive difference when current year has more visits") {
                // visitsCurrentYear = 3, last 12 months (2023) has 2. Diff = 3 - 2 = 1
                reservations.diffVisitsCurrentYearWithLast12Months(2024, 3) shouldBe 1
            }

            should("return negative difference when last 12 months has more visits") {
                // visitsCurrentYear = 1, last 12 months (2023) has 2. Diff = 1 - 2 = -1
                reservations.diffVisitsCurrentYearWithLast12Months(2024, 1) shouldBe -1
            }

            should("return zero when visits are equal") {
                // visitsCurrentYear = 2, last 12 months (2023) has 2. Diff = 2 - 2 = 0
                reservations.diffVisitsCurrentYearWithLast12Months(2024, 2) shouldBe 0
            }
        }
    })
