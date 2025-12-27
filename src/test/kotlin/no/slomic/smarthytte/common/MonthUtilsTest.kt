package no.slomic.smarthytte.common

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

class MonthUtilsTest :
    ShouldSpec({
        should("should return first day of current month and year") {
            val currentYear = 2023
            val currentMonth = Month.JANUARY

            val expectedDate = LocalDate(currentYear, currentMonth, 1)
            val actualDate = firstDateOfThisMonth(currentYear, currentMonth)

            actualDate shouldBe expectedDate
        }

        should("should return first day of next month in current year") {
            val currentYear = 2023
            val currentMonth = Month.JANUARY

            val expectedDate = LocalDate(currentYear, Month.FEBRUARY, 1)
            val actualDate = firstDateOfNextMonth(currentYear, currentMonth)

            actualDate shouldBe expectedDate
        }

        should("should return first day of next month and next year") {
            val currentYear = 2023
            val currentMonth = Month.DECEMBER

            val expectedDate = LocalDate(currentYear + 1, Month.JANUARY, 1)
            val actualDate = firstDateOfNextMonth(currentYear, currentMonth)

            actualDate shouldBe expectedDate
        }

        should("should return previous month in current year") {
            val currentYear = 2023
            val currentMonth = Month.FEBRUARY

            val expectedYearAndMonth = Pair(currentYear, Month.JANUARY)
            val actualYearAndMonth = previousMonth(currentYear, currentMonth)

            actualYearAndMonth shouldBe expectedYearAndMonth
        }

        should("should return previous month and previous year") {
            val currentYear = 2023
            val currentMonth = Month.JANUARY

            val expectedYearAndMonth = Pair(currentYear - 1, Month.DECEMBER)
            val actualYearAndMonth = previousMonth(currentYear, currentMonth)

            actualYearAndMonth shouldBe expectedYearAndMonth
        }
    })
