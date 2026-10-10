package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import no.slomic.smarthytte.statistics.model.GuestStats
import no.slomic.smarthytte.statistics.model.GuestVisit
import no.slomic.smarthytte.statistics.model.VisitStatus

class GuestProfileFunFactsTest :
    ShouldSpec({
        val today = LocalDate(2026, 10, 2)

        fun visit(start: LocalDate, status: VisitStatus = VisitStatus.COMPLETED, days: Int = 4) =
            GuestVisit(start, start.plus(DatePeriod(days = days - 1)), days, nights = days - 1, status = status)

        fun visitIn(year: Int, month: Int = 9, status: VisitStatus = VisitStatus.COMPLETED, days: Int = 4) =
            visit(LocalDate(year, month, 10), status, days)

        fun facts(guest: GuestStats, vararg visits: GuestVisit) =
            calculateGuestProfileFunFacts(guest, visits.toList(), GuestProfileFunFactContext(today))

        fun texts(guest: GuestStats, vararg visits: GuestVisit) = facts(guest, *visits).map { it.text }

        // Visits in consecutive years also give a loyal guest fact, so tests of one theme look at that theme only
        fun textsAbout(theme: String, guest: GuestStats, vararg visits: GuestVisit) =
            texts(guest, *visits).filter { theme in it }

        context("share of life") {
            should("tell how much of the life the guest has spent at the cabin") {
                val guest = liveGuestFixture("isak", isFamily = true, age = 10, allTimeDays = 365)

                textsAbout("av livet", guest) shouldBe listOf("Isak har vært på hytta ca. 10 % av livet sitt")
                facts(guest).all { it.guestId == "isak" } shouldBe true
            }

            should("skip it below the minimum share") {
                // 146 / 3650 = 4 %, 183 / 3650 = 5 %
                textsAbout("av livet", liveGuestFixture("ola", age = 10, allTimeDays = 146)).shouldBeEmpty()
                textsAbout("av livet", liveGuestFixture("ola", age = 10, allTimeDays = 183)) shouldBe
                    listOf("Ola har vært på hytta ca. 5 % av livet sitt")
            }

            should("tell it for older children too, since the cabin was bought before the data starts") {
                textsAbout("av livet", liveGuestFixture("sara", isFamily = true, age = 14, allTimeDays = 472)) shouldBe
                    listOf("Sara har vært på hytta ca. 9 % av livet sitt")
            }

            should("skip it for guests under one year") {
                textsAbout("av livet", liveGuestFixture("baby", age = 0, allTimeDays = 3)).shouldBeEmpty()
            }

            should("cap the share at 100 percent") {
                textsAbout("av livet", liveGuestFixture("ola", age = 1, allTimeDays = 1000)) shouldBe
                    listOf("Ola har vært på hytta ca. 100 % av livet sitt")
            }
        }

        context("loyal guest") {
            val guest = liveGuestFixture("jorgen")

            should("count the years in a row up to last year when the guest is not at the cabin") {
                val visits = (2020..2025).map { visitIn(it) } + visitIn(2026, 11, VisitStatus.UPCOMING)

                textsAbout("trofast", guest, *visits.toTypedArray()) shouldBe
                    listOf("Jorgen er en trofast gjest og har besøkt hytta de siste 6 årene")
            }

            should("include the ongoing visit") {
                val visits = (2020..2025).map { visitIn(it) } + visitIn(2026, 9, VisitStatus.ONGOING)

                textsAbout("trofast", guest, *visits.toTypedArray()) shouldBe
                    listOf("Jorgen er en trofast gjest og har besøkt hytta de siste 7 årene")
            }

            should("only count the years in a row ending at the last visited year") {
                val visits = listOf(visitIn(2020), visitIn(2022), visitIn(2024), visitIn(2025), visitIn(2026))

                textsAbout("trofast", guest, *visits.toTypedArray()) shouldBe
                    listOf("Jorgen er en trofast gjest og har besøkt hytta de siste 3 årene")
            }

            should("skip it when the streak ended before last year") {
                val visits = (2020..2024).map { visitIn(it) }

                textsAbout("trofast", guest, *visits.toTypedArray()).shouldBeEmpty()
            }

            should("skip it for fewer than three years in a row") {
                val visits = listOf(visitIn(2025), visitIn(2026))

                textsAbout("trofast", guest, *visits.toTypedArray()).shouldBeEmpty()
            }

            should("skip it for family") {
                val visits = (2020..2026).map { visitIn(it) }

                textsAbout(
                    "trofast",
                    liveGuestFixture("ismar", isFamily = true),
                    *visits.toTypedArray(),
                ).shouldBeEmpty()
            }
        }

        context("favourite season") {
            val guest = liveGuestFixture("jorgen")

            should("tell the season with most visits") {
                val visits = arrayOf(visitIn(2023, 9), visitIn(2024, 10), visitIn(2025, 10), visitIn(2025, 3))

                textsAbout("hyttelivet", guest, *visits) shouldBe
                    listOf("Jorgen liker hyttelivet mest om høsten (3 av 4 besøk)")
            }

            should("use meteorological seasons") {
                fun season(month: Int) = textsAbout(
                    "hyttelivet",
                    guest,
                    visitIn(2024, month),
                    visitIn(2025, month),
                    visitIn(2026, month),
                ).single()

                season(12) shouldBe "Jorgen liker hyttelivet mest om vinteren (3 av 3 besøk)"
                season(2) shouldBe "Jorgen liker hyttelivet mest om vinteren (3 av 3 besøk)"
                season(3) shouldBe "Jorgen liker hyttelivet mest om våren (3 av 3 besøk)"
                season(8) shouldBe "Jorgen liker hyttelivet mest om sommeren (3 av 3 besøk)"
                season(11) shouldBe "Jorgen liker hyttelivet mest om høsten (3 av 3 besøk)"
            }

            should("skip it when two seasons are tied") {
                val visits = arrayOf(visitIn(2023, 9), visitIn(2024, 10), visitIn(2025, 12), visitIn(2025, 1))

                textsAbout("hyttelivet", guest, *visits).shouldBeEmpty()
            }

            should("skip it for fewer than three visits") {
                textsAbout("hyttelivet", guest, visitIn(2024, 9), visitIn(2025, 9)).shouldBeEmpty()
            }

            should("not count upcoming visits") {
                val visits = arrayOf(visitIn(2024, 9), visitIn(2025, 9), visitIn(2026, 11, VisitStatus.UPCOMING))

                textsAbout("hyttelivet", guest, *visits).shouldBeEmpty()
            }
        }

        context("visit milestone") {
            val dzevad = liveGuestFixture("dzevad", allTimeVisits = 10)

            should("say which visit it is while the guest is at the cabin") {
                val result = facts(dzevad, visitIn(2026, 10, VisitStatus.ONGOING))

                result.first().text shouldBe "Dette er besøk nr. 10 for Dzevad!"
                result.first().priority shouldBe 95
            }

            should("count the visits so far when the guest is not at the cabin") {
                textsAbout("besøkt hytta 10", dzevad, visitIn(2026, 8)) shouldBe
                    listOf("Dzevad har besøkt hytta 10 ganger!")
            }

            should("skip it when the number of visits is not a milestone") {
                textsAbout("ganger!", liveGuestFixture("dzevad", allTimeVisits = 9), visitIn(2026, 8)).shouldBeEmpty()
                textsAbout("ganger!", liveGuestFixture("ismar", isFamily = true, allTimeVisits = 10), visitIn(2026, 8))
                    .shouldBeEmpty()
            }
        }

        context("visits to next milestone") {
            should("tell how many visits are left to the next milestone") {
                textsAbout("unna", liveGuestFixture("dzevad", allTimeVisits = 8)) shouldBe
                    listOf("Dzevad er 2 besøk unna å nå 10 besøk på hytta")
                textsAbout("unna", liveGuestFixture("dzevad", allTimeVisits = 9)) shouldBe
                    listOf("Dzevad er 1 besøk unna å nå 10 besøk på hytta")
            }

            should("skip it when the milestone is more than three visits away, for family and above the last one") {
                textsAbout("unna", liveGuestFixture("dzevad", allTimeVisits = 6)).shouldBeEmpty()
                textsAbout("unna", liveGuestFixture("ismar", isFamily = true, allTimeVisits = 48)).shouldBeEmpty()
                textsAbout("unna", liveGuestFixture("dzevad", allTimeVisits = 201)).shouldBeEmpty()
            }
        }

        context("upcoming visit") {
            val dzevad = liveGuestFixture("dzevad", allTimeVisits = 6)

            should("tell when the next visit starts") {
                val inTwelveDays = visit(today.plus(DatePeriod(days = 12)), VisitStatus.UPCOMING)

                val fact = facts(dzevad, inTwelveDays).single { "Neste besøk" in it.text }

                fact.text shouldBe "Neste besøk for Dzevad er om 12 dager"
                fact.priority shouldBe 60
            }

            should("say tomorrow for a visit starting tomorrow") {
                val tomorrow = visit(today.plus(DatePeriod(days = 1)), VisitStatus.UPCOMING)

                textsAbout("Neste besøk", dzevad, tomorrow) shouldBe listOf("Neste besøk for Dzevad er i morgen")
            }

            should("mention the visit number and rank higher when the next visit is a milestone") {
                val upcoming = visit(today.plus(DatePeriod(days = 12)), VisitStatus.UPCOMING)

                val fact = facts(liveGuestFixture("dzevad", allTimeVisits = 9), upcoming)
                    .single { "Neste besøk" in it.text }

                fact.text shouldBe "Neste besøk for Dzevad er om 12 dager, da er det besøk nr. 10!"
                fact.priority shouldBe 75
            }

            should("use the first upcoming visit and skip it without upcoming visits") {
                val first = visit(today.plus(DatePeriod(days = 5)), VisitStatus.UPCOMING)
                val second = visit(today.plus(DatePeriod(days = 40)), VisitStatus.UPCOMING)

                textsAbout("Neste besøk", dzevad, first, second) shouldBe listOf("Neste besøk for Dzevad er om 5 dager")
                textsAbout("Neste besøk", dzevad, visitIn(2026, 8)).shouldBeEmpty()
            }
        }

        context("longest stay") {
            val guest = liveGuestFixture("dzevad")

            should("tell the longest completed or ongoing stay") {
                textsAbout("lengste", guest, visitIn(2023, 9, days = 4), visitIn(2024, 8, days = 11)) shouldBe
                    listOf("Det lengste oppholdet til Dzevad var 11 dager (aug 2024)")
            }

            should("prefer the latest stay when two are equally long") {
                textsAbout("lengste", guest, visitIn(2023, 9, days = 8), visitIn(2025, 3, days = 8)) shouldBe
                    listOf("Det lengste oppholdet til Dzevad var 8 dager (mars 2025)")
            }

            should("skip it below six days and ignore upcoming visits") {
                textsAbout("lengste", guest, visitIn(2024, 8, days = 5)).shouldBeEmpty()
                textsAbout("lengste", guest, visitIn(2024, 8, days = 6)).size shouldBe 1
                textsAbout("lengste", guest, visitIn(2026, 11, VisitStatus.UPCOMING, days = 10)).shouldBeEmpty()
            }
        }

        context("total time in words") {
            fun totalTime(days: Int) = textsAbout("tilbrakt", liveGuestFixture("dzevad", allTimeDays = days))

            should("convert days to weeks, months and years") {
                totalTime(46) shouldBe listOf("Dzevad har tilbrakt 46 dager på hytta, det er ca. 6 uker")
                totalTime(100) shouldBe listOf("Dzevad har tilbrakt 100 dager på hytta, det er ca. 3 måneder")
                totalTime(800) shouldBe listOf("Dzevad har tilbrakt 800 dager på hytta, det er ca. 2,2 år")
            }

            should("skip it below 30 days") {
                totalTime(29).shouldBeEmpty()
                totalTime(30).size shouldBe 1
            }
        }

        context("record year") {
            val guest = liveGuestFixture("dzevad")

            should("tell the year with most visits") {
                val visits = arrayOf(visitIn(2023, 3), visitIn(2023, 6), visitIn(2023, 9), visitIn(2024), visitIn(2025))

                textsAbout("Rekordåret", guest, *visits) shouldBe
                    listOf("Rekordåret til Dzevad var 2023 med 3 besøk")
            }

            should("skip it for a tie, fewer than three years or when the record year is this year") {
                val tie = arrayOf(visitIn(2023, 3), visitIn(2023, 6), visitIn(2024, 3), visitIn(2024, 6), visitIn(2025))
                val twoYears = arrayOf(visitIn(2024, 3), visitIn(2024, 6), visitIn(2025))
                val thisYear = arrayOf(visitIn(2024), visitIn(2025), visitIn(2026, 1), visitIn(2026, 3))

                textsAbout("Rekordåret", guest, *tie).shouldBeEmpty()
                textsAbout("Rekordåret", guest, *twoYears).shouldBeEmpty()
                textsAbout("Rekordåret", guest, *thisYear).shouldBeEmpty()
            }
        }

        context("reused live facts") {
            should("tell years since the first visit, a long absence and days this year") {
                val guest = liveGuestFixture(
                    "faruk",
                    firstVisitDate = LocalDate(2021, 1, 1),
                    lastVisitDate = LocalDate(2025, 6, 1),
                    currentYearDays = 5,
                )

                val result = texts(guest)

                result shouldContain "Faruk hadde sitt første registrerte besøk for 5 år siden"
                result shouldContain "Det er 16 måneder siden sist, Faruk!"
                result.single { "i år" in it } shouldBe "Faruk har vært 5 dager på hytta i år (1 % av året så langt)"
            }

            should("skip them for family") {
                val family = liveGuestFixture(
                    "ismar",
                    isFamily = true,
                    lastVisitDate = LocalDate(2025, 6, 1),
                )

                texts(family).none { "siden sist" in it || "første registrerte" in it } shouldBe true
            }
        }

        context("calculateGuestProfileFunFacts") {
            should("return no facts for a guest without visits") {
                facts(
                    liveGuestFixture(
                        "ola",
                        age = 30,
                        firstVisitDate = null,
                        lastVisitDate = null,
                        currentYearVisits = 0,
                        allTimeVisits = 0,
                    ),
                ).shouldBeEmpty()
            }

            should("sort the facts by priority, highest first") {
                val visits = (2020..2026).map { visitIn(it, 10) }.toTypedArray()

                val result = facts(liveGuestFixture("jorgen", age = 10, allTimeDays = 365), *visits)

                result.map { it.priority }.filter { it >= 45 } shouldBe listOf(70, 45, 45)
                result.first().text shouldBe "Jorgen er en trofast gjest og har besøkt hytta de siste 7 årene"
            }
        }
    })
