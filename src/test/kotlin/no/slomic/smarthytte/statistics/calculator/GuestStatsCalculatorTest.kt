package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.statistics.model.GuestPeriodStats

class GuestStatsCalculatorTest :
    ShouldSpec({
        val guest1 = Guest("g1", "John", "Doe", 1990, "john@example.com", Gender.MALE)
        val guest2 = Guest("g2", "Jane", "Smith", 1995, "jane@example.com", Gender.FEMALE)
        val guestsById = mapOf("g1" to guest1, "g2" to guest2)

        fun createReservation(id: String, start: LocalDate, end: LocalDate, guestIds: List<String>): Reservation =
            Reservation(
                id = id,
                startTime = start.atTime(0, 0).toInstant(TimeZone.UTC),
                endTime = end.atTime(0, 0).toInstant(TimeZone.UTC),
                guestIds = guestIds,
            )

        context("aggregateGuestVisitStats") {
            should("aggregate visits and stay days for guests") {
                val periodStart = LocalDate(2024, Month.JANUARY, 1)
                val periodEnd = LocalDate(2024, Month.JANUARY, 31)
                val reservations = listOf(
                    createReservation("1", LocalDate(2024, 1, 1), LocalDate(2024, 1, 6), listOf("g1", "g2")),
                    createReservation("2", LocalDate(2024, 1, 10), LocalDate(2024, 1, 15), listOf("g1")),
                )

                val result = aggregateGuestVisitStats(
                    periodStart = periodStart,
                    periodEndExclusive = periodEnd,
                    reservations = reservations,
                    guestsById = guestsById,
                    ageYear = 2024,
                )

                result shouldHaveSize 2
                val g1Stats = result.find { it.guestId == "g1" }!!
                g1Stats.totalVisits shouldBe 2
                g1Stats.totalDays shouldBe 12 // reservation 1: days 1-6 (6), reservation 2: days 10-15 (6)
                g1Stats.age shouldBe 34

                val g2Stats = result.find { it.guestId == "g2" }!!
                g2Stats.totalVisits shouldBe 1
                g2Stats.totalDays shouldBe 6 // reservation 1: days 1-6 (6)
                g2Stats.age shouldBe 29
            }

            should("count visits by arrival date and days within the period for cross-period stays") {
                val reservations = listOf(
                    // Started in June, continues into July: July days but no July visit
                    createReservation("1", LocalDate(2025, 6, 27), LocalDate(2025, 7, 14), listOf("g1", "g2")),
                    // Started in July
                    createReservation("2", LocalDate(2025, 7, 20), LocalDate(2025, 7, 23), listOf("g1")),
                    // Started and ended in June: not present in July
                    createReservation("3", LocalDate(2025, 6, 1), LocalDate(2025, 6, 3), listOf("g2")),
                )

                val result = aggregateGuestVisitStats(
                    periodStart = LocalDate(2025, 7, 1),
                    periodEndExclusive = LocalDate(2025, 8, 1),
                    reservations = reservations,
                    guestsById = guestsById,
                    ageYear = 2025,
                )

                result shouldHaveSize 2
                val g1 = result.single { it.guestId == "g1" }
                g1.totalVisits shouldBe 1
                g1.totalDays shouldBe 18 // 1-14 July (14) + 20-23 July (4)
                val g2 = result.single { it.guestId == "g2" }
                g2.totalVisits shouldBe 0
                g2.totalDays shouldBe 14
            }

            should("return empty list when no reservations are provided") {
                val result = aggregateGuestVisitStats(
                    periodStart = LocalDate(2024, 1, 1),
                    periodEndExclusive = LocalDate(2024, 1, 31),
                    reservations = emptyList(),
                    guestsById = guestsById,
                    ageYear = 2024,
                )
                result shouldBe emptyList()
            }

            should("ignore guests not found in guestsById") {
                val reservations = listOf(
                    createReservation("1", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2), listOf("unknown")),
                )
                val result = aggregateGuestVisitStats(
                    periodStart = LocalDate(2024, 1, 1),
                    periodEndExclusive = LocalDate(2024, 1, 31),
                    reservations = reservations,
                    guestsById = guestsById,
                    ageYear = 2024,
                )
                result shouldBe emptyList()
            }
        }

        context("calculateMonthlyGuestStats") {
            should("calculate and sort monthly guest stats") {
                val dates = MonthDates(2024, Month.JANUARY)
                val reservations = listOf(
                    createReservation("1", LocalDate(2024, 1, 1), LocalDate(2024, 1, 6), listOf("g1")),
                    createReservation("2", LocalDate(2024, 1, 10), LocalDate(2024, 1, 20), listOf("g2")),
                )
                val result = calculateMonthlyGuestStats(
                    year = 2024,
                    guestsById = guestsById,
                    dates = dates,
                    allReservations = reservations,
                )

                result shouldHaveSize 2
                result[0].guestId shouldBe "g2"
                result[1].guestId shouldBe "g1"
            }
        }

        context("calculateLiveGuestStats") {
            val guest3 = Guest("g3", "Ola", "Nordmann", 2000, null, Gender.MALE)
            val allGuestsById = guestsById + ("g3" to guest3)
            val today = LocalDate(2026, 9, 29)

            val pastLastYear = createReservation("1", LocalDate(2025, 6, 1), LocalDate(2025, 6, 5), listOf("g1", "g2"))
            val pastThisYear = createReservation("2", LocalDate(2026, 3, 10), LocalDate(2026, 3, 12), listOf("g1"))
            val current = createReservation("3", LocalDate(2026, 9, 27), LocalDate(2026, 10, 2), listOf("g2", "g1"))
            val next = createReservation("4", LocalDate(2026, 10, 10), LocalDate(2026, 10, 12), listOf("g3", "g2"))
            val allReservations = listOf(pastLastYear, pastThisYear, current, next)

            should("count started reservations and days up to today for guests in current reservation") {
                val result = calculateLiveGuestStats(current, today, allReservations, allGuestsById)

                result.map { it.guestId } shouldBe listOf("g1", "g2")

                val g1 = result[0]
                g1.firstName shouldBe "John"
                g1.lastName shouldBe "Doe"
                g1.age shouldBe 36
                g1.isFirstVisit shouldBe false
                g1.firstVisitDate shouldBe LocalDate(2025, 6, 1)
                g1.lastVisitDate shouldBe LocalDate(2026, 3, 12)
                g1.yearsVisited shouldBe listOf(2026, 2025)
                g1.currentYear shouldBe GuestPeriodStats(totalVisits = 2, totalDays = 6, visitsRank = 1, daysRank = 1)
                g1.allTime shouldBe GuestPeriodStats(totalVisits = 3, totalDays = 11, visitsRank = 1, daysRank = 1)

                val g2 = result[1]
                g2.isFirstVisit shouldBe false
                g2.lastVisitDate shouldBe LocalDate(2025, 6, 5)
                g2.currentYear shouldBe GuestPeriodStats(totalVisits = 1, totalDays = 3, visitsRank = 2, daysRank = 2)
                g2.allTime shouldBe GuestPeriodStats(totalVisits = 2, totalDays = 8, visitsRank = 2, daysRank = 2)
            }

            should("set avatar url only for guests with an avatar") {
                val avatarUrls = mapOf("g1" to "/api/guests/g1/avatar?v=1")

                val result = calculateLiveGuestStats(current, today, allReservations, allGuestsById, avatarUrls)

                result.single { it.guestId == "g1" }.avatarUrl shouldBe "/api/guests/g1/avatar?v=1"
                result.single { it.guestId == "g2" }.avatarUrl shouldBe null
            }

            should("use last completed visit and exclude future visits for guests in next reservation") {
                val result = calculateLiveGuestStats(next, today, allReservations, allGuestsById)

                result.map { it.guestId } shouldBe listOf("g2", "g3")

                val g2 = result[0]
                g2.isFirstVisit shouldBe false
                g2.lastVisitDate shouldBe LocalDate(2025, 6, 5) // current reservation has not ended yet

                val g3 = result[1]
                g3.isFirstVisit shouldBe true
                g3.firstVisitDate shouldBe null
                g3.lastVisitDate shouldBe null
                g3.yearsVisited shouldBe emptyList()
                g3.currentYear shouldBe
                    GuestPeriodStats(totalVisits = 0, totalDays = 0, visitsRank = null, daysRank = null)
                g3.allTime shouldBe GuestPeriodStats(totalVisits = 0, totalDays = 0, visitsRank = null, daysRank = null)
            }

            should("use current reservation as first visit for first-time guests in current reservation") {
                val firstTimer = createReservation("1", LocalDate(2026, 9, 28), LocalDate(2026, 9, 30), listOf("g3"))

                val g3 = calculateLiveGuestStats(firstTimer, today, listOf(firstTimer), allGuestsById).single()

                g3.isFirstVisit shouldBe true
                g3.firstVisitDate shouldBe LocalDate(2026, 9, 28)
                g3.lastVisitDate shouldBe null
                g3.yearsVisited shouldBe listOf(2026)
            }

            should("give tied guests the same rank and skip the next rank") {
                val shared = createReservation("1", LocalDate(2026, 1, 1), LocalDate(2026, 1, 3), listOf("g1", "g2"))
                val single = createReservation("2", LocalDate(2026, 2, 1), LocalDate(2026, 2, 1), listOf("g3"))

                val result = calculateLiveGuestStats(single, today, listOf(shared, single), allGuestsById)

                result.single().currentYear shouldBe
                    GuestPeriodStats(totalVisits = 1, totalDays = 1, visitsRank = 1, daysRank = 3)
            }

            should("ignore guests not found in guestsById") {
                val reservation =
                    createReservation("1", LocalDate(2026, 1, 1), LocalDate(2026, 1, 2), listOf("unknown"))

                calculateLiveGuestStats(reservation, today, listOf(reservation), allGuestsById) shouldBe emptyList()
            }
        }
    })
