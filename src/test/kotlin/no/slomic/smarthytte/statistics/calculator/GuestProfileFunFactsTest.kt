package no.slomic.smarthytte.statistics.calculator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
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

        fun visit(start: LocalDate, status: VisitStatus = VisitStatus.COMPLETED) =
            GuestVisit(start, start.plus(DatePeriod(days = 3)), days = 4, nights = 3, status = status)

        fun visitIn(year: Int, month: Int = 9, status: VisitStatus = VisitStatus.COMPLETED) =
            visit(LocalDate(year, month, 10), status)

        fun facts(guest: GuestStats, vararg visits: GuestVisit) =
            calculateGuestProfileFunFacts(guest, visits.toList(), GuestProfileFunFactContext(today))

        fun texts(guest: GuestStats, vararg visits: GuestVisit) = facts(guest, *visits).map { it.text }

        // Visits in consecutive years also give a loyal guest fact, so tests of one theme look at that theme only
        fun textsAbout(theme: String, guest: GuestStats, vararg visits: GuestVisit) =
            texts(guest, *visits).filter { theme in it }

        context("share of life") {
            should("tell how much of the life the guest has spent at the cabin") {
                val guest = liveGuestFixture("isak", isFamily = true, age = 10, allTimeDays = 365)

                texts(guest) shouldBe listOf("Isak har vært på hytta ca. 10 % av livet sitt")
                facts(guest).single().guestId shouldBe "isak"
            }

            should("skip it below the minimum share") {
                // 146 / 3650 = 4 %, 183 / 3650 = 5 %
                texts(liveGuestFixture("ola", age = 10, allTimeDays = 146)).shouldBeEmpty()
                texts(liveGuestFixture("ola", age = 10, allTimeDays = 183)) shouldBe
                    listOf("Ola har vært på hytta ca. 5 % av livet sitt")
            }

            should("tell it for older children too, since the cabin was bought before the data starts") {
                texts(liveGuestFixture("sara", isFamily = true, age = 14, allTimeDays = 472)) shouldBe
                    listOf("Sara har vært på hytta ca. 9 % av livet sitt")
            }

            should("skip it for guests under one year") {
                texts(liveGuestFixture("baby", age = 0, allTimeDays = 3)).shouldBeEmpty()
            }

            should("cap the share at 100 percent") {
                texts(liveGuestFixture("ola", age = 1, allTimeDays = 1000)) shouldBe
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

        context("calculateGuestProfileFunFacts") {
            should("return no facts for a guest without visits") {
                facts(liveGuestFixture("ola", age = 30)).shouldBeEmpty()
            }

            should("sort the facts by priority, highest first") {
                val visits = (2020..2026).map { visitIn(it, 10) }.toTypedArray()

                val result = facts(liveGuestFixture("jorgen", age = 10, allTimeDays = 365), *visits)

                result.map { it.priority } shouldBe listOf(70, 45, 45)
                result.first().text shouldBe "Jorgen er en trofast gjest og har besøkt hytta de siste 7 årene"
            }
        }
    })
