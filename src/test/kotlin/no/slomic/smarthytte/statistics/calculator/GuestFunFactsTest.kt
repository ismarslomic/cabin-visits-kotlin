package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.LiveGuestStats

class GuestFunFactsTest :
    ShouldSpec({
        val today = LocalDate(2026, 10, 2)
        val dataStart = LocalDate(2020, 9, 1)

        fun standing(id: String, isFamily: Boolean = false, yearVisits: Int = 0, allTimeVisits: Int = 0) =
            GuestStanding(id, id.replaceFirstChar { it.uppercase() }, isFamily, yearVisits, allTimeVisits)

        fun facts(
            vararg guests: LiveGuestStats,
            remainingNights: Int = 3,
            allTimeVisits: Int = 100,
            stayStart: LocalDate = today,
            standings: List<GuestStanding> = emptyList(),
            toCabinDriving: ToCabinDriving? = null,
        ) = calculateGuestFunFacts(
            guests.toList(),
            GuestFunFactContext(
                today = today,
                stay = StayInfo(stayStart, remainingNights),
                allTimeVisits = allTimeVisits,
                dataStartDate = dataStart,
                standings = standings,
                toCabinDriving = toCabinDriving,
            ),
        )

        context("calculateGuestFunFacts") {
            should("highlight first visit with top priority for guests only") {
                val result = facts(liveGuestFixture("dina", isFirstVisit = true, allTimeVisits = 1))

                result.first().guestId shouldBe "dina"
                result.first().priority shouldBe 100
                result.first().text shouldBe "Første registrerte besøk for Dina! 🎉"
                facts(liveGuestFixture("bjorn", isFamily = true, isFirstVisit = true, allTimeVisits = 1))
                    .none { it.priority == 100 } shouldBe true
            }

            should("use different visit milestones for guests and family") {
                facts(liveGuestFixture("dina", allTimeVisits = 10)) shouldContain
                    FunFact("dina", "Dette er besøk nr. 10 for Dina!", 95)
                facts(liveGuestFixture("bjorn", isFamily = true, allTimeVisits = 10)).map {
                    it.priority
                } shouldNotContain
                    95
                facts(liveGuestFixture("bjorn", isFamily = true, allTimeVisits = 100)).map { it.priority } shouldContain
                    95
            }

            should("report long absence only for guests") {
                val absent = liveGuestFixture("dina", lastVisitDate = LocalDate(2025, 6, 1))

                facts(absent) shouldContain FunFact("dina", "Det er 16 måneder siden sist, Dina!", 80)
                facts(absent.copy(isFamily = true)).map { it.priority } shouldNotContain 80
            }

            should("report visit streak counted backwards from current year") {
                val streak = liveGuestFixture("dina", yearsVisited = listOf(2026, 2025, 2024, 2022))

                facts(streak) shouldContain FunFact("dina", "Dina har besøkt hytta 3 år på rad", 70)
                facts(streak.copy(yearsVisited = listOf(2025, 2024, 2023))).map { it.priority } shouldNotContain 70
            }

            should("report first visit of the year") {
                facts(liveGuestFixture("dina", currentYearVisits = 1)) shouldContain
                    FunFact("dina", "Årets første besøk for Dina", 55)
            }

            should("report share of trips only above threshold with lower priority for family") {
                val frequent = liveGuestFixture("dina", allTimeVisits = 30)

                facts(frequent) shouldContain
                    FunFact("dina", "Dina har vært med på 30 % av alle hytteturer", 50)
                facts(frequent.copy(isFamily = true)) shouldContain
                    FunFact("dina", "Dina har vært med på 30 % av alle hytteturer", 30)
                facts(liveGuestFixture("dina", allTimeVisits = 3)).map {
                    it.text
                }.none { "alle hytteturer" in it } shouldBe
                    true
            }

            should("push family facts below guest facts when guests are present") {
                val family = liveGuestFixture("bjorn", isFamily = true, allTimeVisits = 100)
                val visitor = liveGuestFixture("dina", allTimeVisits = 10)

                val withGuest = facts(family, visitor)
                withGuest shouldContain FunFact("bjorn", "Dette er besøk nr. 100 for Bjorn!", 65)
                withGuest.map { it.priority } shouldBe withGuest.map { it.priority }.sortedDescending()
                facts(family) shouldContain FunFact("bjorn", "Dette er besøk nr. 100 for Bjorn!", 95)
            }

            should("report group facts") {
                val newcomers = arrayOf(
                    liveGuestFixture("dina", isFirstVisit = true, allTimeVisits = 1),
                    liveGuestFixture("anders", isFirstVisit = true, allTimeVisits = 1),
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

        context("calculateGuestFunFacts, days, race and group facts") {
            should("report days milestone only when crossed during the stay") {
                val crossing = liveGuestFixture("dina", allTimeDays = 102)

                facts(crossing, stayStart = today.minus(DatePeriod(days = 2))) shouldContain
                    FunFact("dina", "Dina passerer 100 dager på hytta! 🎉", 85)
                facts(crossing, stayStart = today).map { it.priority } shouldNotContain 85
                facts(liveGuestFixture("dina", allTimeDays = 101), stayStart = today).map {
                    it.priority
                } shouldNotContain
                    85
            }

            should("report share of days since data start above threshold") {
                // 2020-09-01..2026-10-02 is 2224 days, 5 % is 111
                facts(liveGuestFixture("dina", allTimeDays = 222)) shouldContain
                    FunFact("dina", "Dina har vært på hytta 9 % av dagene siden sept 2020", 45)
                facts(liveGuestFixture("dina", allTimeDays = 50)).none { "av dagene" in it.text } shouldBe true
            }

            should("report share of life only for children born in the data period") {
                facts(liveGuestFixture("kid", age = 5, allTimeDays = 360)) shouldContain
                    FunFact("kid", "Kid har vært på hytta ca. 19 % av livet sitt", 45)
                facts(liveGuestFixture("adult", age = 30, allTimeDays = 360)).none { "av livet" in it.text } shouldBe
                    true
                facts(liveGuestFixture("kid", age = 7, allTimeDays = 360)).none { "av livet" in it.text } shouldBe true
            }

            should("report days this year with percent of the year so far") {
                facts(liveGuestFixture("dina", currentYearDays = 55)) shouldContain
                    FunFact("dina", "Dina har vært 55 dager på hytta i år (20 % av året så langt)", 40)
                facts(liveGuestFixture("dina", currentYearDays = 2)).none { "i år" in it.text } shouldBe true
            }

            should("report total days and last visit date") {
                val result =
                    facts(
                        liveGuestFixture(
                            "dina",
                            firstVisitDate = null,
                            allTimeDays = 12,
                            lastVisitDate = LocalDate(2026, 8, 3),
                        ),
                    )

                result shouldContain FunFact("dina", "Dina har tilbrakt 12 dager på hytta siden sept 2020", 30)
                result shouldContain FunFact("dina", "Dina var sist her i aug 2026", 30)
            }

            should("report most experienced non-family guest in a group of at least two") {
                val experienced = liveGuestFixture("dina", allTimeVisits = 12)
                val other = liveGuestFixture("anders", allTimeVisits = 3)

                facts(experienced, other) shouldContain
                    FunFact("dina", "Dina er mest erfaren i gjengen med 12 besøk", 40)
                facts(experienced).none { "mest erfaren" in it.text } shouldBe true
                facts(experienced, liveGuestFixture("anders", allTimeVisits = 12)).none {
                    "mest erfaren" in it.text
                } shouldBe
                    true
            }

            should("report stay progress as a group fact") {
                facts(
                    liveGuestFixture("dina"),
                    stayStart = today.minus(DatePeriod(days = 2)),
                    remainingNights = 4,
                ) shouldContain
                    FunFact(null, "Dag 3 av 7 på hytta", 30)
            }

            should("report year leader, shared lead and gap to the leader among guests") {
                val standings = listOf(
                    standing("dina", yearVisits = 4),
                    standing("anders", yearVisits = 4),
                    standing("cecilie", yearVisits = 2),
                    standing("bjorn", isFamily = true, yearVisits = 9),
                )

                facts(liveGuestFixture("dina"), standings = standings) shouldContain
                    FunFact("dina", "Dina deler ledelsen blant gjestene i år med Anders (4 besøk)", 70)
                facts(liveGuestFixture("cecilie"), standings = standings) shouldContain
                    FunFact("cecilie", "Cecilie ligger 2 besøk bak Anders blant gjestene i år", 60)
                facts(liveGuestFixture("dina"), standings = standings.filter { it.guestId != "anders" }) shouldContain
                    FunFact("dina", "Dina leder blant gjestene i år med 4 besøk", 65)
                facts(liveGuestFixture("bjorn", isFamily = true), standings = standings).map { it.text } shouldContain
                    "Bjorn leder i familien i år med 9 besøk"
            }

            should("skip the race below two visits for the leader") {
                val standings = listOf(standing("dina", yearVisits = 1), standing("anders", yearVisits = 1))

                facts(liveGuestFixture("dina"), standings = standings).none { "i år" in it.text } shouldBe true
            }

            should("report all-time top guest excluding family and requires a single leader") {
                val standings = listOf(
                    standing("dina", allTimeVisits = 20),
                    standing("anders", allTimeVisits = 10),
                    standing("bjorn", isFamily = true, allTimeVisits = 80),
                )

                facts(liveGuestFixture("dina"), standings = standings) shouldContain
                    FunFact("dina", "Dina er gjesten med flest besøk siden sept 2020 (20)", 70)
                facts(liveGuestFixture("anders"), standings = standings).none { "flest besøk" in it.text } shouldBe true
                facts(liveGuestFixture("bjorn", isFamily = true), standings = standings)
                    .none { "flest besøk" in it.text } shouldBe true
                facts(liveGuestFixture("dina"), standings = standings.map { it.copy(allTimeVisits = 20) })
                    .none { "flest besøk" in it.text } shouldBe true
            }

            should("report a driving record only when faster than enough earlier trips") {
                val earlier = listOf(95, 100, 110, 120, 130)

                facts(liveGuestFixture("dina"), toCabinDriving = ToCabinDriving(78, earlier)) shouldContain
                    FunFact(null, "Ny rekord! Raskeste tur til hytta: 1 t 18 min", 85)
                facts(liveGuestFixture("dina"), toCabinDriving = ToCabinDriving(50, earlier)) shouldContain
                    FunFact(null, "Ny rekord! Raskeste tur til hytta: 50 min", 85)
                facts(liveGuestFixture("dina"), toCabinDriving = ToCabinDriving(96, earlier)).map {
                    it.priority
                } shouldNotContain
                    85
                facts(liveGuestFixture("dina"), toCabinDriving = ToCabinDriving(78, earlier.take(2)))
                    .map { it.priority } shouldNotContain 85
                facts(liveGuestFixture("dina"), toCabinDriving = ToCabinDriving(null, earlier)).map {
                    it.priority
                } shouldNotContain
                    85
            }

            should("keep at most two facts per guest") {
                val busy =
                    liveGuestFixture(
                        "dina",
                        isFirstVisit = true,
                        allTimeVisits = 10,
                        allTimeDays = 300,
                        currentYearDays = 10,
                    )

                facts(busy).count { it.guestId == "dina" } shouldBe 2
                facts(busy).filter { it.guestId == "dina" }.map { it.priority } shouldBe listOf(100, 95)
            }
        }
    })
