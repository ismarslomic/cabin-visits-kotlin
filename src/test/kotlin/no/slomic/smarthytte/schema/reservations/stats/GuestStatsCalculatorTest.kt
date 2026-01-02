package no.slomic.smarthytte.schema.reservations.stats

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
                    createReservation("1", LocalDate(2024, 1, 1), LocalDate(2024, 1, 6), listOf("g1", "g2")), // 5 days
                    createReservation("2", LocalDate(2024, 1, 10), LocalDate(2024, 1, 15), listOf("g1")), // 5 days
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
                g1Stats.totalStayDays shouldBe 10
                g1Stats.age shouldBe 34 // 2024 - 1990

                val g2Stats = result.find { it.guestId == "g2" }!!
                g2Stats.totalVisits shouldBe 1
                g2Stats.totalStayDays shouldBe 5
                g2Stats.age shouldBe 29 // 2024 - 1995
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
                    createReservation("1", LocalDate(2024, 1, 1), LocalDate(2024, 1, 6), listOf("g1")), // 5 days
                    createReservation("2", LocalDate(2024, 1, 10), LocalDate(2024, 1, 20), listOf("g2")), // 10 days
                )
                val context = MonthStatsContext(
                    year = 2024,
                    yearReservations = reservations,
                    allReservations = reservations,
                    countsByMonth = emptyMap(),
                    guestsById = guestsById,
                    cabinTrips = emptyList(),
                )

                val result = calculateMonthlyGuestStats(context, dates, reservations)

                result shouldHaveSize 2
                // Sorted by totalStayDays descending by GuestVisitStats.COMPARATOR
                result[0].guestId shouldBe "g2"
                result[1].guestId shouldBe "g1"
            }
        }
    })
