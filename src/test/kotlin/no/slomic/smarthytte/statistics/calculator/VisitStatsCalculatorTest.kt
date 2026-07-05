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

        context("calculateMonthlyVisitDeltas") {
            should("calculate deltas correctly") {
                val year = 2024
                val month = Month.MARCH
                val dates = MonthDates(year, month)
                val totalVisits = 3

                val last30DaysReservations = listOf(
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
                val allReservations = last30DaysReservations + sameMonthLastYearReservations

                val result = calculateMonthlyVisitDeltas(
                    allReservations = allReservations,
                    countsByMonth = countsByMonth,
                    dates = dates,
                    totalVisits = totalVisits,
                )

                result.visitsComparedToLast30Days shouldBe 1
                result.visitsComparedToSameMonthLastYear shouldBe -1
                result.visitsComparedToYearToDateAverage shouldBe 0.0
            }

            should("handle leap year correctly in last 30 days window") {
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
                result.visitsComparedToLast30Days shouldBe -3
            }
        }
    })
