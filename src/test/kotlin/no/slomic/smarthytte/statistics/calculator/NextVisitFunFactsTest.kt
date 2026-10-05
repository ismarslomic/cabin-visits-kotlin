package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.LiveGuestStats
import no.slomic.smarthytte.statistics.model.NextReservationInfo

class NextVisitFunFactsTest :
    ShouldSpec({
        val today = LocalDate(2026, 10, 2)

        context("calculateNextVisitFunFacts") {
            fun nextFacts(vararg guests: LiveGuestStats, daysUntil: Int = 5, isOccupied: Boolean = true) =
                calculateNextVisitFunFacts(
                    NextReservationInfo(today.plus(DatePeriod(days = daysUntil)), today, guests.toList(), daysUntil),
                    isOccupied,
                )

            should("announce arrival today, tomorrow and the day after tomorrow only") {
                nextFacts(daysUntil = 0) shouldContain FunFact(null, "Neste besøk starter i dag", 70)
                nextFacts(daysUntil = 1) shouldContain FunFact(null, "Neste besøk starter i morgen", 70)
                nextFacts(daysUntil = 2) shouldContain FunFact(null, "Neste besøk starter i overmorgen", 70)
                nextFacts(daysUntil = 3).map { it.priority } shouldNotContain 70
            }

            should("count down to the next visit only while occupied and not arriving soon") {
                nextFacts(daysUntil = 5) shouldContain FunFact(null, "Neste besøk om 5 dager", 45)
                nextFacts(daysUntil = 5, isOccupied = false) shouldBe emptyList()
                nextFacts(daysUntil = 1).map { it.text }.none { "dager" in it } shouldBe true
            }

            should("highlight next first-timers but not family") {
                nextFacts(liveGuestFixture("dina", isFirstVisit = true, allTimeVisits = 0)) shouldContain
                    FunFact("dina", "Dina kommer på hytta for første gang! 🎉", 45)
                nextFacts(liveGuestFixture("bjorn", isFamily = true, isFirstVisit = true, allTimeVisits = 0))
                    .none { it.guestId == "bjorn" } shouldBe true
            }

            should("report next visit milestone as visits plus one") {
                nextFacts(liveGuestFixture("dina", allTimeVisits = 9)) shouldContain
                    FunFact("dina", "Neste besøk blir besøk nr. 10 for Dina", 40)
                nextFacts(liveGuestFixture("dina", allTimeVisits = 10)).map { it.priority } shouldNotContain 40
                nextFacts(liveGuestFixture("bjorn", isFamily = true, allTimeVisits = 49)).map {
                    it.priority
                } shouldContain
                    40
            }

            should("sort by priority") {
                val result = nextFacts(liveGuestFixture("dina", allTimeVisits = 9), daysUntil = 1)

                result.map { it.priority } shouldBe listOf(70, 40)
            }
        }
    })
