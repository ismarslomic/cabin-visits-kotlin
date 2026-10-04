package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.GuestPeriodStats
import no.slomic.smarthytte.statistics.model.LiveGuestStats
import kotlin.random.Random

class FunFactCalculatorTest :
    ShouldSpec({
        val today = LocalDate(2026, 10, 2)
        val dataStart = LocalDate(2020, 9, 1)

        fun period(visits: Int = 0, days: Int = 0) = GuestPeriodStats(visits, days, visitsRank = 1, daysRank = 1)

        fun guest(
            id: String,
            isFamily: Boolean = false,
            isFirstVisit: Boolean = false,
            firstVisitDate: LocalDate? = LocalDate(2021, 1, 1),
            lastVisitDate: LocalDate? = LocalDate(2026, 9, 1),
            yearsVisited: List<Int> = listOf(2026),
            currentYearVisits: Int = 2,
            allTimeVisits: Int = 3,
        ) = LiveGuestStats(
            guestId = id,
            firstName = id.replaceFirstChar { it.uppercase() },
            lastName = "Test",
            age = 30,
            isFamily = isFamily,
            avatarUrl = null,
            isFirstVisit = isFirstVisit,
            firstVisitDate = firstVisitDate,
            lastVisitDate = lastVisitDate,
            yearsVisited = yearsVisited,
            currentYear = period(visits = currentYearVisits),
            allTime = period(visits = allTimeVisits),
        )

        fun facts(vararg guests: LiveGuestStats, remainingNights: Int = 3, allTimeVisits: Int = 100) =
            calculateGuestFunFacts(guests.toList(), today, remainingNights, allTimeVisits, Random(1))

        context("calculateGuestFunFacts") {
            should("highlight first visit with top priority for guests only") {
                val result = facts(guest("sara", isFirstVisit = true, allTimeVisits = 1))

                result.first().guestId shouldBe "sara"
                result.first().priority shouldBe 100
                result.first().text shouldContain "Sara"
                facts(guest("ismar", isFamily = true, isFirstVisit = true, allTimeVisits = 1))
                    .none { it.priority == 100 } shouldBe true
            }

            should("use different visit milestones for guests and family") {
                facts(guest("sara", allTimeVisits = 10)) shouldContain
                    FunFact("sara", "Dette er besøk nr. 10 for Sara!", 95)
                facts(guest("ismar", isFamily = true, allTimeVisits = 10)).map { it.priority } shouldNotContain 95
                facts(guest("ismar", isFamily = true, allTimeVisits = 100)).map { it.priority } shouldContain 95
            }

            should("report long absence only for guests") {
                val absent = guest("sara", lastVisitDate = LocalDate(2025, 6, 1))

                facts(absent) shouldContain FunFact("sara", "Det er 16 måneder siden sist, Sara!", 80)
                facts(absent.copy(isFamily = true)).map { it.priority } shouldNotContain 80
            }

            should("report visit streak counted backwards from current year") {
                val streak = guest("sara", yearsVisited = listOf(2026, 2025, 2024, 2022))

                facts(streak) shouldContain FunFact("sara", "Sara har besøkt hytta 3 år på rad", 70)
                facts(streak.copy(yearsVisited = listOf(2025, 2024, 2023))).map { it.priority } shouldNotContain 70
            }

            should("report first visit of the year") {
                facts(guest("sara", currentYearVisits = 1)) shouldContain
                    FunFact("sara", "Årets første besøk for Sara", 55)
            }

            should("report share of trips only above threshold with lower priority for family") {
                val frequent = guest("sara", allTimeVisits = 30)

                facts(frequent) shouldContain
                    FunFact("sara", "Sara har vært med på 30 % av alle hytteturer", 50)
                facts(frequent.copy(isFamily = true)) shouldContain
                    FunFact("sara", "Sara har vært med på 30 % av alle hytteturer", 30)
                facts(guest("sara", allTimeVisits = 3)).map { it.text }.none { "alle hytteturer" in it } shouldBe true
            }

            should("push family facts below guest facts when guests are present") {
                val family = guest("ismar", isFamily = true, allTimeVisits = 100)
                val visitor = guest("sara", allTimeVisits = 10)

                val withGuest = facts(family, visitor)
                withGuest shouldContain FunFact("ismar", "Dette er besøk nr. 100 for Ismar!", 65)
                withGuest.map { it.priority } shouldBe withGuest.map { it.priority }.sortedDescending()
                facts(family) shouldContain FunFact("ismar", "Dette er besøk nr. 100 for Ismar!", 95)
            }

            should("report group facts") {
                val newcomers = arrayOf(
                    guest("sara", isFirstVisit = true, allTimeVisits = 1),
                    guest("isak", isFirstVisit = true, allTimeVisits = 1),
                )

                facts(*newcomers, remainingNights = 3) shouldContain FunFact(null, "2 av 2 er her for første gang", 55)
                facts(*newcomers, remainingNights = 3) shouldContain
                    FunFact(null, "3 netter igjen av oppholdet", 30)
                facts(*newcomers, remainingNights = 1) shouldContain
                    FunFact(null, "Siste dag på hytta, ha en trygg hjemreise!", 60)
            }

            should("return only last day fact for an empty guest list on check-out day") {
                facts(remainingNights = 0).map { it.priority } shouldBe listOf(60)
            }
        }

        context("calculateCabinFunFacts") {
            should("always include totals") {
                calculateCabinFunFacts(dataStart, false, 142, 530, 29) shouldBe listOf(
                    FunFact(null, "Siden sept 2020: 142 besøk, 530 netter og 29 ulike gjester", 25),
                )
            }

            should("add cabin visit milestone only while occupied") {
                calculateCabinFunFacts(dataStart, true, 100, 400, 29).first() shouldBe
                    FunFact(null, "Dette er hyttebesøk nr. 100 siden sept 2020", 85)
                calculateCabinFunFacts(dataStart, false, 100, 400, 29).map { it.priority } shouldBe listOf(25)
                calculateCabinFunFacts(dataStart, true, 101, 400, 29).map { it.priority } shouldBe listOf(25)
            }
        }
    })
