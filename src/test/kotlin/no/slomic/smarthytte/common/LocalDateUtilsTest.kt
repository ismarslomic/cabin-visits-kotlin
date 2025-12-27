package no.slomic.smarthytte.common

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

class LocalDateUtilsTest :
    ShouldSpec({
        context("daysUntilSafe") {
            should("Should return the correct number of days between start and end (exclusive)") {
                val start = LocalDate(2024, Month.JUNE, 1)
                val end = LocalDate(2024, Month.JUNE, 11)

                val actualDays = start.daysUntilSafe(end)
                val expectedDays = 10

                actualDays shouldBe expectedDays
            }

            should("Should return 0 if start date is the same as end date") {
                val date = LocalDate(2024, Month.JUNE, 1)

                val actualDays = date.daysUntilSafe(date)
                val expectedDays = 0

                actualDays shouldBe expectedDays
            }

            should("Should return 0 if end date is before start date") {
                val start = LocalDate(2024, Month.JUNE, 10)
                val end = LocalDate(2024, Month.JUNE, 1)

                val actualDays = start.daysUntilSafe(end)
                val expectedDays = 0

                actualDays shouldBe expectedDays
            }

            should("Should handle ranges spanning across month and year boundaries") {
                val start = LocalDate(2023, Month.DECEMBER, 30)
                val end = LocalDate(2024, Month.JANUARY, 2)

                val actualDays = start.daysUntilSafe(end)
                // Dec 30 to Jan 2 is 3 days (30, 31, 1)
                val expectedDays = 3

                actualDays shouldBe expectedDays
            }

            should("Should correctly handle leap years") {
                val start = LocalDate(2024, Month.FEBRUARY, 28)
                val end = LocalDate(2024, Month.MARCH, 1)

                val actualDays = start.daysUntilSafe(end)
                // 2024 is a leap year, so Feb 28 to March 1 is 2 days (28, 29)
                val expectedDays = 2

                actualDays shouldBe expectedDays
            }
        }

        context("datesUntil") {
            should("Should return a sequence of dates between start and end (exclusive)") {
                val start = LocalDate(2024, Month.JUNE, 1)
                val end = LocalDate(2024, Month.JUNE, 5)

                val actualDates = start.datesUntil(end).toList()
                val expectedDates = listOf(
                    LocalDate(2024, Month.JUNE, 1),
                    LocalDate(2024, Month.JUNE, 2),
                    LocalDate(2024, Month.JUNE, 3),
                    LocalDate(2024, Month.JUNE, 4),
                )

                actualDates shouldBe expectedDates
            }

            should("Should return an empty sequence if start date is equal to end date") {
                val date = LocalDate(2024, Month.JUNE, 1)

                val actualDates = date.datesUntil(date).toList()
                val expectedDates = emptyList<LocalDate>()

                actualDates shouldBe expectedDates
            }

            should("Should return an empty sequence if start date is after end date") {
                val start = LocalDate(2024, Month.JUNE, 10)
                val end = LocalDate(2024, Month.JUNE, 1)

                val actualDates = start.datesUntil(end).toList()
                val expectedDates = emptyList<LocalDate>()

                actualDates shouldBe expectedDates
            }

            should("Should handle ranges spanning across month and year boundaries") {
                val start = LocalDate(2023, Month.DECEMBER, 30)
                val end = LocalDate(2024, Month.JANUARY, 2)

                val actualDates = start.datesUntil(end).toList()
                val expectedDates = listOf(
                    LocalDate(2023, Month.DECEMBER, 30),
                    LocalDate(2023, Month.DECEMBER, 31),
                    LocalDate(2024, Month.JANUARY, 1),
                )

                actualDates shouldBe expectedDates
            }

            should("Should correctly handle leap days (Feb 29th)") {
                val start = LocalDate(2024, Month.FEBRUARY, 28)
                val end = LocalDate(2024, Month.MARCH, 2)

                val actualDates = start.datesUntil(end).toList()
                val expectedDates = listOf(
                    LocalDate(2024, Month.FEBRUARY, 28),
                    LocalDate(2024, Month.FEBRUARY, 29),
                    LocalDate(2024, Month.MARCH, 1),
                )

                actualDates shouldBe expectedDates
            }
        }

        context("isoWeekId") {
            should("Should return correct week-based year and week number for standard dates") {
                // June 1st, 2024 is a Saturday in Week 22
                LocalDate(2024, Month.JUNE, 1).isoWeekId() shouldBe (2024 to 22)

                // May 1st, 2024 is a Wednesday in Week 18
                LocalDate(2024, Month.MAY, 1).isoWeekId() shouldBe (2024 to 18)
            }

            should("Should handle year boundaries where the week belongs to the previous year") {
                // January 1st, 2021 (Friday) belongs to Week 53 of 2020
                LocalDate(2021, Month.JANUARY, 1).isoWeekId() shouldBe (2020 to 53)

                // January 3rd, 2021 (Sunday) also belongs to Week 53 of 2020
                LocalDate(2021, Month.JANUARY, 3).isoWeekId() shouldBe (2020 to 53)

                // January 4th, 2021 (Monday) starts Week 1 of 2021
                LocalDate(2021, Month.JANUARY, 4).isoWeekId() shouldBe (2021 to 1)
            }

            should("Should handle year boundaries where the week belongs to the next year") {
                // December 29th, 2025 (Monday) belongs to Week 1 of 2026
                // (Week 1 is the week with the first Thursday of the year)
                LocalDate(2025, Month.DECEMBER, 29).isoWeekId() shouldBe (2026 to 1)

                // December 31st, 2025 (Wednesday) belongs to Week 1 of 2026
                LocalDate(2025, Month.DECEMBER, 31).isoWeekId() shouldBe (2026 to 1)
            }

            should("Should correctly identify week 53 in leap years or years starting/ending on Thursdays") {
                // 2020 was a leap year starting on Wednesday,
                // resulting in 53 ISO weeks.
                LocalDate(2020, Month.DECEMBER, 31).isoWeekId() shouldBe (2020 to 53)
            }
        }

        context("minutesOfDay") {
            should("Should return minutes of the day for a given LocalTime") {
                val localTime = LocalTime(hour = 12, minute = 30)

                val actualMinutesOfDay = localTime.minutesOfDay()
                val expectedMinutesOfDay = 750

                actualMinutesOfDay shouldBe expectedMinutesOfDay
            }
        }

        context("firstDayOfYear") {
            should("Should return january first of given year") {
                val year = 2023

                val actualFirstDayOfYear = firstDayOfYear(year)
                val expectedFirstDayOfYear = LocalDate(year, Month.JANUARY, 1)

                actualFirstDayOfYear shouldBe expectedFirstDayOfYear
            }
        }

        context("firstDayOfYearAfter") {
            should("Should return january first of year after given year") {
                val year = 2023

                val actualFirstDayOfYear = firstDayOfYearAfter(year)
                val expectedFirstDayOfYear = LocalDate(year + 1, Month.JANUARY, 1)

                actualFirstDayOfYear shouldBe expectedFirstDayOfYear
            }
        }
    })
