package no.slomic.smarthytte.schema.reservations.stats

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

        context("calculateMonthlyVisitDeltas") {
            should("calculate deltas correctly") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month) // March 1st, 2024

                // Reservations for current month (March)
                val totalVisits = 3

                // Last 30 days: Feb 1st to Feb 29th (2024 is leap year)
                // Actually the logic is:
                // lastDayPrev = dates.firstOfMonth - 1 day = Feb 29
                // startWindow = dates.firstOfMonth - 30 days = Jan 31
                // So window is Jan 31 to Feb 29.
                val last30DaysReservations = listOf(
                    createReservation("prev1", LocalDate(2024, 2, 10), LocalDate(2024, 2, 15)),
                    createReservation("prev2", LocalDate(2024, 2, 20), LocalDate(2024, 2, 25)),
                ) // 2 visits in last 30 days

                // Same month last year: March 2023
                val sameMonthLastYearReservations = listOf(
                    createReservation("lastYear1", LocalDate(2023, 3, 1), LocalDate(2023, 3, 5)),
                    createReservation("lastYear2", LocalDate(2023, 3, 10), LocalDate(2023, 3, 15)),
                    createReservation("lastYear3", LocalDate(2023, 3, 20), LocalDate(2023, 3, 25)),
                    createReservation("lastYear4", LocalDate(2023, 3, 28), LocalDate(2023, 3, 30)),
                ) // 4 visits same month last year

                // YTD data: Jan, Feb, Mar
                // We need countsByMonth for Jan and Feb, and totalVisits for March
                val countsByMonth = mapOf(
                    Month.JANUARY to 2,
                    Month.FEBRUARY to 4,
                    Month.MARCH to totalVisits,
                )

                val allReservations = last30DaysReservations + sameMonthLastYearReservations

                val context = MonthStatsContext(
                    year = year,
                    yearReservations = emptyList(),
                    allReservations = allReservations,
                    countsByMonth = countsByMonth,
                    guestsById = emptyMap(),
                    cabinTrips = emptyList(),
                )

                val result = calculateMonthlyVisitDeltas(context, month, dates, totalVisits)

                // comparedToLast30Days: 3 - 2 = 1
                result.comparedToLast30Days shouldBe 1

                // comparedToSameMonthLastYear: 3 - 4 = -1
                result.comparedToSameMonthLastYear shouldBe -1

                // YTD Total: 2 (Jan) + 4 (Feb) + 3 (Mar) = 9
                // YTD Average (up to March): 9 / 3 = 3.0
                // comparedToYtdAvg: 3 - 3.0 = 0.0
                result.comparedToYearToDateAverage shouldBe 0.0
            }

            should("handle leap year correctly in last 30 days window") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month) // March 1st

                // lastDayPrev = Feb 29
                // startWindow = March 1st - 30 days = Jan 31
                val reservations = listOf(
                    createReservation("jan31", LocalDate(2024, 1, 31), LocalDate(2024, 2, 1)),
                    createReservation("feb1", LocalDate(2024, 2, 1), LocalDate(2024, 2, 2)),
                    createReservation("feb29", LocalDate(2024, 2, 29), LocalDate(2024, 3, 1)),
                    createReservation("jan30", LocalDate(2024, 1, 30), LocalDate(2024, 1, 31)), // Outside (too early)
                    createReservation(
                        "mar1",
                        LocalDate(2024, 3, 1),
                        LocalDate(2024, 3, 2),
                    ), // Outside (too late for this count)
                )

                val context = MonthStatsContext(
                    year = year,
                    yearReservations = emptyList(),
                    allReservations = reservations,
                    countsByMonth = emptyMap(),
                    guestsById = emptyMap(),
                    cabinTrips = emptyList(),
                )

                val result = calculateMonthlyVisitDeltas(context, month, dates, 0)
                result.comparedToLast30Days shouldBe -3 // 0 - 3
            }
        }
    })
