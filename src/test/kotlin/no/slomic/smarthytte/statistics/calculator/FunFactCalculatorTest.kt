package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import no.slomic.smarthytte.guests.Gender
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.reservations.Reservation
import no.slomic.smarthytte.statistics.model.FunFact
import no.slomic.smarthytte.statistics.model.GuestPeriodStats
import no.slomic.smarthytte.statistics.model.LiveGuestStats
import no.slomic.smarthytte.statistics.model.MonthlyVisitCount
import no.slomic.smarthytte.statistics.model.NextReservationInfo
import no.slomic.smarthytte.statistics.model.YearEvStats
import no.slomic.smarthytte.statistics.model.YearStats

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
            currentYearDays: Int = 0,
            allTimeDays: Int = 0,
            age: Int = 30,
        ) = LiveGuestStats(
            guestId = id,
            firstName = id.replaceFirstChar { it.uppercase() },
            lastName = "Test",
            age = age,
            isFamily = isFamily,
            avatarUrl = null,
            isFirstVisit = isFirstVisit,
            firstVisitDate = firstVisitDate,
            lastVisitDate = lastVisitDate,
            yearsVisited = yearsVisited,
            currentYear = period(visits = currentYearVisits, days = currentYearDays),
            allTime = period(visits = allTimeVisits, days = allTimeDays),
        )

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
                val result = facts(guest("dina", isFirstVisit = true, allTimeVisits = 1))

                result.first().guestId shouldBe "dina"
                result.first().priority shouldBe 100
                result.first().text shouldBe "Første registrerte besøk for Dina! 🎉"
                facts(guest("bjorn", isFamily = true, isFirstVisit = true, allTimeVisits = 1))
                    .none { it.priority == 100 } shouldBe true
            }

            should("use different visit milestones for guests and family") {
                facts(guest("dina", allTimeVisits = 10)) shouldContain
                    FunFact("dina", "Dette er besøk nr. 10 for Dina!", 95)
                facts(guest("bjorn", isFamily = true, allTimeVisits = 10)).map { it.priority } shouldNotContain 95
                facts(guest("bjorn", isFamily = true, allTimeVisits = 100)).map { it.priority } shouldContain 95
            }

            should("report long absence only for guests") {
                val absent = guest("dina", lastVisitDate = LocalDate(2025, 6, 1))

                facts(absent) shouldContain FunFact("dina", "Det er 16 måneder siden sist, Dina!", 80)
                facts(absent.copy(isFamily = true)).map { it.priority } shouldNotContain 80
            }

            should("report visit streak counted backwards from current year") {
                val streak = guest("dina", yearsVisited = listOf(2026, 2025, 2024, 2022))

                facts(streak) shouldContain FunFact("dina", "Dina har besøkt hytta 3 år på rad", 70)
                facts(streak.copy(yearsVisited = listOf(2025, 2024, 2023))).map { it.priority } shouldNotContain 70
            }

            should("report first visit of the year") {
                facts(guest("dina", currentYearVisits = 1)) shouldContain
                    FunFact("dina", "Årets første besøk for Dina", 55)
            }

            should("report share of trips only above threshold with lower priority for family") {
                val frequent = guest("dina", allTimeVisits = 30)

                facts(frequent) shouldContain
                    FunFact("dina", "Dina har vært med på 30 % av alle hytteturer", 50)
                facts(frequent.copy(isFamily = true)) shouldContain
                    FunFact("dina", "Dina har vært med på 30 % av alle hytteturer", 30)
                facts(guest("dina", allTimeVisits = 3)).map { it.text }.none { "alle hytteturer" in it } shouldBe true
            }

            should("push family facts below guest facts when guests are present") {
                val family = guest("bjorn", isFamily = true, allTimeVisits = 100)
                val visitor = guest("dina", allTimeVisits = 10)

                val withGuest = facts(family, visitor)
                withGuest shouldContain FunFact("bjorn", "Dette er besøk nr. 100 for Bjorn!", 65)
                withGuest.map { it.priority } shouldBe withGuest.map { it.priority }.sortedDescending()
                facts(family) shouldContain FunFact("bjorn", "Dette er besøk nr. 100 for Bjorn!", 95)
            }

            should("report group facts") {
                val newcomers = arrayOf(
                    guest("dina", isFirstVisit = true, allTimeVisits = 1),
                    guest("anders", isFirstVisit = true, allTimeVisits = 1),
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
                val crossing = guest("dina", allTimeDays = 102)

                facts(crossing, stayStart = today.minus(DatePeriod(days = 2))) shouldContain
                    FunFact("dina", "Dina passerer 100 dager på hytta! 🎉", 85)
                facts(crossing, stayStart = today).map { it.priority } shouldNotContain 85
                facts(guest("dina", allTimeDays = 101), stayStart = today).map { it.priority } shouldNotContain 85
            }

            should("report share of days since data start above threshold") {
                // 2020-09-01..2026-10-02 is 2224 days, 5 % is 111
                facts(guest("dina", allTimeDays = 222)) shouldContain
                    FunFact("dina", "Dina har vært på hytta 9 % av dagene siden sept 2020", 45)
                facts(guest("dina", allTimeDays = 50)).none { "av dagene" in it.text } shouldBe true
            }

            should("report share of life only for children born in the data period") {
                facts(guest("kid", age = 5, allTimeDays = 360)) shouldContain
                    FunFact("kid", "Kid har vært på hytta ca. 19 % av livet sitt", 45)
                facts(guest("adult", age = 30, allTimeDays = 360)).none { "av livet" in it.text } shouldBe true
                facts(guest("kid", age = 7, allTimeDays = 360)).none { "av livet" in it.text } shouldBe true
            }

            should("report days this year with percent of the year so far") {
                facts(guest("dina", currentYearDays = 55)) shouldContain
                    FunFact("dina", "Dina har vært 55 dager på hytta i år (20 % av året så langt)", 40)
                facts(guest("dina", currentYearDays = 2)).none { "i år" in it.text } shouldBe true
            }

            should("report total days and last visit date") {
                val result =
                    facts(guest("dina", firstVisitDate = null, allTimeDays = 12, lastVisitDate = LocalDate(2026, 8, 3)))

                result shouldContain FunFact("dina", "Dina har tilbrakt 12 dager på hytta siden sept 2020", 30)
                result shouldContain FunFact("dina", "Dina var sist her i aug 2026", 30)
            }

            should("report most experienced non-family guest in a group of at least two") {
                val experienced = guest("dina", allTimeVisits = 12)
                val other = guest("anders", allTimeVisits = 3)

                facts(experienced, other) shouldContain
                    FunFact("dina", "Dina er mest erfaren i gjengen med 12 besøk", 40)
                facts(experienced).none { "mest erfaren" in it.text } shouldBe true
                facts(experienced, guest("anders", allTimeVisits = 12)).none { "mest erfaren" in it.text } shouldBe true
            }

            should("report stay progress as a group fact") {
                facts(guest("dina"), stayStart = today.minus(DatePeriod(days = 2)), remainingNights = 4) shouldContain
                    FunFact(null, "Dag 3 av 7 på hytta", 30)
            }

            should("report year leader, shared lead and gap to the leader among guests") {
                val standings = listOf(
                    standing("dina", yearVisits = 4),
                    standing("anders", yearVisits = 4),
                    standing("cecilie", yearVisits = 2),
                    standing("bjorn", isFamily = true, yearVisits = 9),
                )

                facts(guest("dina"), standings = standings) shouldContain
                    FunFact("dina", "Dina deler ledelsen blant gjestene i år med Anders (4 besøk)", 70)
                facts(guest("cecilie"), standings = standings) shouldContain
                    FunFact("cecilie", "Cecilie ligger 2 besøk bak Anders blant gjestene i år", 60)
                facts(guest("dina"), standings = standings.filter { it.guestId != "anders" }) shouldContain
                    FunFact("dina", "Dina leder blant gjestene i år med 4 besøk", 65)
                facts(guest("bjorn", isFamily = true), standings = standings).map { it.text } shouldContain
                    "Bjorn leder i familien i år med 9 besøk"
            }

            should("skip the race below two visits for the leader") {
                val standings = listOf(standing("dina", yearVisits = 1), standing("anders", yearVisits = 1))

                facts(guest("dina"), standings = standings).none { "i år" in it.text } shouldBe true
            }

            should("report all-time top guest excluding family and requires a single leader") {
                val standings = listOf(
                    standing("dina", allTimeVisits = 20),
                    standing("anders", allTimeVisits = 10),
                    standing("bjorn", isFamily = true, allTimeVisits = 80),
                )

                facts(guest("dina"), standings = standings) shouldContain
                    FunFact("dina", "Dina er gjesten med flest besøk siden sept 2020 (20)", 70)
                facts(guest("anders"), standings = standings).none { "flest besøk" in it.text } shouldBe true
                facts(guest("bjorn", isFamily = true), standings = standings)
                    .none { "flest besøk" in it.text } shouldBe true
                facts(guest("dina"), standings = standings.map { it.copy(allTimeVisits = 20) })
                    .none { "flest besøk" in it.text } shouldBe true
            }

            should("report a driving record only when faster than enough earlier trips") {
                val earlier = listOf(95, 100, 110, 120, 130)

                facts(guest("dina"), toCabinDriving = ToCabinDriving(78, earlier)) shouldContain
                    FunFact(null, "Ny rekord! Raskeste tur til hytta: 1 t 18 min", 85)
                facts(guest("dina"), toCabinDriving = ToCabinDriving(50, earlier)) shouldContain
                    FunFact(null, "Ny rekord! Raskeste tur til hytta: 50 min", 85)
                facts(guest("dina"), toCabinDriving = ToCabinDriving(96, earlier)).map { it.priority } shouldNotContain
                    85
                facts(guest("dina"), toCabinDriving = ToCabinDriving(78, earlier.take(2)))
                    .map { it.priority } shouldNotContain 85
                facts(guest("dina"), toCabinDriving = ToCabinDriving(null, earlier)).map {
                    it.priority
                } shouldNotContain
                    85
            }

            should("keep at most two facts per guest") {
                val busy =
                    guest("dina", isFirstVisit = true, allTimeVisits = 10, allTimeDays = 300, currentYearDays = 10)

                facts(busy).count { it.guestId == "dina" } shouldBe 2
                facts(busy).filter { it.guestId == "dina" }.map { it.priority } shouldBe listOf(100, 95)
            }
        }

        context("calculateGuestStandings") {
            should("count visits this year and all time and mark family") {
                val dina = Guest("dina", "Dina", "T", 1990, gender = Gender.FEMALE, isFamily = true)
                val anders = Guest("anders", "Anders", "T", 2010, gender = Gender.MALE)
                val now = LocalDate(2026, 6, 15)

                fun reservation(id: String, start: LocalDate, vararg guestIds: String) = Reservation(
                    id = id,
                    startTime = start.atStartOfDayIn(TimeZone.UTC),
                    endTime = start.plus(DatePeriod(days = 2)).atStartOfDayIn(TimeZone.UTC),
                    guestIds = guestIds.toList(),
                )

                val standings = calculateGuestStandings(
                    today = now,
                    allReservations = listOf(
                        reservation("r1", LocalDate(2025, 5, 1), "dina", "anders"),
                        reservation("r2", LocalDate(2026, 3, 1), "dina"),
                        reservation("r3", LocalDate(2026, 9, 1), "dina", "anders"),
                    ),
                    guestsById = mapOf("dina" to dina, "anders" to anders),
                ).sortedBy { it.guestId }

                standings shouldBe listOf(
                    GuestStanding("anders", "Anders", false, yearVisits = 0, allTimeVisits = 1),
                    GuestStanding("dina", "Dina", true, yearVisits = 1, allTimeVisits = 2),
                )
            }
        }

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
                nextFacts(guest("dina", isFirstVisit = true, allTimeVisits = 0)) shouldContain
                    FunFact("dina", "Dina kommer på hytta for første gang! 🎉", 45)
                nextFacts(guest("bjorn", isFamily = true, isFirstVisit = true, allTimeVisits = 0))
                    .none { it.guestId == "bjorn" } shouldBe true
            }

            should("report next visit milestone as visits plus one") {
                nextFacts(guest("dina", allTimeVisits = 9)) shouldContain
                    FunFact("dina", "Neste besøk blir besøk nr. 10 for Dina", 40)
                nextFacts(guest("dina", allTimeVisits = 10)).map { it.priority } shouldNotContain 40
                nextFacts(guest("bjorn", isFamily = true, allTimeVisits = 49)).map { it.priority } shouldContain 40
            }

            should("sort by priority") {
                val result = nextFacts(guest("dina", allTimeVisits = 9), daysUntil = 1)

                result.map { it.priority } shouldBe listOf(70, 40)
            }
        }
    })
