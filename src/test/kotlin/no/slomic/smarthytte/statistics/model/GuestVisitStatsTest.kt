package no.slomic.smarthytte.statistics.model

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class GuestVisitStatsTest :
    ShouldSpec({
        context("COMPARATOR") {
            val guest1 = GuestVisitStats("1", "A", "Z", 30, 5, 10)
            val guest2 = GuestVisitStats("2", "B", "Y", 30, 5, 15)
            val guest3 = GuestVisitStats("3", "C", "X", 30, 10, 10)
            val guest4 = GuestVisitStats("4", "A", "X", 30, 5, 10)
            val guest5 = GuestVisitStats("5", "B", "X", 30, 5, 10)

            should("sort by totalStayDays descending") {
                listOf(guest1, guest2).sortedWith(GuestVisitStats.COMPARATOR) shouldBe listOf(guest2, guest1)
            }

            should("sort by totalVisits descending when stay days are equal") {
                listOf(guest1, guest3).sortedWith(GuestVisitStats.COMPARATOR) shouldBe listOf(guest3, guest1)
            }

            should("sort by lastName ascending when stay days and visits are equal") {
                listOf(guest1, guest4).sortedWith(GuestVisitStats.COMPARATOR) shouldBe listOf(guest4, guest1)
            }

            should("sort by firstName ascending when stay days, visits, and last name are equal") {
                listOf(guest4, guest5).sortedWith(GuestVisitStats.COMPARATOR) shouldBe listOf(guest4, guest5)
            }

            should("correctly sort a complex list") {
                val list = listOf(guest1, guest2, guest3, guest4, guest5)
                val expectedOrder = listOf(guest2, guest3, guest4, guest5, guest1)
                list.sortedWith(GuestVisitStats.COMPARATOR) shouldBe expectedOrder
            }
        }
    })
