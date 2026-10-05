package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.MonthlyVisitCount
import no.slomic.smarthytte.statistics.model.YearEvStats
import no.slomic.smarthytte.statistics.model.YearStats

class CabinFunFactsTest :
    ShouldSpec({
        val today = LocalDate(2026, 10, 2)
        val dataStart = LocalDate(2020, 9, 1)

        context("calculateCabinFunFacts") {
            fun cabinFacts(
                isOccupied: Boolean = false,
                visits: Int = 142,
                currentYear: YearStats? = null,
                previousYear: YearStats? = null,
            ) = calculateCabinFunFacts(
                CabinFunFactContext(
                    today = today,
                    dataStartDate = dataStart,
                    isOccupied = isOccupied,
                    totals = CabinTotals(visits = visits, nights = 530, uniqueGuests = 29),
                    currentYear = currentYear,
                    previousYear = previousYear,
                ),
            )

            fun textsOf(facts: List<FunFact>) = facts.map { it.text }

            should("always include totals and years of ownership") {
                cabinFacts() shouldBe listOf(
                    FunFact(null, "Siden sept 2020: 142 besøk, 530 netter og 29 ulike gjester", 25),
                    FunFact(null, "Hytta har vært i familien i 6 år", 15),
                )
            }

            should("add cabin visit milestone only while occupied") {
                cabinFacts(isOccupied = true, visits = 100).first() shouldBe
                    FunFact(null, "Dette er hyttebesøk nr. 100 siden sept 2020", 85)
                cabinFacts(isOccupied = false, visits = 100).map { it.priority } shouldBe listOf(25, 15)
                cabinFacts(isOccupied = true, visits = 101).map { it.priority } shouldBe listOf(25, 15)
            }

            should("compare driving time with the same month last year") {
                val current = yearStatsFixture(
                    months = monthsFixture(2026, Month.OCTOBER, monthDrivingTimeFixture(95, 100, Month.OCTOBER)),
                )
                val previous = yearStatsFixture(
                    year = 2025,
                    months = monthsFixture(2025, Month.OCTOBER, monthDrivingTimeFixture(115, 102, Month.OCTOBER, 2025)),
                )

                val result = cabinFacts(currentYear = current, previousYear = previous)

                result shouldContain
                    FunFact(null, "Turen til hytta er 20 min kortere enn i oktober i fjor", 60)
                result.none { "Turen fra hytta" in it.text } shouldBe true
            }

            should("fall back to year driving time when the month has no data and report longer trips") {
                val current = yearStatsFixture(drivingTime = yearDrivingTimeFixture(130, 100))
                val previous = yearStatsFixture(year = 2025, drivingTime = yearDrivingTimeFixture(120, 100, 2025))

                val result = cabinFacts(currentYear = current, previousYear = previous)

                textsOf(result) shouldContain "Turen til hytta er 10 min lengre enn i fjor"
                result.none { "Turen fra hytta" in it.text } shouldBe true
                textsOf(cabinFacts(currentYear = current)).none { "enn i fjor" in it } shouldBe true
            }

            should("report year statistics facts") {
                val year = yearStatsFixture(
                    avgGroupSize = 3.46,
                    monthMostVisits = MonthlyVisitCount(7, "July", 4),
                    maxNights = 9,
                    dayOccupancy = 24.6,
                    newGuests = 3,
                    drivingDistance = distanceFixture(8_000.0, 7_000.0),
                    ev = YearEvStats(100.0, 18.46, 5.0),
                    drivingTime = yearDrivingTimeFixture(125, 130),
                )

                val texts = textsOf(cabinFacts(currentYear = year))

                texts shouldContain "3 nye gjester på hytta i år"
                texts shouldContain "Hytta har vært i bruk 25 % av årets dager"
                texts shouldContain "Mest besøkte måned i år: juli (4 besøk)"
                texts shouldContain "Årets lengste opphold: 9 netter"
                texts shouldContain "I snitt 3,5 personer per besøk i år"
                texts shouldContain "Årets hytteturer: 15 000 km, 0,4 ganger rundt jorda"
                texts shouldContain "Strømforbruk på hyttetur i år: 18,5 kWh per 100 km"
                texts shouldContain "Snitt kjøretid i år: 2 t 5 min til hytta og 2 t 10 min hjem"
            }

            should("skip year statistics facts without data or below thresholds") {
                val year = yearStatsFixture(
                    monthMostVisits = MonthlyVisitCount(7, "July", 1),
                    drivingDistance = distanceFixture(1_000.0, 1_000.0),
                )

                val texts = textsOf(cabinFacts(currentYear = year))

                texts.none { "ny gjest" in it || "nye gjester" in it } shouldBe true
                texts.none { "i bruk" in it || "Mest besøkte" in it || "rundt jorda" in it } shouldBe true
                texts.none { "lengste opphold" in it || "personer per besøk" in it || "Strømforbruk" in it } shouldBe
                    true
            }

            should("report this month compared to the same month last year") {
                val current = yearStatsFixture(
                    months = monthsFixture(2026, Month.OCTOBER, comparedToSameMonthLastYear = -2),
                )

                textsOf(cabinFacts(currentYear = current)) shouldContain
                    "Så langt i oktober: 2 færre besøk enn i fjor"
                textsOf(
                    cabinFacts(
                        currentYear = yearStatsFixture(
                            months = monthsFixture(2026, Month.OCTOBER, comparedToSameMonthLastYear = 1),
                        ),
                    ),
                ) shouldContain "Så langt i oktober: 1 flere besøk enn i fjor"
                textsOf(
                    cabinFacts(currentYear = yearStatsFixture(months = monthsFixture(2026, Month.OCTOBER))),
                ).none { "Så langt" in it } shouldBe true
            }

            should("sort cabin facts by priority") {
                val current = yearStatsFixture(
                    maxNights = 4,
                    drivingTime = yearDrivingTimeFixture(100, 100),
                )
                val previous = yearStatsFixture(year = 2025, drivingTime = yearDrivingTimeFixture(130, 130, 2025))

                val priorities = cabinFacts(currentYear = current, previousYear = previous).map { it.priority }

                priorities shouldBe priorities.sortedDescending()
                priorities.first() shouldBe 60
            }
        }
    })
