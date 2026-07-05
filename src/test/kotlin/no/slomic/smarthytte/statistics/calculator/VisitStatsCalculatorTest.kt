package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import no.slomic.smarthytte.reservations.Reservation

class VisitStatsCalculatorTest :
    ShouldSpec({
        fun createReservation(id: String, start: LocalDate, end: LocalDate): Reservation = Reservation(
            id = id,
            startTime = start.atTime(0, 0).toInstant(TimeZone.UTC),
            endTime = end.atTime(0, 0).toInstant(TimeZone.UTC),
            guestIds = emptyList(),
        )

        context("calculateMonthlyDaysStats") {
            should("calculate totalDays using occupied days in month window") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)

                // Reservation fully within March: startDate=5, endDate=10 -> days 5,6,7,8,9,10 = 6 days
                val r1 = createReservation("r1", LocalDate(2024, 3, 5), LocalDate(2024, 3, 10))
                // Reservation spanning Feb-March: endDate=6 in March -> days 1,2,3,4,5,6 = 6 days
                val r2 = createReservation("r2", LocalDate(2024, 2, 25), LocalDate(2024, 3, 6))
                // Reservation in February (outside March): should not count
                val r3 = createReservation("r3", LocalDate(2024, 2, 10), LocalDate(2024, 2, 20))

                val result = calculateMonthlyDaysStats(
                    allReservations = listOf(r1, r2, r3),
                    dates = dates,
                )

                // r1 occupies days 5-10 (6 days), r2 occupies days 1-6 (6 days) -> union = days 1-10 = 10 days
                result.totalDays shouldBe 10
            }

            should("calculate min, max and avg days clipped to month boundary") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)

                // Fully within March: startDate=1, endDate=4 -> 4 days (1,2,3,4)
                val r1 = createReservation("r1", LocalDate(2024, 3, 1), LocalDate(2024, 3, 4))
                // Fully within March: startDate=10, endDate=17 -> 8 days (10-17)
                val r2 = createReservation("r2", LocalDate(2024, 3, 10), LocalDate(2024, 3, 17))
                // Spans March-April: endDate=April 6 inclusive, clips to March 31 -> days 27-31 = 5 days
                val r3 = createReservation("r3", LocalDate(2024, 3, 27), LocalDate(2024, 4, 6))

                val result = calculateMonthlyDaysStats(
                    allReservations = listOf(r1, r2, r3),
                    dates = dates,
                )

                result.minDays shouldBe 4
                result.maxDays shouldBe 8 // r3 clips to 5, so r2's 8 is still max
                result.avgDays shouldBe 5.7 // (4 + 8 + 5) / 3
            }

            should("include cross-month reservation in min, max and avg for the overlapping month") {
                val year = 2024
                val month = Month.APRIL
                val dates = MonthDates(year, month)

                // Spans March-April: endDate=April 6 inclusive -> days in April: 1,2,3,4,5,6 = 6 days
                val r1 = createReservation("r1", LocalDate(2024, 3, 27), LocalDate(2024, 4, 6))

                val result = calculateMonthlyDaysStats(
                    allReservations = listOf(r1),
                    dates = dates,
                )

                result.minDays shouldBe 6
                result.maxDays shouldBe 6
                result.avgDays shouldBe 6.0
            }

            should("calculate totalDaysComparedToPreviousMonth") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)

                // March: startDate=1, endDate=6 -> days 1-6 = 6 days
                val march = createReservation("march", LocalDate(2024, 3, 1), LocalDate(2024, 3, 6))
                // Previous month (February): endDate=13 -> days 10-13 = 4 days
                val prevMonth = createReservation("prevMonth", LocalDate(2024, 2, 10), LocalDate(2024, 2, 13))

                val result = calculateMonthlyDaysStats(
                    allReservations = listOf(march, prevMonth),
                    dates = dates,
                )

                result.totalDaysComparedToPreviousMonth shouldBe 2 // 6 - 4
            }

            should("calculate totalDaysComparedToSameMonthLastYear") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)

                // March 2024: startDate=1, endDate=6 -> days 1-6 = 6 days
                val thisYear = createReservation("thisYear", LocalDate(2024, 3, 1), LocalDate(2024, 3, 6))
                // March 2023: startDate=1, endDate=9 -> days 1-9 = 9 days
                val lastYear = createReservation("lastYear", LocalDate(2023, 3, 1), LocalDate(2023, 3, 9))

                val result = calculateMonthlyDaysStats(
                    allReservations = listOf(thisYear, lastYear),
                    dates = dates,
                )

                result.totalDaysComparedToSameMonthLastYear shouldBe -3 // 6 - 9
            }
        }

        context("calculateMonthlyNightsStats") {
            should("calculate totalNights using occupied nights in month window") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)

                // Fully within March: startDate=5, endDate=10 -> nights 5-9 = 5 nights
                val r1 = createReservation("r1", LocalDate(2024, 3, 5), LocalDate(2024, 3, 10))
                // Spans Feb-March: endDate=6 -> nights in March: 1-5 = 5 nights
                val r2 = createReservation("r2", LocalDate(2024, 2, 25), LocalDate(2024, 3, 6))
                // February only: should not count
                val r3 = createReservation("r3", LocalDate(2024, 2, 10), LocalDate(2024, 2, 20))

                val result = calculateMonthlyNightsStats(
                    allReservations = listOf(r1, r2, r3),
                    dates = dates,
                )

                // r1 nights 5-9, r2 nights 1-5 -> union = nights 1-9 = 9 nights
                result.totalNights shouldBe 9
            }

            should("calculate min, max and avg nights clipped to month boundary") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)

                // Fully within March: 3 nights (1,2,3)
                val r1 = createReservation("r1", LocalDate(2024, 3, 1), LocalDate(2024, 3, 4))
                // Fully within March: 7 nights (10-16)
                val r2 = createReservation("r2", LocalDate(2024, 3, 10), LocalDate(2024, 3, 17))
                // Spans March-April: 10 nights total, clips to 5 nights in March (27,28,29,30,31)
                val r3 = createReservation("r3", LocalDate(2024, 3, 27), LocalDate(2024, 4, 6))

                val result = calculateMonthlyNightsStats(
                    allReservations = listOf(r1, r2, r3),
                    dates = dates,
                )

                result.minNights shouldBe 3
                result.maxNights shouldBe 7 // r3 clips to 5, so r2's 7 is still max
                result.avgNights shouldBe 5.0 // (3 + 7 + 5) / 3
            }

            should("calculate totalNightsComparedToPreviousMonth") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)

                // March: nights 1-5 = 5 nights
                val march = createReservation("march", LocalDate(2024, 3, 1), LocalDate(2024, 3, 6))
                // Previous month (February): nights 10-12 = 3 nights
                val prevMonth = createReservation("prevMonth", LocalDate(2024, 2, 10), LocalDate(2024, 2, 13))

                val result = calculateMonthlyNightsStats(
                    allReservations = listOf(march, prevMonth),
                    dates = dates,
                )

                result.totalNightsComparedToPreviousMonth shouldBe 2 // 5 - 3
            }
        }

        context("calculateMonthlyVisitDeltas") {
            should("calculate deltas correctly") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)
                val totalVisits = 3

                val prevMonthReservations = listOf(
                    createReservation("prev1", LocalDate(2024, 2, 10), LocalDate(2024, 2, 15)),
                    createReservation("prev2", LocalDate(2024, 2, 20), LocalDate(2024, 2, 25)),
                )
                val sameMonthLastYearReservations = listOf(
                    createReservation("lastYear1", LocalDate(2023, 3, 1), LocalDate(2023, 3, 5)),
                    createReservation("lastYear2", LocalDate(2023, 3, 10), LocalDate(2023, 3, 15)),
                    createReservation("lastYear3", LocalDate(2023, 3, 20), LocalDate(2023, 3, 25)),
                    createReservation("lastYear4", LocalDate(2023, 3, 28), LocalDate(2023, 3, 30)),
                )
                val countsByMonth = mapOf(
                    Month.JANUARY to 2,
                    Month.FEBRUARY to 4,
                    Month.MARCH to totalVisits,
                )
                val allReservations = prevMonthReservations + sameMonthLastYearReservations

                val result = calculateMonthlyVisitDeltas(
                    allReservations = allReservations,
                    countsByMonth = countsByMonth,
                    dates = dates,
                    totalVisits = totalVisits,
                )

                result.visitsComparedToPreviousMonth shouldBe 1
                result.visitsComparedToSameMonthLastYear shouldBe -1
                result.visitsComparedToYearToDateAverage shouldBe 0.0
            }

            should("count only previous month visits, not earlier months") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)

                val reservations = listOf(
                    createReservation("jan31", LocalDate(2024, 1, 31), LocalDate(2024, 2, 1)),
                    createReservation("feb1", LocalDate(2024, 2, 1), LocalDate(2024, 2, 2)),
                    createReservation("feb29", LocalDate(2024, 2, 29), LocalDate(2024, 3, 1)),
                    createReservation("jan30", LocalDate(2024, 1, 30), LocalDate(2024, 1, 31)),
                    createReservation("mar1", LocalDate(2024, 3, 1), LocalDate(2024, 3, 2)),
                )

                val result = calculateMonthlyVisitDeltas(
                    allReservations = reservations,
                    countsByMonth = emptyMap(),
                    dates = dates,
                    totalVisits = 0,
                )
                // Previous month = February: feb1 and feb29 = 2 visits
                result.visitsComparedToPreviousMonth shouldBe -2
            }
        }
    })
