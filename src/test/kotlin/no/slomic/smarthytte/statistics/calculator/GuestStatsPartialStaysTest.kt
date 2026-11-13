package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.reservations.ReservationGuest
import no.slomic.smarthytte.reservations.VisitStatus
import no.slomic.smarthytte.statistics.model.GuestVisit

/** The per-guest numbers count with the guest's own stay, see GuestStay. */
class GuestStatsPartialStaysTest :
    ShouldSpec({
        val guestsById = mapOf(
            "g1" to Guest("g1", "John", "Doe", 1990, "john@example.com", Gender.MALE),
            "g2" to Guest("g2", "Jane", "Smith", 1995, "jane@example.com", Gender.FEMALE),
        )

        fun reservation(id: String, start: LocalDate, end: LocalDate, vararg guests: ReservationGuest) = Reservation(
            id = id,
            startTime = start.atStartOfDayIn(TimeZone.UTC),
            endTime = end.atStartOfDayIn(TimeZone.UTC),
            guests = guests.toList(),
        )

        // 29 Jan - 3 Feb: g1 the whole stay, g2 only 1-2 Feb
        val crossMonth = reservation(
            "r1",
            LocalDate(2026, 1, 29),
            LocalDate(2026, 2, 3),
            ReservationGuest("g1"),
            ReservationGuest("g2", LocalDate(2026, 2, 1), LocalDate(2026, 2, 2)),
        )

        context("aggregateGuestVisitStats") {
            should("count a visit in the month of the guest's own arrival and the days of the own stay") {
                fun month(from: LocalDate, toExclusive: LocalDate) = aggregateGuestVisitStats(
                    from,
                    toExclusive,
                    listOf(crossMonth),
                    guestsById,
                    ageYear = 2026,
                ).associate { it.guestId to (it.totalVisits to it.totalDays) }

                // g2 arrives in February: no visit and no day in January
                month(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1)) shouldBe mapOf("g1" to (1 to 3))
                month(LocalDate(2026, 2, 1), LocalDate(2026, 3, 1)) shouldBe
                    mapOf("g1" to (0 to 3), "g2" to (1 to 2))
            }
        }

        context("computeYearGuestStats") {
            should("not call a guest new when they arrive this year in a reservation that started last year") {
                val newYear = reservation(
                    "r1",
                    LocalDate(2025, 12, 30),
                    LocalDate(2026, 1, 3),
                    ReservationGuest("g1"),
                    ReservationGuest("g2", fromDate = LocalDate(2026, 1, 2)),
                )
                val lastYear = reservation("r0", LocalDate(2025, 6, 1), LocalDate(2025, 6, 2), ReservationGuest("g1"))

                val stats = computeYearGuestStats(2026, listOf(lastYear, newYear), guestsById)

                // g2 arrives in 2026 and has no stay last year, g1 arrived last year
                stats.newGuests.map { it.guestId } shouldContainExactly listOf("g2")
            }
        }

        context("calculateGuestStats") {
            should("list every guest and count the own stay of each, up to today") {
                val stats = calculateGuestStats(crossMonth, LocalDate(2026, 2, 2), listOf(crossMonth), guestsById)
                    .associateBy { it.guestId }

                stats.keys shouldBe setOf("g1", "g2")
                stats.getValue("g2").currentYear.let {
                    it.totalVisits shouldBe 1
                    it.totalDays shouldBe 2
                    it.totalNights shouldBe 1
                }
                stats.getValue("g1").currentYear.let {
                    it.totalDays shouldBe 5
                    it.totalNights shouldBe 5
                }
                stats.getValue("g2").firstVisitDate shouldBe LocalDate(2026, 2, 1)
                stats.getValue("g2").yearsVisited shouldBe listOf(2026)
            }

            should("not count a visit for a guest who has not arrived yet, but still list the guest") {
                val stats = calculateGuestStats(crossMonth, LocalDate(2026, 1, 31), listOf(crossMonth), guestsById)
                    .associateBy { it.guestId }

                stats.getValue("g2").let {
                    it.firstVisitDate.shouldBeNull()
                    it.currentYear.totalVisits shouldBe 0
                    it.currentYear.totalDays shouldBe 0
                    it.isFirstVisit shouldBe true
                }
                stats.getValue("g1").currentYear.totalVisits shouldBe 1
            }

            should("order the previous visits by the own arrival of the guest") {
                // g2 arrived on 1 Feb in r1 and arrives on 2 Mar in r2, so r1 is the previous visit
                val later = reservation(
                    "r2",
                    LocalDate(2026, 3, 1),
                    LocalDate(2026, 3, 3),
                    ReservationGuest("g2", fromDate = LocalDate(2026, 3, 2)),
                )
                val stats = calculateGuestStats(later, LocalDate(2026, 3, 2), listOf(crossMonth, later), guestsById)
                    .single()

                stats.isFirstVisit shouldBe false
                stats.lastVisitDate shouldBe LocalDate(2026, 2, 2)
            }
        }

        context("calculateGuestProfileStats") {
            should("list the visits with the own dates, days, nights and status of the guest") {
                val profile = calculateGuestProfileStats(
                    "g2",
                    LocalDate(2026, 2, 1),
                    listOf(crossMonth),
                    guestsById,
                    dataStartDate = LocalDate(2019, 12, 1),
                )!!

                profile.visits shouldBe listOf(
                    GuestVisit(LocalDate(2026, 2, 1), LocalDate(2026, 2, 2), 2, 1, VisitStatus.ONGOING),
                )
                profile.guest.isFirstVisit shouldBe true
            }

            should("show the visit as upcoming for a guest who arrives later in a started reservation") {
                val profile = calculateGuestProfileStats(
                    "g2",
                    LocalDate(2026, 1, 31),
                    listOf(crossMonth),
                    guestsById,
                    dataStartDate = LocalDate(2019, 12, 1),
                )!!

                profile.visits.single().status shouldBe VisitStatus.UPCOMING
                profile.guest.isFirstVisit shouldBe false
                profile.guest.firstVisitDate.shouldBeNull()
            }
        }
    })
